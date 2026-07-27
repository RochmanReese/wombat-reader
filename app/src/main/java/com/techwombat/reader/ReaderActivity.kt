package com.techwombat.reader

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.techwombat.reader.databinding.ActivityReaderBinding
import com.techwombat.reader.storage.BookReadingState
import com.techwombat.reader.storage.LibraryStorage
import com.techwombat.reader.storage.LocatorPersistence
import com.techwombat.reader.storage.ReaderDatabase
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.epub.EpubParser

/** Opens local EPUBs from the private library and restores their saved reading location. */
class ReaderActivity : AppCompatActivity() {
    private lateinit var binding: ActivityReaderBinding
    private val readerDatabase by lazy { ReaderDatabase.create(this) }
    private var activeBookId: String? = null
    private var activeNavigator: EpubNavigatorFragment? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReaderBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.closeReaderButton.setOnClickListener { finish() }

        val uri = intent.getStringExtra(EXTRA_EPUB_URI)?.let(Uri::parse)
        if (uri == null) {
            showError("No EPUB was selected.")
        } else if (savedInstanceState == null) {
            openEpub(uri)
        }
    }

    override fun onPause() {
        persistCurrentLocation()
        super.onPause()
    }

    override fun onDestroy() {
        readerDatabase.close()
        super.onDestroy()
    }

    private fun openEpub(uri: Uri) {
        binding.readerStatus.text = "Adding EPUB to library…"
        lifecycleScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val importedBook = importToLibrary(uri)
                    val previousState = readerDatabase.bookReadingStateDao().get(importedBook.bookId)
                    val initialLocator = LocatorPersistence.deserialize(previousState?.locatorJson)
                    readerDatabase.bookReadingStateDao().upsert(
                        previousState?.copy(
                            sourceUri = uri.toString(),
                            privateFilePath = importedBook.file.absolutePath,
                            lastOpenedAtEpochMillis = System.currentTimeMillis(),
                        ) ?: BookReadingState(
                            bookId = importedBook.bookId,
                            sourceUri = uri.toString(),
                            privateFilePath = importedBook.file.absolutePath,
                            title = null,
                            locatorJson = null,
                            totalProgression = null,
                            lastOpenedAtEpochMillis = System.currentTimeMillis(),
                        ),
                    )
                    val asset = AssetRetriever(contentResolver, DefaultHttpClient())
                        .retrieve(importedBook.file)
                        .getOrNull()
                        ?: error("This file is not a readable EPUB.")
                    val publication = PublicationOpener(EpubParser())
                        .open(asset, allowUserInteraction = false)
                        .getOrNull()
                        ?: error("Readium could not open this EPUB.")
                    OpenedBook(publication, importedBook, initialLocator)
                }
            }
            result.onSuccess { openedBook ->
                runCatching { showPublication(openedBook) }
                    .onFailure { error ->
                        showError("This EPUB could not be displayed: ${error.message ?: "unknown reader error"}")
                    }
            }.onFailure { error ->
                showError(error.message ?: "Could not open this EPUB.")
            }
        }
    }

    @OptIn(FlowPreview::class)
    private fun showPublication(openedBook: OpenedBook) {
        val navigatorFactory = EpubNavigatorFactory(openedBook.publication)
        supportFragmentManager.fragmentFactory = navigatorFactory.createFragmentFactory(
            initialLocator = openedBook.initialLocator,
            initialPreferences = EpubPreferences(pageMargins = 1.5),
        )
        val navigator = supportFragmentManager.fragmentFactory.instantiate(
            classLoader,
            EpubNavigatorFragment::class.java.name,
        ) as EpubNavigatorFragment
        supportFragmentManager.beginTransaction()
            .replace(R.id.readerContainer, navigator, NAVIGATOR_TAG)
            .commit()
        activeBookId = openedBook.importedBook.bookId
        activeNavigator = navigator
        lifecycleScope.launch {
            navigator.currentLocator
                .debounce(750L)
                .collect { locator -> persistLocation(openedBook.importedBook.bookId, locator) }
        }
        if (openedBook.importedBook.wasAdded) {
            Toast.makeText(this, "Added to library", Toast.LENGTH_SHORT).show()
        }
        binding.readerStatus.visibility = android.view.View.GONE
    }

    private fun persistCurrentLocation() {
        val bookId = activeBookId ?: return
        val locator = activeNavigator?.currentLocator?.value ?: return
        persistLocation(bookId, locator)
    }

    private fun persistLocation(bookId: String, locator: Locator) {
        lifecycleScope.launch(Dispatchers.IO) {
            readerDatabase.bookReadingStateDao().updateLocation(
                bookId = bookId,
                locatorJson = LocatorPersistence.serialize(locator),
                totalProgression = locator.locations.totalProgression,
                updatedAtEpochMillis = System.currentTimeMillis(),
            )
        }
    }

    private fun importToLibrary(uri: Uri): LibraryStorage.ImportedBook {
        val libraryDirectory = File(filesDir, EPUB_LIBRARY_DIRECTORY)
        val input = contentResolver.openInputStream(uri)
            ?: error("The selected file could not be read.")
        return LibraryStorage.importEpub(input, libraryDirectory)
    }

    private fun showError(message: String) {
        binding.readerStatus.text = message
        binding.readerStatus.visibility = android.view.View.VISIBLE
    }

    private data class OpenedBook(
        val publication: Publication,
        val importedBook: LibraryStorage.ImportedBook,
        val initialLocator: Locator?,
    )

    companion object {
        private const val EXTRA_EPUB_URI = "epub_uri"
        private const val NAVIGATOR_TAG = "epub-navigator"
        private const val EPUB_LIBRARY_DIRECTORY = "ebooks"

        fun intent(context: Context, uri: Uri): Intent = Intent(context, ReaderActivity::class.java)
            .putExtra(EXTRA_EPUB_URI, uri.toString())
    }
}

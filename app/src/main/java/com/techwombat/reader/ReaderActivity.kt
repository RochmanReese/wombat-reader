package com.techwombat.reader

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
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
import org.readium.r2.navigator.input.DragEvent
import org.readium.r2.navigator.input.InputListener
import org.readium.r2.navigator.input.KeyEvent
import org.readium.r2.navigator.input.TapEvent
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
    private val controlsHandler = Handler(Looper.getMainLooper())
    private val hideControlsRunnable = Runnable { hideControls() }
    private var activeBookId: String? = null
    private var activeNavigator: EpubNavigatorFragment? = null
    private var controlsVisible = true

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
        controlsHandler.removeCallbacks(hideControlsRunnable)
        persistCurrentLocation()
        super.onPause()
    }

    override fun onDestroy() {
        readerDatabase.close()
        super.onDestroy()
    }

    private fun openEpub(uri: Uri) {
        showControls(autoHide = false)
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
        navigator.addInputListener(controlsInputListener(navigator))
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
        binding.readerStatus.visibility = View.GONE
        hideControls(immediate = true)
    }

    private fun controlsInputListener(navigator: EpubNavigatorFragment): InputListener = object : InputListener {
        override fun onTap(event: TapEvent): Boolean {
            val readerView = navigator.publicationView
            if (readerView.width == 0 || readerView.height == 0) return false
            val isCentreTap = event.point.x in readerView.width * CENTRE_REGION_START..readerView.width * CENTRE_REGION_END &&
                event.point.y in readerView.height * CENTRE_REGION_START..readerView.height * CENTRE_REGION_END
            if (!isCentreTap) return false

            runOnUiThread { toggleControls() }
            return true
        }

        override fun onDrag(event: DragEvent): Boolean = false

        override fun onKey(event: KeyEvent): Boolean = false
    }

    private fun toggleControls() {
        if (controlsVisible) hideControls() else showControls(autoHide = true)
    }

    private fun showControls(autoHide: Boolean) {
        controlsHandler.removeCallbacks(hideControlsRunnable)
        if (!controlsVisible) {
            binding.readerControlsBar.alpha = 0f
            binding.readerControlsBar.visibility = View.VISIBLE
            binding.readerControlsBar.animate().alpha(1f).setDuration(CONTROLS_ANIMATION_MILLIS).start()
            controlsVisible = true
        }
        if (autoHide) {
            controlsHandler.postDelayed(hideControlsRunnable, CONTROLS_AUTO_HIDE_MILLIS)
        }
    }

    private fun hideControls(immediate: Boolean = false) {
        controlsHandler.removeCallbacks(hideControlsRunnable)
        if (!controlsVisible) return
        if (immediate) {
            binding.readerControlsBar.visibility = View.GONE
            binding.readerControlsBar.alpha = 1f
        } else {
            binding.readerControlsBar.animate()
                .alpha(0f)
                .setDuration(CONTROLS_ANIMATION_MILLIS)
                .withEndAction {
                    binding.readerControlsBar.visibility = View.GONE
                    binding.readerControlsBar.alpha = 1f
                }
                .start()
        }
        controlsVisible = false
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
        showControls(autoHide = false)
        binding.readerStatus.text = message
        binding.readerStatus.visibility = View.VISIBLE
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
        private const val CENTRE_REGION_START = 0.25f
        private const val CENTRE_REGION_END = 0.75f
        private const val CONTROLS_AUTO_HIDE_MILLIS = 4_000L
        private const val CONTROLS_ANIMATION_MILLIS = 180L

        fun intent(context: Context, uri: Uri): Intent = Intent(context, ReaderActivity::class.java)
            .putExtra(EXTRA_EPUB_URI, uri.toString())
    }
}

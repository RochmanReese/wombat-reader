package com.techwombat.reader

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.techwombat.reader.appearance.AppearanceDialog
import com.techwombat.reader.appearance.ReaderAppearanceMapper
import com.techwombat.reader.controls.ProgressionSlider
import com.techwombat.reader.databinding.ActivityReaderBinding
import com.techwombat.reader.storage.BookReadingState
import com.techwombat.reader.storage.LibraryStorage
import com.techwombat.reader.storage.LocatorPersistence
import com.techwombat.reader.storage.ReaderAppearance
import com.techwombat.reader.storage.ReaderDatabase
import com.techwombat.reader.storage.ReaderPreferencesRepository
import com.techwombat.reader.storage.ReaderTheme
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.input.DragEvent
import org.readium.r2.navigator.input.InputListener
import org.readium.r2.navigator.input.KeyEvent
import org.readium.r2.navigator.input.TapEvent
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.locateProgression
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.epub.EpubParser

class ReaderActivity : AppCompatActivity() {
    private lateinit var binding: ActivityReaderBinding
    private val readerDatabase by lazy { ReaderDatabase.create(this) }
    private val readerPreferences by lazy { ReaderPreferencesRepository(this) }
    private val controlsHandler = Handler(Looper.getMainLooper())
    private val hideControlsRunnable = Runnable { hideControls() }
    private var activeEpubUri: Uri? = null
    private var activeBookId: String? = null
    private var activeNavigator: EpubNavigatorFragment? = null
    private var activePublication: Publication? = null
    private var currentAppearance = ReaderAppearance()
    private var controlsVisible = true
    private var userIsDraggingProgress = false

    override fun onCreate(savedInstanceState: Bundle?) {
        if (savedInstanceState != null) supportFragmentManager.fragmentFactory = EpubNavigatorFragment.createDummyFactory()
        super.onCreate(savedInstanceState)
        binding = ActivityReaderBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.closeReaderButton.setOnClickListener { finish() }
        binding.readerAppearanceButton.setOnClickListener {
            showControls(autoHide = false)
            AppearanceDialog(this, currentAppearance, ::applyAndSaveAppearance).show()
        }
        configureProgressSlider()
        lifecycleScope.launch { readerPreferences.appearance.collect(::applyAppearance) }
        val uri = savedInstanceState?.getString(STATE_EPUB_URI)?.let(Uri::parse)
            ?: intent.getStringExtra(EXTRA_EPUB_URI)?.let(Uri::parse)
        if (uri == null) { showError("No EPUB was selected."); return }
        activeEpubUri = uri
        if (savedInstanceState != null) removeRestoredNavigator()
        openEpub(uri)
    }

    override fun onSaveInstanceState(outState: Bundle) { activeEpubUri?.let { outState.putString(STATE_EPUB_URI, it.toString()) }; super.onSaveInstanceState(outState) }
    override fun onPause() { controlsHandler.removeCallbacks(hideControlsRunnable); persistCurrentLocation(); super.onPause() }
    override fun onDestroy() { readerDatabase.close(); super.onDestroy() }
    private fun removeRestoredNavigator() { supportFragmentManager.findFragmentByTag(NAVIGATOR_TAG)?.let { supportFragmentManager.beginTransaction().remove(it).commitNow() } }

    private fun configureProgressSlider() {
        binding.readerProgressSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) { if (fromUser) updateProgressLabel(progress) }
            override fun onStartTrackingTouch(bar: SeekBar) { userIsDraggingProgress = true; showControls(false) }
            override fun onStopTrackingTouch(bar: SeekBar) { userIsDraggingProgress = false; navigateToProgression(ProgressionSlider.toProgression(bar.progress)); showControls(true) }
        })
    }

    private fun openEpub(uri: Uri) {
        showControls(false); binding.readerStatus.text = "Opening EPUB…"
        lifecycleScope.launch {
            val result = runCatching { withContext(Dispatchers.IO) {
                val imported = importToLibrary(uri)
                val previous = readerDatabase.bookReadingStateDao().get(imported.bookId)
                val initial = LocatorPersistence.deserialize(previous?.locatorJson)
                readerDatabase.bookReadingStateDao().upsert(previous?.copy(sourceUri = uri.toString(), privateFilePath = imported.file.absolutePath, lastOpenedAtEpochMillis = System.currentTimeMillis()) ?: BookReadingState(imported.bookId, uri.toString(), imported.file.absolutePath, null, null, null, System.currentTimeMillis()))
                val asset = AssetRetriever(contentResolver, DefaultHttpClient()).retrieve(imported.file).getOrNull() ?: error("This file is not a readable EPUB.")
                val publication = PublicationOpener(EpubParser()).open(asset, allowUserInteraction = false).getOrNull() ?: error("Readium could not open this EPUB.")
                OpenedBook(publication, imported, initial)
            }}
            result.onSuccess { runCatching { showPublication(it) }.onFailure { error -> showError("This EPUB could not be displayed: ${error.message ?: "unknown reader error"}") } }
                .onFailure { showError(it.message ?: "Could not open this EPUB.") }
        }
    }

    @OptIn(FlowPreview::class)
    private fun showPublication(book: OpenedBook) {
        val factory = EpubNavigatorFactory(book.publication)
        supportFragmentManager.fragmentFactory = factory.createFragmentFactory(initialLocator = book.initialLocator, initialPreferences = ReaderAppearanceMapper.toEpubPreferences(currentAppearance))
        val navigator = supportFragmentManager.fragmentFactory.instantiate(classLoader, EpubNavigatorFragment::class.java.name) as EpubNavigatorFragment
        navigator.addInputListener(controlsInputListener(navigator))
        supportFragmentManager.beginTransaction().replace(R.id.readerContainer, navigator, NAVIGATOR_TAG).commit()
        activeBookId = book.importedBook.bookId; activeNavigator = navigator; activePublication = book.publication
        lifecycleScope.launch { navigator.currentLocator.debounce(750L).collect { locator -> updateProgressSlider(locator); persistLocation(book.importedBook.bookId, locator) } }
        if (book.importedBook.wasAdded) Toast.makeText(this, "Added to library", Toast.LENGTH_SHORT).show()
        binding.readerStatus.visibility = View.GONE; hideControls(true)
    }

    private fun controlsInputListener(navigator: EpubNavigatorFragment) = object : InputListener {
        override fun onTap(event: TapEvent): Boolean {
            val view = navigator.publicationView
            if (view.width == 0 || view.height == 0) return false
            if (event.point.x !in view.width * CENTRE_REGION_START..view.width * CENTRE_REGION_END || event.point.y !in view.height * CENTRE_REGION_START..view.height * CENTRE_REGION_END) return false
            runOnUiThread { if (controlsVisible) hideControls() else showControls(true) }; return true
        }
        override fun onDrag(event: DragEvent) = false
        override fun onKey(event: KeyEvent) = false
    }

    private fun applyAndSaveAppearance(appearance: ReaderAppearance) { applyAppearance(appearance); lifecycleScope.launch { readerPreferences.saveAppearance(appearance) } }
    private fun applyAppearance(appearance: ReaderAppearance) {
        currentAppearance = appearance.normalized()
        activeNavigator?.submitPreferences(ReaderAppearanceMapper.toEpubPreferences(currentAppearance))
        val dark = currentAppearance.theme == ReaderTheme.DARK
        binding.root.setBackgroundColor(if (dark) Color.BLACK else Color.WHITE)
        binding.readerControlsBar.setBackgroundColor(if (dark) Color.rgb(30, 30, 30) else Color.rgb(255, 253, 248))
        binding.readerProgressLabel.setTextColor(if (dark) Color.WHITE else Color.DKGRAY)
    }

    private fun updateProgressSlider(locator: Locator) { if (!userIsDraggingProgress) { val progress = ProgressionSlider.toSliderProgress(locator.locations.totalProgression); binding.readerProgressSlider.progress = progress; updateProgressLabel(progress) } }
    private fun updateProgressLabel(progress: Int) { binding.readerProgressLabel.text = getString(R.string.reader_progress_percent, progress) }
    private fun navigateToProgression(progression: Double) { lifecycleScope.launch { activePublication?.locateProgression(progression)?.let { activeNavigator?.go(it) } } }
    private fun showControls(autoHide: Boolean) { controlsHandler.removeCallbacks(hideControlsRunnable); if (!controlsVisible) { binding.readerControlsBar.alpha = 0f; binding.readerControlsBar.visibility = View.VISIBLE; binding.readerControlsBar.animate().alpha(1f).setDuration(180L).start(); controlsVisible = true }; if (autoHide && !userIsDraggingProgress) controlsHandler.postDelayed(hideControlsRunnable, 4_000L) }
    private fun hideControls(immediate: Boolean = false) { controlsHandler.removeCallbacks(hideControlsRunnable); if (!controlsVisible || userIsDraggingProgress) return; if (immediate) { binding.readerControlsBar.visibility = View.GONE; binding.readerControlsBar.alpha = 1f } else binding.readerControlsBar.animate().alpha(0f).setDuration(180L).withEndAction { binding.readerControlsBar.visibility = View.GONE; binding.readerControlsBar.alpha = 1f }.start(); controlsVisible = false }
    private fun persistCurrentLocation() { activeBookId?.let { id -> activeNavigator?.currentLocator?.value?.let { persistLocation(id, it) } } }
    private fun persistLocation(bookId: String, locator: Locator) { lifecycleScope.launch(Dispatchers.IO) { readerDatabase.bookReadingStateDao().updateLocation(bookId, LocatorPersistence.serialize(locator), locator.locations.totalProgression, System.currentTimeMillis()) } }
    private fun importToLibrary(uri: Uri): LibraryStorage.ImportedBook { val input = contentResolver.openInputStream(uri) ?: error("The selected file could not be read."); return LibraryStorage.importEpub(input, File(filesDir, EPUB_LIBRARY_DIRECTORY)) }
    private fun showError(message: String) { showControls(false); binding.readerStatus.text = message; binding.readerStatus.visibility = View.VISIBLE }
    private data class OpenedBook(val publication: Publication, val importedBook: LibraryStorage.ImportedBook, val initialLocator: Locator?)
    companion object { private const val EXTRA_EPUB_URI = "epub_uri"; private const val STATE_EPUB_URI = "state_epub_uri"; private const val NAVIGATOR_TAG = "epub-navigator"; private const val EPUB_LIBRARY_DIRECTORY = "ebooks"; private const val CENTRE_REGION_START = 0.25f; private const val CENTRE_REGION_END = 0.75f; fun intent(context: Context, uri: Uri) = Intent(context, ReaderActivity::class.java).putExtra(EXTRA_EPUB_URI, uri.toString()) }
}

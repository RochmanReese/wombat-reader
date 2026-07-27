package com.techwombat.reader

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.techwombat.reader.databinding.ActivityReaderBinding
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.epub.EpubParser

/** Opens a local EPUB with Readium. Reading controls and persistence follow in later stages. */
class ReaderActivity : AppCompatActivity() {
    private lateinit var binding: ActivityReaderBinding

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

    private fun openEpub(uri: Uri) {
        binding.readerStatus.text = "Opening EPUB…"
        lifecycleScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val cachedEpub = copyToCache(uri)
                    val asset = AssetRetriever(contentResolver, DefaultHttpClient())
                        .retrieve(cachedEpub)
                        .getOrNull()
                        ?: error("This file is not a readable EPUB.")
                    PublicationOpener(EpubParser())
                        .open(asset, allowUserInteraction = false)
                        .getOrNull()
                        ?: error("Readium could not open this EPUB.")
                }
            }
            result.onSuccess { publication ->
                runCatching { showPublication(publication) }
                    .onFailure { error ->
                        showError("This EPUB could not be displayed: ${error.message ?: "unknown reader error"}")
                    }
            }.onFailure { error ->
                showError(error.message ?: "Could not open this EPUB.")
            }
        }
    }

    private fun showPublication(publication: Publication) {
        val navigatorFactory = EpubNavigatorFactory(publication)
        supportFragmentManager.fragmentFactory = navigatorFactory.createFragmentFactory(initialLocator = null)
        val navigator = supportFragmentManager.fragmentFactory.instantiate(
            classLoader,
            EpubNavigatorFragment::class.java.name,
        )
        supportFragmentManager.beginTransaction()
            .replace(R.id.readerContainer, navigator, NAVIGATOR_TAG)
            .commit()
        binding.readerStatus.visibility = android.view.View.GONE
    }

    private fun showError(message: String) {
        binding.readerStatus.text = message
        binding.readerStatus.visibility = android.view.View.VISIBLE
    }

    private fun copyToCache(uri: Uri): File {
        val destination = File(cacheDir, "opened-epub.epub")
        contentResolver.openInputStream(uri)?.use { input ->
            destination.outputStream().use { output -> input.copyTo(output) }
        } ?: error("The selected file could not be read.")
        return destination
    }

    companion object {
        private const val EXTRA_EPUB_URI = "epub_uri"
        private const val NAVIGATOR_TAG = "epub-navigator"

        fun intent(context: Context, uri: Uri): Intent = Intent(context, ReaderActivity::class.java)
            .putExtra(EXTRA_EPUB_URI, uri.toString())
    }
}

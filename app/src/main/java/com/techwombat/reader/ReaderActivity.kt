package com.techwombat.reader

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.techwombat.reader.databinding.ActivityReaderBinding

/** Temporary compatibility-spike screen; Readium rendering is step 2's next subtask. */
class ReaderActivity : AppCompatActivity() {
    private lateinit var binding: ActivityReaderBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReaderBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.selectedUri.text = intent.getStringExtra(EXTRA_EPUB_URI) ?: "No EPUB selected"
        binding.closeReaderButton.setOnClickListener { finish() }
    }

    companion object {
        private const val EXTRA_EPUB_URI = "epub_uri"

        fun intent(context: Context, uri: Uri): Intent = Intent(context, ReaderActivity::class.java)
            .putExtra(EXTRA_EPUB_URI, uri.toString())
    }
}

package com.techwombat.reader

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.techwombat.reader.databinding.ActivityMainBinding
import com.techwombat.reader.library.LibraryAdapter
import com.techwombat.reader.storage.BookReadingState
import com.techwombat.reader.storage.ReaderDatabase
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val database by lazy { ReaderDatabase.create(this) }
    private lateinit var adapter: LibraryAdapter
    private var libraryJob: Job? = null
    private val openEpub = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@registerForActivityResult
        contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(ReaderActivity.intent(this, uri))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        adapter = LibraryAdapter(::openBook, ::confirmDelete)
        binding.libraryBooks.layoutManager = LinearLayoutManager(this)
        binding.libraryBooks.adapter = adapter
        binding.openEpubButton.setOnClickListener { openEpub.launch(arrayOf("application/epub+zip", "application/zip")) }
        binding.librarySearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = observeLibrary(s?.toString().orEmpty())
            override fun afterTextChanged(s: Editable?) = Unit
        })
        observeLibrary("")
    }

    override fun onDestroy() { libraryJob?.cancel(); database.close(); super.onDestroy() }
    private fun observeLibrary(query: String) {
        libraryJob?.cancel()
        libraryJob = lifecycleScope.launch { database.bookReadingStateDao().observeLibrary(query).collect(adapter::submit) }
    }
    private fun openBook(book: BookReadingState) { startActivity(ReaderActivity.intent(this, android.net.Uri.parse(book.sourceUri))) }
    private fun confirmDelete(book: BookReadingState) {
        val title = book.title?.takeIf { it.isNotBlank() } ?: book.privateFilePath?.let { File(it).nameWithoutExtension } ?: "this book"
        AlertDialog.Builder(this).setTitle("Delete from library?").setMessage("Delete $title from Wombat Reader? Your original file will not be deleted.")
            .setNegativeButton("Cancel", null).setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch(Dispatchers.IO) { book.privateFilePath?.let(::File)?.delete(); database.bookReadingStateDao().delete(book.bookId) }
            }.show()
    }
}

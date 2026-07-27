package com.techwombat.reader

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.techwombat.reader.databinding.ActivityMainBinding
import com.techwombat.reader.library.EpubFolderImporter
import com.techwombat.reader.library.LibraryAdapter
import com.techwombat.reader.storage.BookReadingState
import com.techwombat.reader.storage.ReaderDatabase
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val database by lazy { ReaderDatabase.create(this) }
    private val folderImporter by lazy { EpubFolderImporter(this) }
    private lateinit var adapter: LibraryAdapter
    private var libraryJob: Job? = null
    private val openEpub = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION); startActivity(ReaderActivity.intent(this, it)) } }
    private val importFolder = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> uri?.let { contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION); scanFolder(it) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); binding = ActivityMainBinding.inflate(layoutInflater); setContentView(binding.root)
        adapter = LibraryAdapter(::openBook, ::confirmDelete); binding.libraryBooks.layoutManager = LinearLayoutManager(this); binding.libraryBooks.adapter = adapter
        binding.openEpubButton.setOnClickListener { openEpub.launch(arrayOf("application/epub+zip", "application/zip")) }
        binding.importFolderButton.setOnClickListener { importFolder.launch(null) }
        binding.librarySearch.addTextChangedListener(object : TextWatcher { override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit; override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = observeLibrary(s?.toString().orEmpty()); override fun afterTextChanged(s: Editable?) = Unit })
        observeLibrary("")
    }
    override fun onDestroy() { libraryJob?.cancel(); database.close(); super.onDestroy() }
    private fun observeLibrary(query: String) { libraryJob?.cancel(); libraryJob = lifecycleScope.launch { database.bookReadingStateDao().observeLibrary(query).collect(adapter::submit) } }
    private fun openBook(book: BookReadingState) { startActivity(ReaderActivity.intent(this, android.net.Uri.parse(book.sourceUri))) }

    private fun scanFolder(uri: android.net.Uri) {
        binding.importFolderButton.isEnabled = false; binding.importProgress.visibility = View.VISIBLE; binding.importProgress.text = "Scanning EPUB folder…"
        lifecycleScope.launch {
            val sources = withContext(Dispatchers.IO) { folderImporter.scanTree(uri) }
            val groups = sources.groupBy { it.name.lowercase() }
            val selected = groups.filterValues { it.size == 1 }.values.flatten().toMutableList()
            val duplicates = groups.filterValues { it.size > 1 }.values.toList()
            if (duplicates.isEmpty()) startImport(selected) else chooseDuplicate(duplicates, 0, selected)
        }
    }

    private fun chooseDuplicate(groups: List<List<EpubFolderImporter.Source>>, index: Int, selected: MutableList<EpubFolderImporter.Source>) {
        if (index == groups.size) { startImport(selected); return }
        val copies = groups[index]
        val labels = copies.map { "${it.name}\n${it.uri}" }.toTypedArray()
        AlertDialog.Builder(this).setTitle("Choose one copy to import").setMessage("More than one EPUB is named ${copies.first().name}.")
            .setSingleChoiceItems(labels, 0, null).setNegativeButton("Cancel import") { _, _ -> finishImport("Import cancelled.") }
            .setPositiveButton("Use selected") { dialog, _ -> selected += copies[(dialog as AlertDialog).listView.checkedItemPosition]; chooseDuplicate(groups, index + 1, selected) }.show()
    }

    private fun startImport(sources: List<EpubFolderImporter.Source>) {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { folderImporter.importSources(sources) { message -> runOnUiThread { binding.importProgress.text = message } } }
            finishImport("Imported ${result.imported}; duplicates ${result.duplicates}; skipped ${result.skipped}.")
        }
    }
    private fun finishImport(message: String) { binding.importFolderButton.isEnabled = true; binding.importProgress.text = message }
    private fun confirmDelete(book: BookReadingState) {
        val title = book.title?.takeIf { it.isNotBlank() } ?: book.privateFilePath?.let { File(it).nameWithoutExtension } ?: "this book"
        AlertDialog.Builder(this).setTitle("Delete from library?").setMessage("Delete $title from Wombat Reader? Your original file will not be deleted.").setNegativeButton("Cancel", null).setPositiveButton("Delete") { _, _ -> lifecycleScope.launch(Dispatchers.IO) { book.privateFilePath?.let(::File)?.delete(); database.bookReadingStateDao().delete(book.bookId) } }.show()
    }
}

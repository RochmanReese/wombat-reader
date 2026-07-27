package com.techwombat.reader.library

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.techwombat.reader.storage.BookReadingState
import com.techwombat.reader.storage.LibraryStorage
import com.techwombat.reader.storage.ReaderDatabase
import java.io.File
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.epub.EpubParser

class EpubFolderImporter(private val context: Context) {
    data class Source(val uri: Uri, val name: String)
    data class Result(val imported: Int, val duplicates: Int, val skipped: Int)

    fun scanTree(treeUri: Uri): List<Source> {
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: error("The selected folder could not be opened.")
        return walk(root).filter { it.name?.endsWith(".epub", ignoreCase = true) == true }
            .map { Source(it.uri, it.name ?: "Unnamed EPUB") }.toList()
    }

    suspend fun importSources(sources: List<Source>, onProgress: (String) -> Unit): Result {
        val library = File(context.filesDir, "ebooks")
        val database = ReaderDatabase.create(context)
        val dao = database.bookReadingStateDao()
        var imported = 0; var duplicates = 0; var skipped = 0
        try {
            for (source in sources) {
                onProgress("Importing ${source.name}…")
                runCatching {
                    val input = context.contentResolver.openInputStream(source.uri) ?: error("Cannot read ${source.name}")
                    val book = LibraryStorage.importEpub(input, library)
                    val existing = dao.get(book.bookId)
                    val metadata = readMetadata(book.file)
                    dao.upsert(existing?.copy(sourceUri = source.uri.toString(), privateFilePath = book.file.absolutePath) ?: BookReadingState(book.bookId, source.uri.toString(), book.file.absolutePath, metadata.first ?: book.file.nameWithoutExtension, null, null, System.currentTimeMillis(), metadata.second ?: "Unknown author"))
                    if (book.wasAdded) imported++ else duplicates++
                }.onFailure { skipped++ }
            }
        } finally { database.close() }
        return Result(imported, duplicates, skipped)
    }

    private fun walk(directory: DocumentFile): Sequence<DocumentFile> = sequence { for (child in directory.listFiles()) if (child.isDirectory) yieldAll(walk(child)) else if (child.isFile) yield(child) }
    private suspend fun readMetadata(file: File): Pair<String?, String?> {
        val asset = AssetRetriever(context.contentResolver, DefaultHttpClient()).retrieve(file).getOrNull() ?: return null to null
        val publication = PublicationOpener(EpubParser()).open(asset, allowUserInteraction = false).getOrNull() ?: return null to null
        return EpubMetadata.title(publication) to EpubMetadata.author(publication)
    }
}

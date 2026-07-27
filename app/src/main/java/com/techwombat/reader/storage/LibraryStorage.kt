package com.techwombat.reader.storage

import java.io.File
import java.io.InputStream

/** Stores one private, content-addressed EPUB copy for every distinct imported book. */
object LibraryStorage {
    data class ImportedBook(
        val bookId: String,
        val file: File,
        val wasAdded: Boolean,
    )

    fun importEpub(input: InputStream, libraryDirectory: File): ImportedBook {
        check(libraryDirectory.exists() || libraryDirectory.mkdirs()) {
            "Could not create the EPUB library folder."
        }
        val temporary = File.createTempFile("import-", ".epub", libraryDirectory)
        try {
            input.use { source ->
                temporary.outputStream().buffered().use { destination -> source.copyTo(destination) }
            }
            val bookId = BookIdentity.fromEpubFile(temporary)
            val destination = File(libraryDirectory, "$bookId.epub")
            if (destination.exists()) {
                return ImportedBook(bookId, destination, wasAdded = false)
            }
            check(temporary.renameTo(destination)) { "Could not add the EPUB to the library." }
            return ImportedBook(bookId, destination, wasAdded = true)
        } finally {
            if (temporary.exists()) temporary.delete()
        }
    }
}

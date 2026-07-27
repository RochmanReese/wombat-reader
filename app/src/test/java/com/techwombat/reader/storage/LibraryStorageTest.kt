package com.techwombat.reader.storage

import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryStorageTest {
    private val directory = Files.createTempDirectory("reader-library-").toFile()

    @After
    fun cleanUp() {
        directory.deleteRecursively()
    }

    @Test
    fun firstImportCreatesAContentAddressedPrivateCopy() {
        val imported = LibraryStorage.importEpub(
            ByteArrayInputStream("book contents".toByteArray()),
            directory,
        )

        assertTrue(imported.wasAdded)
        assertTrue(imported.file.exists())
        assertEquals("book contents", imported.file.readText())
        assertEquals("${imported.bookId}.epub", imported.file.name)
    }

    @Test
    fun duplicateImportReusesTheExistingLibraryCopy() {
        val first = LibraryStorage.importEpub(ByteArrayInputStream("same".toByteArray()), directory)
        val second = LibraryStorage.importEpub(ByteArrayInputStream("same".toByteArray()), directory)

        assertTrue(first.wasAdded)
        assertFalse(second.wasAdded)
        assertEquals(first.bookId, second.bookId)
        assertEquals(first.file, second.file)
    }
}

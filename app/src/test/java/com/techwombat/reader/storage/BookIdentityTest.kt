package com.techwombat.reader.storage

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class BookIdentityTest {
    @Test
    fun sameEpubContentAlwaysProducesTheSameBookId() {
        val first = temporaryFile("same book")
        val second = temporaryFile("same book")

        assertEquals(BookIdentity.fromEpubFile(first), BookIdentity.fromEpubFile(second))
    }

    @Test
    fun changedEpubContentProducesADifferentBookId() {
        val first = temporaryFile("first book")
        val second = temporaryFile("second book")

        assertNotEquals(BookIdentity.fromEpubFile(first), BookIdentity.fromEpubFile(second))
    }

    private fun temporaryFile(contents: String): File = File.createTempFile("book-identity", ".epub").apply {
        writeText(contents)
        deleteOnExit()
    }
}

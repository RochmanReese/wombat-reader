package com.techwombat.reader.storage

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderStorageInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private var database: ReaderDatabase? = null

    @After
    fun tearDown() {
        database?.close()
    }

    @Test
    fun appearancePersistsThroughDataStore() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val file = File.createTempFile("reader-preferences", ".preferences_pb", context.cacheDir)
        try {
            val dataStore = PreferenceDataStoreFactory.create(scope = scope) { file }
            val repository = ReaderPreferencesRepository(dataStore)
            val expected = ReaderAppearance(
                fontScale = 1.4f,
                fontFamily = "sans-serif",
                lineSpacing = 1.8f,
                theme = ReaderTheme.DARK,
            )

            repository.saveAppearance(expected)

            assertEquals(expected, repository.appearance.first())
        } finally {
            scope.cancel()
            file.delete()
        }
    }

    @Test
    fun bookLocationIsStoredByBookId() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, ReaderDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        database = db
        val firstBook = BookReadingState(
            bookId = "first-book",
            sourceUri = "content://books/first",
            privateFilePath = "/files/books/first.epub",
            title = "First",
            locatorJson = "{\"href\":\"chapter-1.xhtml\"}",
            totalProgression = 0.1,
            lastOpenedAtEpochMillis = 100L,
        )
        val secondBook = firstBook.copy(
            bookId = "second-book",
            sourceUri = "content://books/second",
            locatorJson = "{\"href\":\"chapter-7.xhtml\"}",
            totalProgression = 0.7,
        )

        db.bookReadingStateDao().upsert(firstBook)
        db.bookReadingStateDao().upsert(secondBook)
        db.bookReadingStateDao().updateLocation(
            bookId = firstBook.bookId,
            locatorJson = "{\"href\":\"chapter-2.xhtml\"}",
            totalProgression = 0.2,
            updatedAtEpochMillis = 200L,
        )

        val restoredFirst = db.bookReadingStateDao().get(firstBook.bookId)
        val restoredSecond = db.bookReadingStateDao().get(secondBook.bookId)
        assertNotNull(restoredFirst)
        assertNotNull(restoredSecond)
        assertEquals(0.2, restoredFirst?.totalProgression)
        assertEquals(0.7, restoredSecond?.totalProgression)
    }
}

package com.techwombat.reader.storage

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "book_reading_state")
data class BookReadingState(
    @PrimaryKey val bookId: String,
    val sourceUri: String,
    val privateFilePath: String?,
    val title: String?,
    val locatorJson: String?,
    val totalProgression: Double?,
    val lastOpenedAtEpochMillis: Long,
    val author: String? = null,
)

@Dao
interface BookReadingStateDao {
    @Query("SELECT * FROM book_reading_state WHERE bookId = :bookId")
    suspend fun get(bookId: String): BookReadingState?

    @Query("SELECT * FROM book_reading_state WHERE bookId = :bookId")
    fun observe(bookId: String): Flow<BookReadingState?>

    @Query("""
        SELECT * FROM book_reading_state
        WHERE lower(COALESCE(title, '')) LIKE '%' || lower(:query) || '%'
           OR lower(COALESCE(author, '')) LIKE '%' || lower(:query) || '%'
        ORDER BY lastOpenedAtEpochMillis DESC
    """)
    fun observeLibrary(query: String): Flow<List<BookReadingState>>

    @Upsert
    suspend fun upsert(state: BookReadingState)

    @Query("UPDATE book_reading_state SET title = :title, author = :author WHERE bookId = :bookId")
    suspend fun updateMetadata(bookId: String, title: String, author: String)

    @Query("DELETE FROM book_reading_state WHERE bookId = :bookId")
    suspend fun delete(bookId: String)

    @Query("""
        UPDATE book_reading_state SET locatorJson = :locatorJson, totalProgression = :totalProgression,
        lastOpenedAtEpochMillis = :updatedAtEpochMillis WHERE bookId = :bookId
    """)
    suspend fun updateLocation(bookId: String, locatorJson: String, totalProgression: Double?, updatedAtEpochMillis: Long)
}

@Database(entities = [BookReadingState::class], version = 2, exportSchema = true)
abstract class ReaderDatabase : RoomDatabase() {
    abstract fun bookReadingStateDao(): BookReadingStateDao
    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE book_reading_state ADD COLUMN author TEXT")
            }
        }
        fun create(context: Context): ReaderDatabase = Room.databaseBuilder(context.applicationContext, ReaderDatabase::class.java, "wombat-reader.db")
            .addMigrations(MIGRATION_1_2)
            .build()
    }
}

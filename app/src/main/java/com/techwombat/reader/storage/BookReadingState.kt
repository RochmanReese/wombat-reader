package com.techwombat.reader.storage

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import android.content.Context
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
)

@Dao
interface BookReadingStateDao {
    @Query("SELECT * FROM book_reading_state WHERE bookId = :bookId")
    suspend fun get(bookId: String): BookReadingState?

    @Query("SELECT * FROM book_reading_state WHERE bookId = :bookId")
    fun observe(bookId: String): Flow<BookReadingState?>

    @Upsert
    suspend fun upsert(state: BookReadingState)

    @Query(
        "UPDATE book_reading_state SET locatorJson = :locatorJson, totalProgression = :totalProgression, " +
            "lastOpenedAtEpochMillis = :updatedAtEpochMillis WHERE bookId = :bookId",
    )
    suspend fun updateLocation(
        bookId: String,
        locatorJson: String,
        totalProgression: Double?,
        updatedAtEpochMillis: Long,
    )
}

@Database(
    entities = [BookReadingState::class],
    version = 1,
    exportSchema = true,
)
abstract class ReaderDatabase : RoomDatabase() {
    abstract fun bookReadingStateDao(): BookReadingStateDao

    companion object {
        fun create(context: Context): ReaderDatabase = Room.databaseBuilder(
            context.applicationContext,
            ReaderDatabase::class.java,
            "wombat-reader.db",
        ).build()
    }
}

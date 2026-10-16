package com.filemanager.app.data.db

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "recent_search")
data class RecentSearch(@PrimaryKey val term: String, val timestamp: Long)

/** names and paths are newline-separated lists. */
@Entity(tableName = "share_log")
data class ShareLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val names: String,
    val paths: String,
    val target: String,
    val timestamp: Long
)

@Dao
interface SearchDao {
    @Query("SELECT * FROM recent_search ORDER BY timestamp DESC LIMIT 8")
    fun observe(): Flow<List<RecentSearch>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: RecentSearch)

    @Query("DELETE FROM recent_search WHERE term = :term")
    suspend fun delete(term: String)

    @Query("DELETE FROM recent_search")
    suspend fun clear()
}

@Dao
interface ShareDao {
    @Query("SELECT * FROM share_log ORDER BY timestamp DESC LIMIT 100")
    fun observe(): Flow<List<ShareLog>>

    @Insert
    suspend fun insert(item: ShareLog)

    @Query("DELETE FROM share_log")
    suspend fun clear()
}

@Database(entities = [RecentSearch::class, ShareLog::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun searchDao(): SearchDao
    abstract fun shareDao(): ShareDao
}

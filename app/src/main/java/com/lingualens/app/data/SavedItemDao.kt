package com.lingualens.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedItemDao {

    @Query("SELECT * FROM saved_items ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<SavedItem>>

    @Query("SELECT * FROM saved_items ORDER BY createdAt DESC")
    suspend fun getAll(): List<SavedItem>

    @Query("SELECT * FROM saved_items WHERE dueAt <= :now ORDER BY dueAt ASC LIMIT 100")
    suspend fun getDue(now: Long): List<SavedItem>

    @Query("SELECT COUNT(*) FROM saved_items WHERE dueAt <= :now")
    fun observeDueCount(now: Long): Flow<Int>

    @Query("SELECT * FROM saved_items WHERE ankiNoteId = 0")
    suspend fun getNotInAnki(): List<SavedItem>

    @Query("SELECT * FROM saved_items WHERE dutch = :dutch LIMIT 1")
    suspend fun findByDutch(dutch: String): SavedItem?

    @Insert
    suspend fun insert(item: SavedItem): Long

    @Update
    suspend fun update(item: SavedItem)

    @Delete
    suspend fun delete(item: SavedItem)

    @Query("DELETE FROM saved_items")
    suspend fun deleteAll()
}

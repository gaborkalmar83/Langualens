package com.lingualens.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_items")
data class SavedItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dutch: String,
    val english: String,
    val kind: String = KIND_SENTENCE,
    val context: String = "",
    val source: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    // SM-2 scheduling
    val dueAt: Long = System.currentTimeMillis(),
    val intervalDays: Int = 0,
    val ease: Double = 2.5,
    val reps: Int = 0,
    val lapses: Int = 0,
    val ankiNoteId: Long = 0L
) {
    companion object {
        const val KIND_WORD = "word"
        const val KIND_SENTENCE = "sentence"
    }
}

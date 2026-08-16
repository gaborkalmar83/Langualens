package com.langualens.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_items")
data class SavedItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** The text as you read it. */
    val sourceText: String,
    /** The translation shown underneath. */
    val targetText: String,
    val sourceLang: String = "nl",
    val targetLang: String = "en",
    val kind: String = KIND_SENTENCE,
    /** Surrounding sentence or paragraph, kept for context on the card back. */
    val context: String = "",
    /** Where it came from: a URL, an app name, or the screen. */
    val origin: String = "",
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

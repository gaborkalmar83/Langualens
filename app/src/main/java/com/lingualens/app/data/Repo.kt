package com.lingualens.app.data

import android.content.Context
import com.lingualens.app.anki.AnkiBridge
import com.lingualens.app.translate.Nl2En
import com.lingualens.app.translate.Sentences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class Repo private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val dao = AppDatabase.get(appContext).savedItemDao()
    val prefs = Prefs(appContext)

    fun observeAll(): Flow<List<SavedItem>> = dao.observeAll()

    suspend fun save(
        dutch: String,
        english: String? = null,
        context: String = "",
        source: String = ""
    ): SaveResult = withContext(Dispatchers.IO) {
        val clean = dutch.trim()
        if (clean.isEmpty()) return@withContext SaveResult(false, "Nothing to save")

        val existing = dao.findByDutch(clean)
        if (existing != null) return@withContext SaveResult(false, "Already saved")

        val translation = english?.takeIf { it.isNotBlank() } ?: Nl2En.translate(clean)
        val kind = if (Sentences.looksLikeSingleWord(clean)) SavedItem.KIND_WORD else SavedItem.KIND_SENTENCE
        var item = SavedItem(
            dutch = clean,
            english = translation,
            kind = kind,
            context = context.take(400),
            source = source.take(300)
        )
        val id = dao.insert(item)
        item = item.copy(id = id)

        if (prefs.autoPushAnki) {
            val noteId = AnkiBridge.addNote(appContext, prefs.ankiDeck, prefs.ankiModel, item)
            if (noteId != null && noteId > 0) dao.update(item.copy(ankiNoteId = noteId))
        }
        SaveResult(true, "Saved: $clean")
    }

    suspend fun all(): List<SavedItem> = withContext(Dispatchers.IO) { dao.getAll() }

    suspend fun due(): List<SavedItem> =
        withContext(Dispatchers.IO) { dao.getDue(System.currentTimeMillis()) }

    suspend fun update(item: SavedItem) = withContext(Dispatchers.IO) { dao.update(item) }

    suspend fun delete(item: SavedItem) = withContext(Dispatchers.IO) { dao.delete(item) }

    suspend fun pushAllToAnki(): String = withContext(Dispatchers.IO) {
        if (!AnkiBridge.isAvailable(appContext)) return@withContext "AnkiDroid not found on this phone"
        val pending = dao.getNotInAnki()
        if (pending.isEmpty()) return@withContext "Nothing new to push"
        var ok = 0
        var failed = 0
        for (item in pending) {
            val noteId = AnkiBridge.addNote(appContext, prefs.ankiDeck, prefs.ankiModel, item)
            if (noteId != null && noteId > 0) {
                dao.update(item.copy(ankiNoteId = noteId)); ok++
            } else failed++
        }
        if (failed == 0) "Pushed $ok cards to AnkiDroid" else "Pushed $ok, failed $failed"
    }

    data class SaveResult(val success: Boolean, val message: String)

    companion object {
        @Volatile
        private var INSTANCE: Repo? = null

        fun get(context: Context): Repo = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Repo(context).also { INSTANCE = it }
        }
    }
}

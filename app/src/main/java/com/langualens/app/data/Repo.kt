package com.langualens.app.data

import android.content.Context
import com.langualens.app.R
import com.langualens.app.anki.AnkiBridge
import com.langualens.app.translate.Sentences
import com.langualens.app.translate.Translate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

enum class SaveOutcome { SAVED, DUPLICATE, EMPTY }

data class SaveResult(val outcome: SaveOutcome, val text: String = "")

class Repo private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val dao = AppDatabase.get(appContext).savedItemDao()
    val prefs = Prefs(appContext)

    init {
        Translate.configure(prefs.sourceLanguage, prefs.targetLanguage)
    }

    fun observeAll(): Flow<List<SavedItem>> = dao.observeAll()

    fun setLanguages(source: String, target: String) {
        prefs.sourceLanguage = source
        prefs.targetLanguage = target
        Translate.configure(source, target)
    }

    suspend fun save(
        text: String,
        translation: String? = null,
        context: String = "",
        origin: String = ""
    ): SaveResult = withContext(Dispatchers.IO) {
        val clean = text.trim()
        if (clean.isEmpty()) return@withContext SaveResult(SaveOutcome.EMPTY)

        val sourceLang = Translate.sourceTag
        val targetLang = Translate.targetTag

        if (dao.findExisting(clean, sourceLang) != null) {
            return@withContext SaveResult(SaveOutcome.DUPLICATE, clean)
        }

        val translated = translation?.takeIf { it.isNotBlank() } ?: Translate.translate(clean)
        val kind =
            if (Sentences.looksLikeSingleWord(clean)) SavedItem.KIND_WORD else SavedItem.KIND_SENTENCE

        var item = SavedItem(
            sourceText = clean,
            targetText = translated,
            sourceLang = sourceLang,
            targetLang = targetLang,
            kind = kind,
            context = context.take(400),
            origin = origin.take(300)
        )
        val id = dao.insert(item)
        item = item.copy(id = id)

        if (prefs.autoPushAnki) {
            val noteId = AnkiBridge.addNote(appContext, prefs.ankiDeck, prefs.ankiModel, item)
            if (noteId != null && noteId > 0) dao.update(item.copy(ankiNoteId = noteId))
        }
        SaveResult(SaveOutcome.SAVED, clean)
    }

    suspend fun all(): List<SavedItem> = withContext(Dispatchers.IO) { dao.getAll() }

    suspend fun due(): List<SavedItem> =
        withContext(Dispatchers.IO) { dao.getDue(System.currentTimeMillis()) }

    suspend fun update(item: SavedItem) = withContext(Dispatchers.IO) { dao.update(item) }

    suspend fun delete(item: SavedItem) = withContext(Dispatchers.IO) { dao.delete(item) }

    suspend fun pushAllToAnki(): String = withContext(Dispatchers.IO) {
        if (!AnkiBridge.isAvailable(appContext)) {
            return@withContext appContext.getString(R.string.anki_not_found)
        }
        val pending = dao.getNotInAnki()
        if (pending.isEmpty()) return@withContext appContext.getString(R.string.anki_nothing_new)
        var ok = 0
        var failed = 0
        for (item in pending) {
            val noteId = AnkiBridge.addNote(appContext, prefs.ankiDeck, prefs.ankiModel, item)
            if (noteId != null && noteId > 0) {
                dao.update(item.copy(ankiNoteId = noteId)); ok++
            } else failed++
        }
        if (failed == 0) appContext.getString(R.string.anki_pushed, ok)
        else appContext.getString(R.string.anki_partial, ok, failed)
    }

    companion object {
        @Volatile
        private var INSTANCE: Repo? = null

        fun get(context: Context): Repo = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Repo(context).also { INSTANCE = it }
        }

        fun message(context: Context, result: SaveResult): String = when (result.outcome) {
            SaveOutcome.SAVED -> context.getString(R.string.saved_item, result.text)
            SaveOutcome.DUPLICATE -> context.getString(R.string.already_saved)
            SaveOutcome.EMPTY -> context.getString(R.string.nothing_to_save)
        }
    }
}

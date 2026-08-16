package com.lingualens.app.anki

import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import com.lingualens.app.data.SavedItem

/**
 * Talks to AnkiDroid through its public ContentProvider
 * (content://com.ichi2.anki.flashcards). No extra library needed.
 */
object AnkiBridge {

    private const val TAG = "AnkiBridge"
    const val AUTHORITY = "com.ichi2.anki.flashcards"
    const val PERMISSION = "com.ichi2.anki.permission.READ_WRITE_DATABASE"
    private const val FS = "\u001f" // AnkiDroid field separator

    private val NOTES: Uri = Uri.parse("content://$AUTHORITY/notes")
    private val MODELS: Uri = Uri.parse("content://$AUTHORITY/models")
    private val DECKS: Uri = Uri.parse("content://$AUTHORITY/decks")

    fun isAvailable(context: Context): Boolean = try {
        context.packageManager.resolveContentProvider(AUTHORITY, 0) != null
    } catch (t: Throwable) {
        false
    }

    fun hasPermission(context: Context): Boolean =
        context.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED

    fun findDeckId(context: Context, deckName: String): Long? {
        return try {
            context.contentResolver.query(DECKS, null, null, null, null)?.use { c ->
                val idIdx = c.getColumnIndex("deck_id")
                val nameIdx = c.getColumnIndex("deck_name")
                if (idIdx < 0 || nameIdx < 0) return null
                while (c.moveToNext()) {
                    if (c.getString(nameIdx).equals(deckName, ignoreCase = true)) {
                        return c.getLong(idIdx)
                    }
                }
            }
            null
        } catch (t: Throwable) {
            Log.w(TAG, "findDeckId failed", t); null
        }
    }

    fun createDeck(context: Context, deckName: String): Long? {
        return try {
            val values = ContentValues().apply { put("deck_name", deckName) }
            val uri = context.contentResolver.insert(DECKS, values) ?: return null
            uri.lastPathSegment?.toLongOrNull()
        } catch (t: Throwable) {
            Log.w(TAG, "createDeck failed", t); null
        }
    }

    fun findOrCreateDeck(context: Context, deckName: String): Long? =
        findDeckId(context, deckName) ?: createDeck(context, deckName)

    fun listModels(context: Context): List<Pair<Long, String>> {
        val out = ArrayList<Pair<Long, String>>()
        try {
            context.contentResolver.query(MODELS, null, null, null, null)?.use { c ->
                val idIdx = c.getColumnIndex("_id")
                val nameIdx = c.getColumnIndex("name")
                if (idIdx < 0 || nameIdx < 0) return out
                while (c.moveToNext()) out.add(c.getLong(idIdx) to c.getString(nameIdx))
            }
        } catch (t: Throwable) {
            Log.w(TAG, "listModels failed", t)
        }
        return out
    }

    /** Returns modelId to fieldCount. Falls back to any two-field model if the named one is missing. */
    private fun resolveModel(context: Context, modelName: String): Pair<Long, Int>? {
        try {
            var fallback: Long? = null
            context.contentResolver.query(MODELS, null, null, null, null)?.use { c ->
                val idIdx = c.getColumnIndex("_id")
                val nameIdx = c.getColumnIndex("name")
                val fieldsIdx = c.getColumnIndex("field_names")
                if (idIdx < 0 || nameIdx < 0) return null
                while (c.moveToNext()) {
                    val id = c.getLong(idIdx)
                    val name = c.getString(nameIdx) ?: ""
                    val fields = if (fieldsIdx >= 0) c.getString(fieldsIdx) ?: "" else ""
                    val count = if (fields.isEmpty()) 2 else fields.split(FS).size
                    if (name.equals(modelName, ignoreCase = true)) return id to count
                    if (fallback == null && name.startsWith("Basic", ignoreCase = true)) fallback = id
                }
            }
            return fallback?.let { it to 2 }
        } catch (t: Throwable) {
            Log.w(TAG, "resolveModel failed", t)
            return null
        }
    }

    /** Adds one note. Returns the new note id, or null on failure. */
    fun addNote(context: Context, deckName: String, modelName: String, item: SavedItem): Long? {
        if (!isAvailable(context)) return null
        if (!hasPermission(context)) return null
        return try {
            val (modelId, fieldCount) = resolveModel(context, modelName) ?: return null
            val deckId = findOrCreateDeck(context, deckName)

            val back = buildString {
                append(item.english)
                if (item.context.isNotBlank() && item.context != item.dutch) {
                    append("<br><br><i style=\"color:#888;font-size:0.85em\">")
                    append(escape(item.context))
                    append("</i>")
                }
                if (item.source.isNotBlank()) {
                    append("<br><span style=\"color:#aaa;font-size:0.75em\">")
                    append(escape(item.source))
                    append("</span>")
                }
            }

            val fields = ArrayList<String>()
            fields.add(item.dutch)
            fields.add(back)
            while (fields.size < fieldCount) fields.add("")
            val flds = fields.take(fieldCount).joinToString(FS)

            val values = ContentValues().apply {
                put("mid", modelId)
                put("flds", flds)
                put("tags", "tolk dutch " + item.kind)
            }
            val noteUri = context.contentResolver.insert(NOTES, values) ?: return null
            val noteId = noteUri.lastPathSegment?.toLongOrNull() ?: return null

            if (deckId != null) moveCardsToDeck(context, noteId, deckId)
            noteId
        } catch (t: Throwable) {
            Log.w(TAG, "addNote failed", t); null
        }
    }

    private fun moveCardsToDeck(context: Context, noteId: Long, deckId: Long) {
        try {
            val cardsUri = Uri.parse("content://$AUTHORITY/notes/$noteId/cards")
            context.contentResolver.query(cardsUri, null, null, null, null)?.use { c ->
                val ordIdx = c.getColumnIndex("ord")
                while (c.moveToNext()) {
                    val ord = if (ordIdx >= 0) c.getInt(ordIdx) else 0
                    val cardUri = Uri.parse("content://$AUTHORITY/notes/$noteId/cards/$ord")
                    val values = ContentValues().apply { put("deck_id", deckId) }
                    context.contentResolver.update(cardUri, values, null, null)
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "moveCardsToDeck failed", t)
        }
    }

    private fun escape(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}

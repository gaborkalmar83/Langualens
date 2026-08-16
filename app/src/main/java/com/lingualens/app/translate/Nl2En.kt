package com.lingualens.app.translate

import android.util.LruCache
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * On-device Dutch to English translation via ML Kit.
 * The nl<->en model is ~30 MB and is downloaded once, then works fully offline.
 */
object Nl2En {

    private val cache = LruCache<String, String>(4000)
    private val mutex = Mutex()

    @Volatile
    private var client: Translator? = null

    @Volatile
    var modelReady: Boolean = false
        private set

    private fun clientOrCreate(): Translator {
        client?.let { return it }
        synchronized(this) {
            client?.let { return it }
            val options = TranslatorOptions.Builder()
                .setSourceLanguage(TranslateLanguage.DUTCH)
                .setTargetLanguage(TranslateLanguage.ENGLISH)
                .build()
            val c = Translation.getClient(options)
            client = c
            return c
        }
    }

    suspend fun isModelDownloaded(): Boolean = withContext(Dispatchers.IO) {
        try {
            val model = TranslateRemoteModel.Builder(TranslateLanguage.DUTCH).build()
            RemoteModelManager.getInstance().isModelDownloaded(model).await()
        } catch (t: Throwable) {
            false
        }
    }

    /** Downloads the model if needed. Returns null on success, an error message otherwise. */
    suspend fun prepare(requireWifi: Boolean = false): String? = mutex.withLock {
        if (modelReady) return null
        return try {
            val conditions = DownloadConditions.Builder().apply {
                if (requireWifi) requireWifi()
            }.build()
            clientOrCreate().downloadModelIfNeeded(conditions).await()
            modelReady = true
            null
        } catch (t: Throwable) {
            t.message ?: "Model download failed"
        }
    }

    suspend fun translate(text: String): String {
        val key = text.trim()
        if (key.isEmpty()) return ""
        cache.get(key)?.let { return it }
        val err = prepare()
        if (err != null) return ""
        return try {
            val out = clientOrCreate().translate(key).await()
            cache.put(key, out)
            out
        } catch (t: Throwable) {
            ""
        }
    }

    /** Translates a batch, preserving order. Runs a few at a time to stay responsive. */
    suspend fun translateAll(texts: List<String>): List<String> = coroutineScope {
        val results = arrayOfNulls<String>(texts.size)
        texts.chunked(8).forEachIndexed { chunkIndex, chunk ->
            val jobs = chunk.mapIndexed { i, t ->
                async(Dispatchers.Default) { (chunkIndex * 8 + i) to translate(t) }
            }
            jobs.awaitAll().forEach { (idx, value) -> results[idx] = value }
        }
        results.map { it ?: "" }
    }

    /** Deletes the downloaded model to free space. */
    suspend fun deleteModel(): Boolean = withContext(Dispatchers.IO) {
        try {
            val model = TranslateRemoteModel.Builder(TranslateLanguage.DUTCH).build()
            RemoteModelManager.getInstance().deleteDownloadedModel(model).await()
            modelReady = false
            true
        } catch (t: Throwable) {
            false
        }
    }
}

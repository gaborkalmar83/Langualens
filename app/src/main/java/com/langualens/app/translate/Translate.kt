package com.langualens.app.translate

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
import java.util.concurrent.ConcurrentHashMap

/**
 * On-device translation between any two of ML Kit's supported languages.
 * Models are downloaded once per language and then work offline.
 */
object Translate {

    private val clients = ConcurrentHashMap<String, Translator>()
    private val cache = LruCache<String, String>(4000)
    private val prepareLock = Mutex()
    private val readyPairs = HashSet<String>()

    @Volatile
    var sourceTag: String = "nl"
        private set

    @Volatile
    var targetTag: String = "en"
        private set

    fun configure(source: String, target: String) {
        sourceTag = source
        targetTag = target
    }

    val pairKey: String get() = "$sourceTag>$targetTag"

    val isSamePair: Boolean get() = sourceTag == targetTag

    private fun clientFor(source: String, target: String): Translator? {
        val src = TranslateLanguage.fromLanguageTag(source) ?: return null
        val tgt = TranslateLanguage.fromLanguageTag(target) ?: return null
        val key = "$src>$tgt"
        clients[key]?.let { return it }
        return synchronized(clients) {
            clients[key] ?: run {
                val options = TranslatorOptions.Builder()
                    .setSourceLanguage(src)
                    .setTargetLanguage(tgt)
                    .build()
                val created = Translation.getClient(options)
                clients[key] = created
                created
            }
        }
    }

    /** Downloads whatever the current pair needs. Returns null on success, a message otherwise. */
    suspend fun prepare(requireWifi: Boolean = false): String? = prepareLock.withLock {
        val key = pairKey
        if (isSamePair) return null
        if (readyPairs.contains(key)) return null
        val client = clientFor(sourceTag, targetTag) ?: return "Unsupported language pair"
        return try {
            val conditions = DownloadConditions.Builder().apply {
                if (requireWifi) requireWifi()
            }.build()
            client.downloadModelIfNeeded(conditions).await()
            readyPairs.add(key)
            null
        } catch (t: Throwable) {
            t.message ?: "Model download failed"
        }
    }

    /** True when both halves of the current pair are already on the device. */
    suspend fun isReady(): Boolean = withContext(Dispatchers.IO) {
        if (isSamePair) return@withContext true
        try {
            val have = downloadedTags()
            val needed = setOf(sourceTag, targetTag) - setOf("en")
            have.containsAll(needed)
        } catch (t: Throwable) {
            false
        }
    }

    suspend fun downloadedTags(): Set<String> = withContext(Dispatchers.IO) {
        try {
            RemoteModelManager.getInstance()
                .getDownloadedModels(TranslateRemoteModel::class.java)
                .await()
                .mapNotNull { it.language }
                .toSet()
        } catch (t: Throwable) {
            emptySet()
        }
    }

    suspend fun deleteModel(tag: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val language = TranslateLanguage.fromLanguageTag(tag) ?: return@withContext false
            val model = TranslateRemoteModel.Builder(language).build()
            RemoteModelManager.getInstance().deleteDownloadedModel(model).await()
            readyPairs.removeAll { it.contains(tag) }
            true
        } catch (t: Throwable) {
            false
        }
    }

    suspend fun translate(text: String): String {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return ""
        if (isSamePair) return trimmed

        val key = "$pairKey|$trimmed"
        cache.get(key)?.let { return it }

        if (prepare() != null) return ""
        val client = clientFor(sourceTag, targetTag) ?: return ""
        return try {
            val out = client.translate(trimmed).await()
            cache.put(key, out)
            out
        } catch (t: Throwable) {
            ""
        }
    }

    /** Translates a batch, preserving order. */
    suspend fun translateAll(texts: List<String>): List<String> = coroutineScope {
        if (texts.isEmpty()) return@coroutineScope emptyList()
        val results = arrayOfNulls<String>(texts.size)
        texts.chunked(BATCH).forEachIndexed { chunkIndex, chunk ->
            val jobs = chunk.mapIndexed { i, t ->
                async(Dispatchers.Default) { (chunkIndex * BATCH + i) to translate(t) }
            }
            jobs.awaitAll().forEach { (index, value) -> results[index] = value }
        }
        results.map { it ?: "" }
    }

    private const val BATCH = 8
}

package com.lingualens.app.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.lingualens.app.data.SavedItem
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CsvExport {

    /**
     * Writes an Anki-friendly file: tab separated, no header, Front then Back then tags.
     * In Anki: File > Import, field separator Tab, allow HTML in fields.
     */
    fun write(context: Context, items: List<SavedItem>): File {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
        val file = File(dir, "tolk-$stamp.txt")
        file.bufferedWriter().use { w ->
            for (item in items) {
                val front = clean(item.dutch)
                val backParts = StringBuilder(clean(item.english))
                if (item.context.isNotBlank() && item.context != item.dutch) {
                    backParts.append("<br><i>").append(clean(item.context)).append("</i>")
                }
                if (item.source.isNotBlank()) {
                    backParts.append("<br><small>").append(clean(item.source)).append("</small>")
                }
                w.write(front)
                w.write("\t")
                w.write(backParts.toString())
                w.write("\t")
                w.write("tolk dutch ${item.kind}")
                w.newLine()
            }
        }
        return file
    }

    fun share(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "com.lingualens.app.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, file.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Export vocabulary").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    private fun clean(s: String): String =
        s.replace("\t", " ").replace("\n", " ").replace("\r", " ").trim()
}

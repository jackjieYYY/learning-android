package com.jack.englishlearning.data.local

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TranscriptReader(private val context: Context) {
    suspend fun transcript(uri: String): List<String> = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(Uri.parse(uri))?.use { stream ->
            // Bound memory use for accidentally selected binary / enormous files.
            val buffer = java.io.ByteArrayOutputStream()
            val chunk = ByteArray(8192)
            while (true) {
                val count = stream.read(chunk)
                if (count < 0) break
                check(buffer.size() + count <= 4 * 1024 * 1024) { "双语文本超过 4 MB，请拆分后再读取。" }
                buffer.write(chunk, 0, count)
            }
            buffer.toString("UTF-8")
        } ?: error("无法读取双语文本。")
        LearningFiles.paragraphs(text)
    }}

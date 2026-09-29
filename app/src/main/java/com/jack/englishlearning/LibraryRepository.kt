package com.jack.englishlearning

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class LearningVideo(val uri: String, val title: String, val transcriptUri: String?)

class LibraryRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences("library", Context.MODE_PRIVATE)
    var root: String?
        get() = prefs.getString("root", null)
        set(value) { prefs.edit().putString("root", value).apply() }

    fun position(uri: String) = prefs.getLong("position:$uri", 0)
    fun savePosition(uri: String, position: Long) {
        prefs.edit().putLong("position:$uri", position.coerceAtLeast(0)).apply()
    }

    suspend fun scan(root: String): List<LearningVideo> = withContext(Dispatchers.IO) {
        val directory = DocumentFile.fromTreeUri(context, Uri.parse(root))
            ?: error("无法打开学习目录，请重新选择。")
        check(directory.exists() && directory.canRead()) { "目录不存在或授权已失效，请重新选择。" }
        // One level only: the selected directory and each immediate learning-package folder.
        val children = directory.listFiles()
        val groups = listOf(children) + children.filter { it.isDirectory }.map { it.listFiles() }
        groups.flatMap { files ->
            files.filter { it.isFile && LearningFiles.isVideo(it.name.orEmpty()) }.map { video ->
                val name = video.name.orEmpty()
                val transcript = files.firstOrNull {
                    it.isFile && it.name.equals(LearningFiles.transcriptName(name), ignoreCase = true)
                }
                LearningVideo(video.uri.toString(), LearningFiles.title(name), transcript?.uri?.toString())
            }
        }.sortedWith(compareBy<LearningVideo> { it.title.lowercase() }.thenBy { it.uri })
    }

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
    }
}

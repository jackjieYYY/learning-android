package com.jack.englishlearning.data.local

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.jack.englishlearning.domain.model.LearningVideo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DocumentLibraryDataSource(private val context: Context) {
    suspend fun scan(root: String): List<LearningVideo> = withContext(Dispatchers.IO) {
        val directory = DocumentFile.fromTreeUri(context, Uri.parse(root))
            ?: error("无法打开学习目录，请重新选择。")
        check(directory.exists() && directory.canRead()) { "目录不存在或授权已失效，请重新选择。" }
        // One level only: the selected directory and each immediate learning-package folder.
        val children = directory.listFiles()
        val groups = listOf(children) + children.filter { it.isDirectory }.map { it.listFiles() }
        groups.flatMap { files ->
            // DocumentFile metadata can query the provider; read each entry once per directory.
            val namedFiles = files.mapNotNull { file ->
                if (file.isFile) file.name?.let { name -> name to file } else null
            }
            val filesByName = mutableMapOf<String, DocumentFile>()
            namedFiles.forEach { (name, file) -> filesByName.putIfAbsent(name.lowercase(), file) }
            namedFiles.filter { (name, _) -> LearningFiles.isVideo(name) }.map { (name, video) ->
                val transcript = filesByName[LearningFiles.transcriptName(name).lowercase()]
                LearningVideo(video.uri.toString(), LearningFiles.title(name), transcript?.uri?.toString())
            }
        }.sortedWith(compareBy<LearningVideo> { it.title.lowercase() }.thenBy { it.uri })
    }

}

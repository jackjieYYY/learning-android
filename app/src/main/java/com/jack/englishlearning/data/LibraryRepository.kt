package com.jack.englishlearning.data

import android.content.Context
import android.net.Uri
import com.jack.englishlearning.data.local.DocumentLibraryDataSource
import com.jack.englishlearning.data.local.LibraryRootStore
import com.jack.englishlearning.data.local.PlaybackPositionStore
import com.jack.englishlearning.data.local.TranscriptReader
import com.jack.englishlearning.domain.model.LearningVideo

/** Single entry point for local library data. Remote sources can be added without changing screens. */
class LibraryRepository(context: Context) {
    private val roots = LibraryRootStore(context.applicationContext)
    private val documents = DocumentLibraryDataSource(context.applicationContext)
    private val transcripts = TranscriptReader(context.applicationContext)
    private val positions = PlaybackPositionStore(context.applicationContext)

    val root: String? get() = roots.root
    fun selectRoot(uri: Uri) = roots.selectRoot(uri)
    suspend fun scan(root: String): List<LearningVideo> = documents.scan(root)
    suspend fun transcript(uri: String): List<String> = transcripts.transcript(uri)
    fun position(uri: String): Long = positions.position(uri)
    fun savePosition(uri: String, position: Long) = positions.savePosition(uri, position)
}

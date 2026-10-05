package com.jack.englishlearning.data

import com.jack.englishlearning.data.cloud.CloudLibrary
import com.jack.englishlearning.data.cloud.LibrarySyncScheduler
import com.jack.englishlearning.domain.model.TranscriptParagraph
import android.content.Context
import android.net.Uri
import com.jack.englishlearning.data.local.DocumentLibraryDataSource
import com.jack.englishlearning.data.local.LibraryRootStore
import com.jack.englishlearning.data.local.PlaybackPositionStore
import com.jack.englishlearning.data.local.TranscriptReader
import com.jack.englishlearning.domain.model.LearningVideo

/** Local imports and verified cloud downloads share the same offline study interface. */
class LibraryRepository(context: Context) {
    val cloud = CloudLibrary(context.applicationContext)
    val sync by lazy { LibrarySyncScheduler(context.applicationContext) }
    private val roots = LibraryRootStore(context.applicationContext)
    private val documents = DocumentLibraryDataSource(context.applicationContext)
    private val transcripts = TranscriptReader(context.applicationContext)
    private val positions = PlaybackPositionStore(context.applicationContext)

    val root: String? get() = roots.root
    fun selectRoot(uri: Uri) = roots.selectRoot(uri)
    suspend fun scan(root: String): List<LearningVideo> = documents.scan(root)
    suspend fun transcript(uri: String): List<TranscriptParagraph> = transcripts.transcript(uri)
    fun position(uri: String): Long = positions.position(cloud.positionKey(uri))
    fun savePosition(uri: String, position: Long) = positions.savePosition(cloud.positionKey(uri), position)
}

package com.jack.englishlearning.data

import com.jack.englishlearning.data.cloud.CloudLibrary
import com.jack.englishlearning.data.cloud.LibrarySyncScheduler
import com.jack.englishlearning.domain.model.TranscriptParagraph
import android.content.Context
import com.jack.englishlearning.data.local.PlaybackPositionStore
import com.jack.englishlearning.data.local.TranscriptReader

/** Verified cloud downloads provide the offline study interface. */
class LibraryRepository(context: Context) {
    val cloud = CloudLibrary(context.applicationContext)
    val sync by lazy { LibrarySyncScheduler(context.applicationContext) }
    private val transcripts = TranscriptReader(context.applicationContext)
    private val positions = PlaybackPositionStore(context.applicationContext)

    suspend fun transcript(uri: String): List<TranscriptParagraph> = transcripts.transcript(uri)
    fun position(uri: String): Long = positions.position(cloud.positionKey(uri))
    fun savePosition(uri: String, position: Long) = positions.savePosition(cloud.positionKey(uri), position)
}

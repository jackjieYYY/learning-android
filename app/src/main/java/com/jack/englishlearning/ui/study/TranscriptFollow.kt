package com.jack.englishlearning.ui.study

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*
import androidx.media3.common.Player
import com.jack.englishlearning.domain.model.TranscriptParagraph
import kotlinx.coroutines.delay

internal data class TranscriptCue(val startMs: Long, val paragraphIndex: Int)

internal fun transcriptCues(paragraphs: List<TranscriptParagraph>): List<TranscriptCue> =
    paragraphs.flatMapIndexed { index, paragraph -> paragraph.sentences.map { TranscriptCue(it.startMs, index) } }

/** Last sentence starting at or before playback; later equal starts win, matching transcript order. */
internal fun paragraphAt(cues: List<TranscriptCue>, positionMs: Long): Int? {
    if (cues.isEmpty()) return null
    var low = 0
    var high = cues.size
    while (low < high) {
        val middle = (low + high) ushr 1
        if (cues[middle].startMs <= positionMs) low = middle + 1 else high = middle
    }
    return cues[(low - 1).coerceAtLeast(0)].paragraphIndex
}

/** Playback is the only durable progress. Follow paragraph changes and explicit seeks. */
@Composable
internal fun FollowTranscript(player: Player?, mediaId: String, paragraphs: List<TranscriptParagraph>, list: LazyListState) {
    val cues = remember(paragraphs) { transcriptCues(paragraphs) }
    var seekRevision by remember(player) { mutableIntStateOf(0) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
                seekRevision++
            }
        }
        player?.addListener(listener)
        onDispose { player?.removeListener(listener) }
    }
    LaunchedEffect(player, mediaId, cues, list) {
        if (player == null || cues.isEmpty()) return@LaunchedEffect
        var lastParagraph: Int? = null
        var lastSeek = -1
        while (true) {
            if (player.currentMediaItem?.mediaId == mediaId) {
                val paragraph = paragraphAt(cues, player.currentPosition)
                if (paragraph != null && (paragraph != lastParagraph || seekRevision != lastSeek)) {
                    // No independent reading bookmark or follow/resume control.
                    list.scrollToItem(paragraph)
                    lastParagraph = paragraph
                    lastSeek = seekRevision
                }
            }
            delay(250)
        }
    }
}

package com.jack.englishlearning

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.jack.englishlearning.domain.model.TranscriptParagraph
import com.jack.englishlearning.domain.model.TranscriptSentence
import com.jack.englishlearning.ui.study.FollowTranscript
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class TranscriptFollowUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun restoresFromVideoTimeAndFollowsForwardAndBackwardPlayback() {
        val player = mock(Player::class.java)
        `when`(player.currentMediaItem).thenReturn(MediaItem.Builder().setMediaId("lesson").build())
        `when`(player.currentPosition).thenReturn(3500L)
        val paragraphs = List(10) { index ->
            TranscriptParagraph(listOf(TranscriptSentence(index * 1000L, "Paragraph $index")), "中文")
        }
        lateinit var list: LazyListState
        compose.setContent {
            list = rememberLazyListState()
            FollowTranscript(player, "lesson", paragraphs, list)
            LazyColumn(state = list, modifier = Modifier.height(300.dp)) {
                items(paragraphs.size) { Text("Paragraph $it", Modifier.height(200.dp)) }
            }
        }
        compose.waitUntil(5000) { list.firstVisibleItemIndex == 3 }
        compose.runOnIdle { `when`(player.currentPosition).thenReturn(6500L) }
        compose.mainClock.advanceTimeBy(500)
        compose.waitUntil(5000) { list.firstVisibleItemIndex == 6 }
        compose.runOnIdle { `when`(player.currentPosition).thenReturn(1500L) }
        compose.mainClock.advanceTimeBy(500)
        compose.waitUntil(5000) { list.firstVisibleItemIndex == 1 }
        compose.runOnIdle { assertEquals(1, list.firstVisibleItemIndex) }
    }
}

package com.jack.englishlearning

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.jack.englishlearning.playback.resolveMediaItem
import com.jack.englishlearning.playback.resumePosition
import com.jack.englishlearning.ui.study.formatTime
import com.jack.englishlearning.ui.study.stopLesson
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PlaybackLifecycleTest {
    private fun player(uri: String, state: Int = Player.STATE_READY, position: Long = 12_345L): Player {
        val player = mock(Player::class.java)
        `when`(player.currentMediaItem).thenReturn(MediaItem.Builder().setMediaId(uri).setUri(uri).build())
        `when`(player.playbackState).thenReturn(state)
        `when`(player.currentPosition).thenReturn(position)
        return player
    }

    @Test fun leavingLessonSavesPositionAndStopsPlayback() {
        val player = player("file:///a.mp4")
        var saved = -1L

        stopLesson(player, "file:///a.mp4") { saved = it }

        assertEquals(12_345L, saved)
        verify(player).pause()
        verify(player).clearMediaItems()
    }

    @Test fun leavingDoesNotStopAnotherLesson() {
        val player = player("file:///other.mp4")
        var saved = -1L

        stopLesson(player, "file:///a.mp4") { saved = it }

        assertEquals(-1L, saved)
        verify(player, never()).pause()
        verify(player, never()).clearMediaItems()
    }

    @Test fun completedVideoResumesFromStart() {
        assertEquals(0L, resumePosition(player("file:///a.mp4", Player.STATE_ENDED)))
        assertEquals(12_345L, resumePosition(player("file:///a.mp4")))
    }

    @Test fun controllerItemsRegainTheirUriFromTheMediaId() {
        val fromController = MediaItem.Builder().setMediaId("content://lessons/a.mp4").build()
        assertEquals(Uri.parse("content://lessons/a.mp4"), resolveMediaItem(fromController).localConfiguration?.uri)
        val local = MediaItem.Builder().setMediaId("id").setUri("file:///b.mp4").build()
        assertEquals(local, resolveMediaItem(local))
    }

    @Test fun timeFormatting() {
        assertEquals("0:00", formatTime(-5))
        assertEquals("1:05", formatTime(65_000))
        assertEquals("1:01:01", formatTime(3_661_000))
    }
}

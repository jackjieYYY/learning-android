package com.jack.englishlearning

import androidx.media3.common.Player
import com.jack.englishlearning.ui.study.onPlaybackStopped
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify

class PlaybackLifecycleTest {
    @Test fun leavingAppSavesPositionAndPausesPlayback() {
        val player = mock(Player::class.java)
        `when`(player.playbackState).thenReturn(Player.STATE_READY)
        `when`(player.currentPosition).thenReturn(12_345L)
        var savedPosition = -1L

        onPlaybackStopped(player, inPip = false) { savedPosition = it }

        assertEquals(12_345L, savedPosition)
        verify(player).pause()
    }

    @Test fun enteringPipKeepsPlaying() {
        val player = mock(Player::class.java)
        `when`(player.playbackState).thenReturn(Player.STATE_READY)
        `when`(player.currentPosition).thenReturn(12_345L)
        var savedPosition = -1L

        onPlaybackStopped(player, inPip = true) { savedPosition = it }

        assertEquals(12_345L, savedPosition)
        verify(player, never()).pause()
    }

    @Test fun completedVideoResumesFromStart() {
        val player = mock(Player::class.java)
        `when`(player.playbackState).thenReturn(Player.STATE_ENDED)
        var savedPosition = -1L

        onPlaybackStopped(player, inPip = false) { savedPosition = it }

        assertEquals(0L, savedPosition)
    }
}

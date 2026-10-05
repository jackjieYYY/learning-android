package com.jack.englishlearning

import androidx.media3.common.C
import androidx.media3.common.Player
import com.jack.englishlearning.ui.study.seekPlayback
import com.jack.englishlearning.ui.study.togglePlayback
import org.junit.Test
import org.mockito.Mockito.*

class PlaybackControlsTest {
    private fun player(duration: Long = 10_000): Player = mock(Player::class.java).also {
        `when`(it.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)).thenReturn(true)
        `when`(it.duration).thenReturn(duration)
    }
    @Test fun sentenceSeeksAndPlays() {
        val player = player()
        seekPlayback(player, 2500, play = true)
        verify(player).seekTo(2500)
        verify(player).play()
    }
    @Test fun relativeSeekingClampsAndDoesNotChangePlaybackState() {
        val player = player()
        seekPlayback(player, -3000)
        seekPlayback(player, 15_000)
        verify(player).seekTo(0)
        verify(player).seekTo(10_000)
        verify(player, never()).play()
        verify(player, never()).pause()
    }
    @Test fun unknownDurationStillAllowsSeeking() {
        val player = player(C.TIME_UNSET)
        seekPlayback(player, 20_000)
        verify(player).seekTo(20_000)
    }
    @Test fun unavailableSeekingDoesNotStartPlayback() {
        val player = player()
        `when`(player.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)).thenReturn(false)
        seekPlayback(player, 2500, play = true)
        verify(player, never()).seekTo(anyLong())
        verify(player, never()).play()
    }
    @Test fun togglePausesEvenWhileBuffering() {
        val player = player()
        `when`(player.playWhenReady).thenReturn(true)
        `when`(player.playbackState).thenReturn(Player.STATE_BUFFERING)
        togglePlayback(player)
        verify(player).pause()
    }
    @Test fun toggleResumesPausedPlayback() {
        val player = player()
        togglePlayback(player)
        verify(player).play()
    }
    @Test fun toggleRestartsEndedVideo() {
        val player = player()
        `when`(player.playbackState).thenReturn(Player.STATE_ENDED)
        togglePlayback(player)
        verify(player).seekTo(0)
        verify(player).play()
    }
}

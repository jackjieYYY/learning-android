package com.jack.englishlearning.ui.study

import androidx.media3.common.C
import androidx.media3.common.Player

internal fun seekPlayback(player: Player, positionMs: Long, play: Boolean = false) {
    if (!player.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)) return
    val duration = player.duration
    val target = if (duration != C.TIME_UNSET && duration >= 0) positionMs.coerceIn(0, duration)
        else positionMs.coerceAtLeast(0)
    player.seekTo(target)
    if (play) player.play()
}

internal fun togglePlayback(player: Player) {
    if (player.playWhenReady && player.playbackState != Player.STATE_ENDED) player.pause()
    else {
        if (player.playbackState == Player.STATE_ENDED) seekPlayback(player, 0)
        player.play()
    }
}

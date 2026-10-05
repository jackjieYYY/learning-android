package com.jack.englishlearning.ui.study

import android.app.PictureInPictureParams
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.jack.englishlearning.MainActivity
import com.jack.englishlearning.data.LibraryRepository
import com.jack.englishlearning.domain.model.LearningVideo
import kotlinx.coroutines.delay

internal fun onPlaybackStopped(player: Player, inPip: Boolean, savePosition: (Long) -> Unit) {
    savePosition(if (player.playbackState == Player.STATE_ENDED) 0 else player.currentPosition)
    if (!inPip) player.pause()
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
internal fun VideoPane(video: LearningVideo, repository: LibraryRepository, modifier: Modifier,
    onPlayerChanged: (Player?) -> Unit = {}) {
    val context = LocalContext.current
    val activity = LocalActivity.current as MainActivity
    val supportsPip = remember { context.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val positionKey = remember(video.uri) { repository.cloud.positionKey(video.uri) }
    var isPlaying by remember(video.uri) { mutableStateOf(false) }
    var playbackError by remember(video.uri) { mutableStateOf<String?>(null) }
    val player = remember(video.uri) {
        ExoPlayer.Builder(context).build().apply {
            setAudioAttributes(androidx.media3.common.AudioAttributes.DEFAULT, true)
            setHandleAudioBecomingNoisy(true)
            setMediaItem(MediaItem.fromUri(video.uri))
            seekTo(repository.position(positionKey))
            prepare()
            // Explicit play avoids surprise audio when opening or returning to the app.
            playWhenReady = false
        }
    }
    val playerChanged by rememberUpdatedState(onPlayerChanged)
    DisposableEffect(player) {
        playerChanged(player)
        onDispose { playerChanged(null) }
    }
    DisposableEffect(player, lifecycleOwner) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                if (supportsPip && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    activity.setPictureInPictureParams(
                        PictureInPictureParams.Builder().setAutoEnterEnabled(playing).build()
                    )
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                playbackError = "视频无法播放：${error.errorCodeName}。请检查文件是否完整、目录权限是否有效。"
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) repository.savePosition(positionKey, 0)
            }
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                // PiP can enter after ON_PAUSE; only stop playback when truly backgrounded.
                onPlaybackStopped(player, activity.isInPictureInPictureMode) { position ->
                    repository.savePosition(positionKey, position)
                }
            }
        }
        player.addListener(listener)
        if (supportsPip && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            activity.setPictureInPictureParams(PictureInPictureParams.Builder().setAutoEnterEnabled(player.isPlaying).build())
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            if (supportsPip && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                activity.setPictureInPictureParams(PictureInPictureParams.Builder().setAutoEnterEnabled(false).build())
            }
            repository.savePosition(positionKey, if (player.playbackState == Player.STATE_ENDED) 0 else player.currentPosition)
            lifecycleOwner.lifecycle.removeObserver(observer)
            player.removeListener(listener)
            player.release()
        }
    }
    LaunchedEffect(player) {
        while (true) {
            delay(5000)
            if (player.isPlaying) repository.savePosition(positionKey, player.currentPosition)
        }
    }
    Box(modifier.background(Color.Black), contentAlignment = Alignment.Center) {
        AndroidView(factory = { viewContext ->
            PlayerView(viewContext).apply {
                this.player = player
                setShowNextButton(false)
                setShowPreviousButton(false)
            }
        }, update = {
            it.player = player
            it.useController = !activity.inPictureInPicture
            it.keepScreenOn = isPlaying
        }, onRelease = { it.player = null }, modifier = Modifier.fillMaxSize())
        playbackError?.let { message ->
            Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(16.dp)) {
                Text(message, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { playbackError = null; player.prepare() }) { Text("重试") }
            }
        }
        if (supportsPip && !activity.inPictureInPicture && isPlaying) {
            Button(
                onClick = {
                    val params = PictureInPictureParams.Builder().apply {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) setAutoEnterEnabled(true)
                    }.build()
                    activity.enterPictureInPictureMode(params)
                },
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
            ) { Text("小窗") }
        }
    }
}

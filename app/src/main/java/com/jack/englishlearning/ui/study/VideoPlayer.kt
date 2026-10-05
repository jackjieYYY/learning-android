package com.jack.englishlearning.ui.study

import android.app.PictureInPictureParams
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.PlayerView
import com.jack.englishlearning.MainActivity
import com.jack.englishlearning.data.LibraryRepository
import com.jack.englishlearning.domain.model.LearningVideo
import com.jack.englishlearning.playback.PlaybackService
import com.jack.englishlearning.playback.resumePosition
import com.jack.englishlearning.ui.pixel.*
import com.jack.englishlearning.ui.theme.PixelTheme
import kotlinx.coroutines.delay
import java.util.Locale

/** Leaving the lesson stops playback; locking the screen or switching apps does not. */
internal fun stopLesson(player: Player, uri: String, savePosition: (Long) -> Unit) {
    if (player.currentMediaItem?.mediaId != uri) return
    savePosition(resumePosition(player))
    player.pause()
    player.clearMediaItems()
}

internal fun formatTime(ms: Long): String {
    val seconds = (ms.coerceAtLeast(0) / 1000)
    return if (seconds >= 3600) String.format(Locale.ROOT, "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
    else String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60)
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
internal fun VideoPane(video: LearningVideo, repository: LibraryRepository, modifier: Modifier,
    onPlayerChanged: (Player?) -> Unit = {}) {
    val context = LocalContext.current
    val activity = LocalActivity.current as MainActivity
    val palette = PixelTheme.palette
    val supportsPip = remember { context.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE) }
    var controller by remember { mutableStateOf<MediaController?>(null) }
    var isPlaying by remember(video.uri) { mutableStateOf(false) }
    var playbackError by remember(video.uri) { mutableStateOf<String?>(null) }
    DisposableEffect(Unit) {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            controller = try { future.get() } catch (_: Exception) {
                playbackError = "播放服务启动失败，请返回后重新打开课程。"
                null
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            controller = null
            MediaController.releaseFuture(future)
        }
    }
    val player = controller
    val playerChanged by rememberUpdatedState(onPlayerChanged)
    DisposableEffect(player) {
        playerChanged(player)
        onDispose { playerChanged(null) }
    }
    LaunchedEffect(player, video.uri) {
        val current = player ?: return@LaunchedEffect
        if (current.currentMediaItem?.mediaId != video.uri) {
            val item = MediaItem.Builder().setMediaId(video.uri).setUri(video.uri)
                .setMediaMetadata(MediaMetadata.Builder().setTitle(video.title).setArtist("英语听力").build())
                .build()
            current.setMediaItem(item, repository.position(video.uri))
            // Explicit play avoids surprise audio when opening or returning to the app.
            current.playWhenReady = false
            current.prepare()
        } else if (current.playbackState == Player.STATE_IDLE) current.prepare()
    }
    if (player != null) DisposableEffect(player, video.uri) {
        fun updatePip(playing: Boolean) {
            if (supportsPip && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                activity.setPictureInPictureParams(PictureInPictureParams.Builder().setAutoEnterEnabled(playing).build())
            }
        }
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                updatePip(playing)
            }

            override fun onPlayerError(error: PlaybackException) {
                playbackError = "视频无法播放：${error.errorCodeName}。请检查文件是否完整、目录权限是否有效。"
            }
        }
        isPlaying = player.isPlaying
        updatePip(player.isPlaying)
        player.addListener(listener)
        onDispose {
            updatePip(false)
            player.removeListener(listener)
            // Activity recreation or task removal keeps the service playing; going back to the library stops it.
            if (!activity.isChangingConfigurations && !activity.isFinishing) {
                stopLesson(player, video.uri) { repository.savePosition(video.uri, it) }
            }
        }
    }
    Column(modifier.background(Color.Black)) {
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (player != null) AndroidView(factory = { viewContext ->
                PlayerView(viewContext).apply {
                    useController = false
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                }
            }, update = {
                it.player = player
                it.keepScreenOn = isPlaying
            }, onRelease = { it.player = null }, modifier = Modifier.fillMaxSize()
                .pointerInput(player) { detectTapGestures(onDoubleTap = { togglePlayback(player) }) })
            playbackError?.let { message ->
                PixelPanel(Modifier.fillMaxWidth().padding(8.dp), colors = palette.paperColors(), textured = false) {
                    Text(message, style = MaterialTheme.typography.labelMedium, color = palette.error)
                    Spacer(Modifier.height(8.dp))
                    PixelButton("重试", { playbackError = null; player?.prepare() }, kind = PixelButtonKind.Secondary,
                        icon = PixelGlyphs.Refresh)
                }
            }
        }
        if (player != null && !activity.inPictureInPicture) {
            PlaybackBar(player, isPlaying, showPip = supportsPip, onPip = {
                val params = PictureInPictureParams.Builder().apply {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) setAutoEnterEnabled(true)
                }.build()
                activity.enterPictureInPictureMode(params)
            })
        }
    }
}

/** Pixel play/pause, time and seek bar under the video. */
@Composable
private fun PlaybackBar(player: Player, isPlaying: Boolean, showPip: Boolean, onPip: () -> Unit) {
    val palette = PixelTheme.palette
    var position by remember(player) { mutableLongStateOf(player.currentPosition) }
    var duration by remember(player) { mutableLongStateOf(player.duration) }
    var dragFraction by remember(player) { mutableStateOf<Float?>(null) }
    LaunchedEffect(player) {
        while (true) {
            position = player.currentPosition
            duration = player.duration
            delay(250)
        }
    }
    val known = duration != C.TIME_UNSET && duration > 0
    val fraction = dragFraction ?: if (known) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    Row(Modifier.fillMaxWidth().background(palette.panel).pixelBlock(palette.panelColors(), seed = 9)
        .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PixelIconButton(if (isPlaying) PixelGlyphs.Pause else PixelGlyphs.Play, if (isPlaying) "暂停" else "播放",
            { togglePlayback(player) }, kind = PixelButtonKind.Primary)
        val shown = if (known) (fraction * duration).toLong() else position
        Text("${formatTime(shown)} / ${if (known) formatTime(duration) else "--:--"}",
            style = MaterialTheme.typography.labelMedium, color = palette.text)
        Canvas(Modifier.weight(1f).height(28.dp)
            .semantics {
                contentDescription = "播放进度"
                progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
            }
            .pointerInput(player, known) {
                if (!known) return@pointerInput
                detectTapGestures { offset -> seekPlayback(player, (offset.x / size.width * duration).toLong()) }
            }
            .pointerInput(player, known) {
                if (!known) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = { offset -> dragFraction = (offset.x / size.width).coerceIn(0f, 1f) },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        dragFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                    },
                    onDragEnd = {
                        dragFraction?.let { seekPlayback(player, (it * duration).toLong()) }
                        dragFraction = null
                    },
                    onDragCancel = { dragFraction = null })
            }) {
            val p = artPixelPx()
            val trackHeight = 3 * p
            val top = (size.height - trackHeight) / 2
            drawRect(palette.outline, Offset(0f, top - p), Size(size.width, trackHeight + 2 * p))
            drawRect(palette.panelShade, Offset(p, top), Size(size.width - 2 * p, trackHeight))
            val filled = (size.width - 2 * p) * fraction
            drawRect(palette.accent, Offset(p, top), Size(filled, trackHeight))
            val thumb = 6 * p
            val x = (p + filled - thumb / 2).coerceIn(0f, size.width - thumb)
            val y = (size.height - thumb - 2 * p) / 2
            drawRect(palette.outline, Offset(x, y), Size(thumb, thumb + 2 * p))
            drawRect(palette.accentLight, Offset(x + p, y + p), Size(thumb - 2 * p, thumb))
            drawRect(palette.accent, Offset(x + 2 * p, y + 2 * p), Size(thumb - 3 * p, thumb - 2 * p))
        }
        if (showPip && isPlaying) PixelIconButton(PixelGlyphs.PictureInPicture, "小窗", onPip)
    }
}

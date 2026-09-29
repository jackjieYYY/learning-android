package com.jack.englishlearning

import android.app.PictureInPictureParams
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    var inPictureInPicture by mutableStateOf(false)
        private set

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPictureInPicture = isInPictureInPictureMode
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF245E56))) {
                Surface(Modifier.fillMaxSize()) { LearningApp() }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LearningApp() {
    val context = LocalContext.current
    val repository = remember { LibraryRepository(context.applicationContext) }
    var root by remember { mutableStateOf(repository.root) }
    var refresh by remember { mutableIntStateOf(0) }
    var videos by remember { mutableStateOf(emptyList<LearningVideo>()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedUri by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedTitle by rememberSaveable { mutableStateOf("") }
    var selectedTranscript by rememberSaveable { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                val previous = root
                repository.root = uri.toString()
                root = uri.toString()
                refresh++
                if (previous != null && previous != root) {
                    // Only release the old permission after the new one has been saved.
                    try {
                        context.contentResolver.releasePersistableUriPermission(Uri.parse(previous), Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    } catch (_: SecurityException) { /* Already revoked by provider. */ }
                }
            } catch (_: SecurityException) {
                error = "无法保留此目录的读取权限，请选择手机本地的学习目录。"
            }
        }
    }
    LaunchedEffect(root, refresh) {
        videos = emptyList()
        error = null
        val current = root ?: return@LaunchedEffect
        loading = true
        try {
            videos = repository.scan(current)
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (exception: Exception) {
            error = exception.message ?: "读取目录失败，请重新选择。"
        } finally {
            loading = false
        }
    }
    val currentUri = selectedUri
    if (currentUri != null) {
        BackHandler { selectedUri = null }
        StudyPage(LearningVideo(currentUri, selectedTitle, selectedTranscript), repository) { selectedUri = null }
    } else {
        Scaffold(topBar = { TopAppBar(title = { Text("英语听力") }) }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { picker.launch(root?.let(Uri::parse)) }) {
                        Text(if (root == null) "选择学习目录" else "更换目录")
                    }
                    OutlinedButton(onClick = { refresh++ }, enabled = root != null && !loading) { Text("刷新") }
                }
                Text("本地视频 · 离线学习", style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(vertical = 12.dp))
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 12.dp)) }
                if (!loading && videos.isEmpty() && error == null) {
                    Text("将视频和双语文本复制到 Download/EnglishLearning 下，每个视频一个文件夹，再选择 EnglishLearning 目录。\n\n文件示例：\nmy-video.mp4\nmy-video.bilingual.txt\n\n复制新视频后点击“刷新”。", modifier = Modifier.padding(vertical = 20.dp))
                }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                    items(videos, key = { it.uri }) { video ->
                        Card(onClick = {
                            selectedTitle = video.title
                            selectedTranscript = video.transcriptUri
                            selectedUri = video.uri
                        }, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp)) {
                                Text(video.title, style = MaterialTheme.typography.titleMedium)
                                Spacer(Modifier.height(6.dp))
                                Text(if (video.transcriptUri == null) "缺少双语文本 · 可播放视频" else "视频 + 中英对照",
                                    style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StudyPage(video: LearningVideo, repository: LibraryRepository, onBack: () -> Unit) {
    var paragraphs by remember(video.uri) { mutableStateOf(emptyList<String>()) }
    var textError by remember(video.uri) { mutableStateOf<String?>(null) }
    var loading by remember(video.uri) { mutableStateOf(true) }
    val listState = rememberLazyListState()
    val inPip = (LocalActivity.current as MainActivity).inPictureInPicture
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    // Move the same player composition between layouts without releasing playback.
    val videoPane = remember(video.uri, repository) {
        movableContentOf<Modifier> { modifier -> VideoPane(video, repository, modifier) }
    }
    LaunchedEffect(video.transcriptUri) {
        try {
            val uri = video.transcriptUri
            if (uri == null) textError = "此视频没有双语文本，请添加同名 .bilingual.txt 后返回列表刷新。"
            else {
                paragraphs = repository.transcript(uri)
                if (paragraphs.isEmpty()) textError = "双语文本为空。"
            }
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (exception: Exception) {
            textError = exception.message ?: "双语文本读取失败。"
        } finally {
            loading = false
        }
    }
    Scaffold(topBar = {
        if (!inPip) TopAppBar(title = { Text(video.title, maxLines = 1) }, navigationIcon = {
            TextButton(onClick = onBack) { Text("返回") }
        })
    }) { padding ->
        val contentModifier = Modifier.fillMaxSize().padding(if (inPip) PaddingValues(0.dp) else padding)
        val bilingualPane: @Composable (Modifier) -> Unit = { modifier ->
            Column(modifier) {
                Text("中英对照 · 手动滑动", style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                when {
                    loading -> CircularProgressIndicator(Modifier.padding(16.dp))
                    textError != null -> Text(textError.orEmpty(), modifier = Modifier.padding(16.dp))
                    else -> SelectionContainer(Modifier.weight(1f).fillMaxWidth()) {
                        LazyColumn(state = listState, modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            items(paragraphs.size) { index ->
                                Text(paragraphs[index], fontSize = 18.sp, lineHeight = 28.sp)
                            }
                        }
                    }
                }
            }
        }
        when {
            inPip -> Box(contentModifier) {
                videoPane(Modifier.fillMaxSize())
            }
            isLandscape -> Row(contentModifier) {
                videoPane(Modifier.weight(1f).fillMaxHeight())
                VerticalDivider()
                bilingualPane(Modifier.weight(3f).fillMaxHeight())
            }
            else -> Column(contentModifier) {
                videoPane(Modifier.weight(1f).fillMaxWidth())
                HorizontalDivider()
                bilingualPane(Modifier.weight(3f).fillMaxWidth())
            }
        }
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun VideoPane(video: LearningVideo, repository: LibraryRepository, modifier: Modifier) {
    val context = LocalContext.current
    val activity = LocalActivity.current as MainActivity
    val supportsPip = remember { context.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE) }
    val lifecycleOwner = LocalLifecycleOwner.current
    var isPlaying by remember(video.uri) { mutableStateOf(false) }
    var playbackError by remember(video.uri) { mutableStateOf<String?>(null) }
    val player = remember(video.uri) {
        ExoPlayer.Builder(context).build().apply {
            setAudioAttributes(androidx.media3.common.AudioAttributes.DEFAULT, true)
            setHandleAudioBecomingNoisy(true)
            setMediaItem(MediaItem.fromUri(video.uri))
            seekTo(repository.position(video.uri))
            prepare()
            // Explicit play avoids surprise audio when opening or returning to the app.
            playWhenReady = false
        }
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
                if (playbackState == Player.STATE_ENDED) repository.savePosition(video.uri, 0)
            }
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                // PiP can enter after ON_PAUSE; only stop playback when truly backgrounded.
                repository.savePosition(video.uri, if (player.playbackState == Player.STATE_ENDED) 0 else player.currentPosition)
                if (!activity.isInPictureInPictureMode) player.pause()
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
            repository.savePosition(video.uri, if (player.playbackState == Player.STATE_ENDED) 0 else player.currentPosition)
            lifecycleOwner.lifecycle.removeObserver(observer)
            player.removeListener(listener)
            player.release()
        }
    }
    LaunchedEffect(player) {
        while (true) {
            delay(5000)
            if (player.isPlaying) repository.savePosition(video.uri, player.currentPosition)
        }
    }
    Box(modifier.background(Color.Black), contentAlignment = Alignment.Center) {
        AndroidView(factory = { viewContext ->
            PlayerView(viewContext).apply {
                this.player = player
                keepScreenOn = true
                setShowNextButton(false)
                setShowPreviousButton(false)
            }
        }, update = {
            it.player = player
            it.useController = !activity.inPictureInPicture
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

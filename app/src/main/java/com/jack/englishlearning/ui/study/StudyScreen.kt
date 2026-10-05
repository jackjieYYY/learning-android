package com.jack.englishlearning.ui.study

import android.content.res.Configuration
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.media3.common.Player
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jack.englishlearning.MainActivity
import com.jack.englishlearning.data.LibraryRepository
import com.jack.englishlearning.domain.model.LearningVideo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StudyScreen(video: LearningVideo, repository: LibraryRepository, state: StudyUiState, onBack: () -> Unit) {
    var player by remember(video.uri) { mutableStateOf<Player?>(null) }
    var explanationIndex by rememberSaveable(video.uri) { mutableStateOf<Int?>(null) }
    var pausedForExplanation by rememberSaveable(video.uri) { mutableStateOf(false) }
    fun showExplanation(index: Int) {
        if (state.paragraphs.getOrNull(index)?.explanation == null) return
        pausedForExplanation = player?.playWhenReady == true
        player?.pause()
        explanationIndex = index
    }
    val swipeThreshold = with(LocalDensity.current) { 48.dp.toPx() }
    val listState = rememberLazyListState()
    val inPip = (LocalActivity.current as MainActivity).inPictureInPicture
    LaunchedEffect(inPip) {
        if (inPip) explanationIndex = null
    }
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    // Move the same player composition between layouts without releasing playback.
    val videoPane = remember(video.uri, repository) {
        movableContentOf<Modifier> { modifier -> VideoPane(video, repository, modifier) { player = it } }
    }
    Scaffold(topBar = {
        if (!inPip) TopAppBar(title = { Text(video.title, maxLines = 1) }, navigationIcon = {
            TextButton(onClick = onBack) { Text("返回") }
        })
    }) { padding ->
        val contentModifier = Modifier.fillMaxSize().padding(if (inPip) PaddingValues(0.dp) else padding)
        val bilingualPane: @Composable (Modifier) -> Unit = { modifier ->
            Column(modifier) {
                Text("点英文句子播放 · 双击暂停/继续 · 左滑 −5秒 / 右滑 +5秒" +
                    if (state.paragraphs.any { it.explanation != null }) " · 长按看讲解" else "",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                when {
                    state.loading -> CircularProgressIndicator(Modifier.padding(16.dp))
                    state.error != null -> Text(state.error, modifier = Modifier.padding(16.dp))
                    else -> LazyColumn(state = listState,
                        modifier = Modifier.weight(1f).fillMaxWidth()
                            .pointerInput(player, swipeThreshold) {
                                var distance = 0f
                                detectHorizontalDragGestures(
                                    onDragStart = { distance = 0f },
                                    onHorizontalDrag = { change, amount ->
                                        change.consume()
                                        distance += amount
                                    },
                                    onDragCancel = { distance = 0f },
                                    onDragEnd = {
                                        if (kotlin.math.abs(distance) >= swipeThreshold) {
                                            player?.let { seekPlayback(it, it.currentPosition + if (distance > 0) 5000 else -5000) }
                                        }
                                        distance = 0f
                                    }
                                )
                            }
                            .pointerInput(player) {
                                // Handles double taps in the gaps; rows handle their own taps.
                                detectTapGestures(onDoubleTap = { player?.let(::togglePlayback) })
                            },
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        itemsIndexed(state.paragraphs) { index, paragraph ->
                            val explain: (() -> Unit)? = if (paragraph.explanation != null)
                                ({ showExplanation(index) }) else null
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                EnglishParagraph(paragraph.sentences, Modifier.fillMaxWidth(),
                                    onSentence = { start -> player?.let { seekPlayback(it, start, play = true) } },
                                    onDoubleTap = { player?.let(::togglePlayback) },
                                    onExplanation = explain)
                                Text(paragraph.translation, fontSize = 18.sp, lineHeight = 28.sp,
                                    modifier = Modifier.fillMaxWidth().then(if (explain != null)
                                        Modifier.semantics {
                                            customActions = listOf(CustomAccessibilityAction("查看老师讲解") {
                                                explain(); true
                                            })
                                        }.pointerInput(paragraph) {
                                            detectTapGestures(
                                                onLongPress = { explain() },
                                                onDoubleTap = { player?.let(::togglePlayback) })
                                        } else Modifier))
                                if (explain != null) TextButton(onClick = explain) { Text("讲解") }
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
    val explainedParagraph = explanationIndex?.let(state.paragraphs::getOrNull)
    if (!inPip && explainedParagraph?.explanation != null) {
        ExplanationSheet(explainedParagraph, pausedForExplanation,
            onDismiss = { explanationIndex = null },
            onPlayParagraph = {
                explanationIndex = null
                player?.let { seekPlayback(it, explainedParagraph.sentences.first().startMs, play = true) }
            })
    }
}


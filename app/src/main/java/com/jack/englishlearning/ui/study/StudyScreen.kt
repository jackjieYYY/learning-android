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
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.jack.englishlearning.ui.pixel.*
import com.jack.englishlearning.ui.theme.PixelTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.jack.englishlearning.MainActivity
import com.jack.englishlearning.data.LibraryRepository
import com.jack.englishlearning.domain.model.LearningVideo

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
    val palette = PixelTheme.palette
    Column(Modifier.fillMaxSize()) {
        if (!inPip) PixelTopBar(video.title, navigation = { PixelIconButton(PixelGlyphs.Back, "返回", onBack) })
        val contentModifier = Modifier.weight(1f).fillMaxWidth()
        val bilingualPane: @Composable (Modifier) -> Unit = { modifier ->
            Column(modifier) {
                Text("点句播放，双击暂停或继续，左右滑动调整 5 秒。" +
                    (if (state.paragraphs.any { it.explanation != null }) "\n长按段落看讲解。" else ""),
                    style = MaterialTheme.typography.labelSmall, color = palette.textMuted,
                    modifier = Modifier.fillMaxWidth().background(palette.background)
                        .padding(horizontal = 16.dp, vertical = 8.dp))
                PixelDivider()
                when {
                    state.loading -> PixelProgressBar(null, Modifier.padding(16.dp))
                    state.error != null -> Text(state.error, style = MaterialTheme.typography.bodyMedium,
                        color = palette.error, modifier = Modifier.padding(16.dp))
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
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        itemsIndexed(state.paragraphs) { index, paragraph ->
                            val explain: (() -> Unit)? = if (paragraph.explanation != null)
                                ({ showExplanation(index) }) else null
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (index > 0) PixelDivider(Modifier.padding(bottom = 8.dp))
                                EnglishParagraph(paragraph.sentences, Modifier.fillMaxWidth(),
                                    onSentence = { start -> player?.let { seekPlayback(it, start, play = true) } },
                                    onDoubleTap = { player?.let(::togglePlayback) },
                                    onExplanation = explain)
                                Text(paragraph.translation, style = MaterialTheme.typography.bodyLarge,
                                    color = palette.textMuted,
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
                                if (explain != null) PixelButton("讲解", explain, kind = PixelButtonKind.Secondary,
                                    icon = PixelGlyphs.Book)
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
            isLandscape -> Row(contentModifier.navigationBarsPadding()) {
                videoPane(Modifier.weight(1f).fillMaxHeight())
                Spacer(Modifier.fillMaxHeight().width(2.dp).background(palette.outline))
                bilingualPane(Modifier.weight(3f).fillMaxHeight())
            }
            else -> Column(contentModifier.navigationBarsPadding()) {
                videoPane(Modifier.weight(1f).fillMaxWidth())
                Spacer(Modifier.fillMaxWidth().height(2.dp).background(palette.outline))
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


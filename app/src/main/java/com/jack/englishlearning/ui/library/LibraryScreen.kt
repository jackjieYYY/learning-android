package com.jack.englishlearning.ui.library

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.work.WorkInfo
import com.jack.englishlearning.data.cloud.CloudLesson
import com.jack.englishlearning.data.cloud.CloudLessonStatus
import com.jack.englishlearning.data.cloud.newestFirst
import com.jack.englishlearning.domain.model.LearningVideo
import com.jack.englishlearning.ui.pixel.*
import com.jack.englishlearning.ui.theme.Brand
import com.jack.englishlearning.ui.theme.PixelTheme
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(state: LibraryUiState, onRefresh: () -> Unit,
                  onDownload: (CloudLesson) -> Unit, onPause: (String) -> Unit, onVideo: (LearningVideo) -> Unit,
                  onSettings: () -> Unit = {}, header: @Composable () -> Unit = {}) {
    val context = LocalContext.current
    val palette = PixelTheme.palette
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val refreshState = rememberPullToRefreshState()
    fun download(lesson: CloudLesson) {
        onDownload(lesson)
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    Column(Modifier.fillMaxSize()) {
        PixelTopBar("英语听力", actions = { PixelIconButton(PixelGlyphs.Gear, "设置", onSettings) })
        PullToRefreshBox(isRefreshing = state.loading,
            onRefresh = { if (!state.loading) onRefresh() }, state = refreshState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            indicator = {
                PullToRefreshDefaults.Indicator(
                    state = refreshState, isRefreshing = state.loading,
                    modifier = Modifier.align(Alignment.TopCenter),
                    containerColor = palette.panel, color = palette.accent)
            }) {
        LazyColumn(Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
        ) {
            item(key = "app-update") { header() }
            state.error?.let { message ->
                item(key = "error") { Text(message, style = MaterialTheme.typography.labelMedium, color = palette.error) }
            }
            items(state.cloudLessons.newestFirst(), key = { "course:${it.lesson.id}" }) { status ->
                LessonCard(status, state.downloads[status.lesson.id], onVideo, ::download, onPause)
            }
            if (state.videos.isNotEmpty()) item(key = "local-title") {
                PixelSectionLabel("离线课程", Modifier.padding(top = 8.dp))
            }
            items(state.videos, key = { it.uri }) { video ->
                PixelPanel(Modifier.fillMaxWidth(), seed = video.uri.hashCode(), onClick = { onVideo(video) }) {
                    Text(video.title, style = MaterialTheme.typography.titleMedium, color = palette.text,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (video.transcriptUri == null) {
                        Spacer(Modifier.height(8.dp))
                        Text("没有找到中英文本，只能看视频", style = MaterialTheme.typography.labelMedium,
                            color = palette.textMuted)
                    }
                }
            }
            if (state.videos.isEmpty() && state.cloudLessons.isEmpty() && !state.loading) item(key = "empty") {
                Text("暂无课程，下拉刷新。",
                    style = MaterialTheme.typography.bodyMedium, color = palette.text)
            }
            item(key = "bottom-inset") { Spacer(Modifier.navigationBarsPadding()) }
        }
        }
    }
}

@Composable
private fun LessonCard(status: CloudLessonStatus, task: CourseDownload?, onVideo: (LearningVideo) -> Unit,
                       onDownload: (CloudLesson) -> Unit, onPause: (String) -> Unit) {
    val palette = PixelTheme.palette
    val busy = task?.state in setOf(WorkInfo.State.RUNNING, WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED)
    val video = status.video
    PixelPanel(Modifier.fillMaxWidth(), seed = status.lesson.id.hashCode(), onClick = video?.let { { onVideo(it) } }) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(status.lesson.title, style = MaterialTheme.typography.titleMedium, color = palette.text,
                maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (status.downloaded && !status.updateAvailable) PixelIcon(PixelGlyphs.Check, palette.green, size = 22.dp)
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (status.updateAvailable) PixelTag("有更新", palette.blue, Brand.Ink)
            Text(String.format(Locale.ROOT, "%.1f MB", status.lesson.videoBytes / 1e6),
                style = MaterialTheme.typography.labelMedium, color = palette.textMuted)
            if (status.updateAvailable) Text("旧版可继续学习", style = MaterialTheme.typography.labelMedium, color = palette.textMuted)
        }
        if (task != null && task.state != WorkInfo.State.SUCCEEDED) {
            Spacer(Modifier.height(16.dp))
            // One progress signal: the mining block once a percentage is known, a stepped bar while waiting.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val percent = task.percent
                if (percent != null) MiningBlock(percent / 100f, size = 36.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(task.message, style = MaterialTheme.typography.labelMedium,
                        color = if (task.state == WorkInfo.State.FAILED) palette.error else palette.text,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (busy && percent == null) PixelProgressBar(null)
                }
            }
        }
        if (busy || !status.downloaded || status.updateAvailable) {
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (busy) PixelButton("暂停", { onPause(status.lesson.id) }, kind = PixelButtonKind.Secondary,
                    icon = PixelGlyphs.Pause)
                else PixelButton(when {
                    task?.state == WorkInfo.State.CANCELLED -> "继续下载"
                    task?.state == WorkInfo.State.FAILED -> "重试"
                    status.updateAvailable -> "更新"
                    else -> "下载"
                }, { onDownload(status.lesson) }, icon = PixelGlyphs.Download)
                if (video != null) PixelButton("开始学习", { onVideo(video) }, kind = PixelButtonKind.Secondary,
                    icon = PixelGlyphs.Play)
            }
        }
    }
}

/** Small flat colour label. */
@Composable
private fun PixelTag(text: String, color: Color, textColor: Color) {
    val palette = PixelTheme.palette
    Box(Modifier.pixelBlock(BlockColors(color, color, color, palette.outline), textured = false, bevel = false)
        .padding(horizontal = 8.dp, vertical = 4.dp)) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = textColor)
    }
}

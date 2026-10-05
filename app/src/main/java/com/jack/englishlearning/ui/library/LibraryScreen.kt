package com.jack.englishlearning.ui.library

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.ui.unit.dp
import androidx.work.WorkInfo
import com.jack.englishlearning.data.cloud.CloudLesson
import com.jack.englishlearning.domain.model.LearningVideo
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(state: LibraryUiState, onSelectRoot: (Uri) -> Unit, onRefresh: () -> Unit,
                  onDownload: (CloudLesson) -> Unit, onPause: (String) -> Unit, onVideo: (LearningVideo) -> Unit) {
    val context = LocalContext.current
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) onSelectRoot(uri)
    }
    fun download(lesson: CloudLesson) {
        onDownload(lesson)
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    Scaffold(topBar = { TopAppBar(title = { Text("英语听力") }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onRefresh, enabled = !state.loading) { Text("刷新课程") }
                    OutlinedButton(onClick = { picker.launch(state.root?.let(Uri::parse)) }) { Text("导入本地目录") }
                }
            }
            if (state.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            item { Text("选择课程下载 · 下载后可离线学习", style = MaterialTheme.typography.labelLarge) }
            state.error?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
            items(state.cloudLessons.sortedByDescending { it.lesson.id }, key = { "course:${it.lesson.id}" }) { status ->
                val task = state.downloads[status.lesson.id]
                val busy = task?.state in setOf(WorkInfo.State.RUNNING, WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED)
                val video = status.video
                Card(onClick = { video?.let(onVideo) }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(status.lesson.title, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(6.dp))
                        val label = when {
                            status.updateAvailable -> "有更新 · 旧版可继续学习"
                            status.downloaded -> "已下载 · 点击学习"
                            else -> "未下载"
                        }
                        Text("$label · ${String.format(Locale.ROOT, "%.1f", status.lesson.videoBytes / 1e6)} MB", style = MaterialTheme.typography.bodySmall)
                        if (task != null && task.state != WorkInfo.State.SUCCEEDED) {
                            Text(task.message, style = MaterialTheme.typography.bodySmall,
                                color = if (task.state == WorkInfo.State.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (busy || !status.downloaded || status.updateAvailable) {
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (busy) OutlinedButton(onClick = { onPause(status.lesson.id) }) { Text("暂停") }
                                else Button(onClick = { download(status.lesson) }) {
                                    Text(when {
                                        task?.state == WorkInfo.State.CANCELLED -> "继续下载"
                                        task?.state == WorkInfo.State.FAILED -> "重试"
                                        status.updateAvailable -> "更新"
                                        else -> "下载"
                                    })
                                }
                                if (video != null) TextButton(onClick = { onVideo(video) }) { Text("开始学习") }
                            }
                        }
                    }
                }
            }
            if (state.videos.isNotEmpty()) item { Text("本地教材", style = MaterialTheme.typography.titleSmall) }
            items(state.videos, key = { it.uri }) { video ->
                Card(onClick = { onVideo(video) }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(video.title, style = MaterialTheme.typography.titleMedium)
                        Text(if (video.transcriptUri == null) "缺少双语文本 · 可播放视频" else "视频 + 中英对照 · 离线学习", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (state.videos.isEmpty() && state.cloudLessons.isEmpty() && !state.loading) item {
                Text("暂时没有课程。连接网络后点击“刷新课程”，也可以导入本地目录。")
            }
        }
    }
}

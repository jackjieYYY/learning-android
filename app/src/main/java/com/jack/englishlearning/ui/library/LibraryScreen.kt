package com.jack.englishlearning.ui.library

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jack.englishlearning.domain.model.LearningVideo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(state: LibraryUiState, onSelectRoot: (Uri) -> Unit, onRefresh: () -> Unit, onVideo: (LearningVideo) -> Unit) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) onSelectRoot(uri)
    }
    Scaffold(topBar = { TopAppBar(title = { Text("英语听力") }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { picker.launch(state.root?.let(Uri::parse)) }) {
                    Text(if (state.root == null) "选择学习目录" else "更换目录")
                }
                OutlinedButton(onClick = onRefresh, enabled = state.root != null && !state.loading) { Text("刷新") }
            }
            Text("本地视频 · 离线学习", style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(vertical = 12.dp))
            if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 12.dp)) }
            if (!state.loading && state.videos.isEmpty() && state.error == null) {
                Text("将视频和双语文本复制到 Download/EnglishLearning 下，每个视频一个文件夹，再选择 EnglishLearning 目录。\n\n文件示例：\nmy-video.mp4\nmy-video.bilingual.txt\n\n复制新视频后点击“刷新”。", modifier = Modifier.padding(vertical = 20.dp))
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                items(state.videos, key = { it.uri }) { video ->
                    Card(onClick = { onVideo(video) }, modifier = Modifier.fillMaxWidth()) {
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

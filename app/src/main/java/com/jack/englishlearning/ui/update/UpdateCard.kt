package com.jack.englishlearning.ui.update

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import java.util.Locale

@Composable
fun UpdateCard(state: UpdateUiState, onUpdate: () -> Unit) {
    val (info, message) = when (state) {
        UpdateUiState.None -> return
        is UpdateUiState.Available -> state.info to state.message
        is UpdateUiState.Downloading -> state.info to null
        is UpdateUiState.Ready -> state.info to state.message
        is UpdateUiState.Installing -> state.info to null
    }
    Card(Modifier.fillMaxWidth().testTag("update-card"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(16.dp)) {
            Text("新版本 ${info.versionName} 可用", style = MaterialTheme.typography.titleMedium)
            Text("${String.format(Locale.ROOT, "%.1f", info.size / 1e6)} MB", style = MaterialTheme.typography.bodySmall)
            if (info.notes.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(info.notes, style = MaterialTheme.typography.bodySmall, maxLines = 6)
            }
            message?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(8.dp))
            when (state) {
                is UpdateUiState.Downloading -> {
                    LinearProgressIndicator(progress = { state.percent / 100f }, modifier = Modifier.fillMaxWidth())
                    Text("正在下载 ${state.percent}%", style = MaterialTheme.typography.bodySmall)
                }
                is UpdateUiState.Installing -> Text("正在安装… 完成后 App 会关闭，请重新打开。", style = MaterialTheme.typography.bodySmall)
                is UpdateUiState.Ready -> Button(onClick = onUpdate) { Text("安装") }
                else -> Button(onClick = onUpdate) { Text(if (message == null) "下载并安装" else "重试") }
            }
        }
    }
}

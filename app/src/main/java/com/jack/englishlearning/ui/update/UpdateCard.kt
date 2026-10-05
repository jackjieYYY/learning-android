package com.jack.englishlearning.ui.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.jack.englishlearning.ui.pixel.PixelButton
import com.jack.englishlearning.ui.pixel.PixelGlyphs
import com.jack.englishlearning.ui.pixel.PixelIcon
import com.jack.englishlearning.ui.pixel.PixelPanel
import com.jack.englishlearning.ui.pixel.PixelProgressBar
import com.jack.englishlearning.ui.theme.PixelTheme
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
    val palette = PixelTheme.palette
    PixelPanel(Modifier.fillMaxWidth().testTag("update-card"), seed = 5) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PixelIcon(PixelGlyphs.Download, palette.accent, size = 20.dp)
            Text("新版本 ${info.versionName} 可用", style = MaterialTheme.typography.titleMedium, color = palette.text)
        }
        Text("${String.format(Locale.ROOT, "%.1f", info.size / 1e6)} MB", style = MaterialTheme.typography.labelMedium,
            color = palette.textMuted)
        if (info.notes.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(info.notes, style = MaterialTheme.typography.bodySmall, color = palette.text, maxLines = 6)
        }
        message?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = MaterialTheme.typography.labelMedium, color = palette.error)
        }
        Spacer(Modifier.height(16.dp))
        when (state) {
            is UpdateUiState.Downloading -> {
                PixelProgressBar(state.percent / 100f)
                Spacer(Modifier.height(8.dp))
                Text("正在下载 ${state.percent}%", style = MaterialTheme.typography.labelMedium, color = palette.text)
            }
            is UpdateUiState.Installing -> Text("正在安装… 完成后 App 会关闭，请重新打开。",
                style = MaterialTheme.typography.labelMedium, color = palette.text)
            is UpdateUiState.Ready -> PixelButton("安装", onUpdate)
            else -> PixelButton(if (message == null) "下载并安装" else "重试", onUpdate, icon = PixelGlyphs.Download)
        }
    }
}

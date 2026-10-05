package com.jack.englishlearning.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jack.englishlearning.BuildConfig
import com.jack.englishlearning.ui.pixel.PixelButton
import com.jack.englishlearning.ui.pixel.PixelButtonKind
import com.jack.englishlearning.ui.pixel.PixelDivider
import com.jack.englishlearning.ui.pixel.PixelGlyphs
import com.jack.englishlearning.ui.pixel.PixelIconButton
import com.jack.englishlearning.ui.pixel.PixelRadioRow
import com.jack.englishlearning.ui.pixel.PixelSectionLabel
import com.jack.englishlearning.ui.pixel.PixelTopBar
import com.jack.englishlearning.ui.theme.PixelTheme
import com.jack.englishlearning.ui.theme.ThemeMode
import com.jack.englishlearning.ui.update.UpdateCard
import com.jack.englishlearning.ui.update.UpdateUiState

@Composable
fun SettingsScreen(mode: ThemeMode, onMode: (ThemeMode) -> Unit, updateState: UpdateUiState, checkMessage: String?,
                   onCheckUpdate: () -> Unit, onUpdate: () -> Unit, onBack: () -> Unit) {
    val palette = PixelTheme.palette
    Column(Modifier.fillMaxSize()) {
        PixelTopBar("设置", navigation = { PixelIconButton(PixelGlyphs.Back, "返回", onBack) })
        // One flat surface; sections are separated by dividers rather than boxed.
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())
            .navigationBarsPadding().padding(horizontal = 16.dp, vertical = 24.dp)) {
            PixelSectionLabel("主题")
            Spacer(Modifier.height(8.dp))
            Column(Modifier.selectableGroup()) {
                ThemeMode.entries.forEach { option ->
                    PixelRadioRow(option.label, selected = option == mode, onSelect = { onMode(option) })
                }
            }
            PixelDivider(Modifier.padding(vertical = 16.dp))
            PixelSectionLabel("应用更新")
            Spacer(Modifier.height(8.dp))
            Text("当前版本 ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.labelMedium,
                color = palette.textMuted)
            Spacer(Modifier.height(16.dp))
            PixelButton("检查更新", onCheckUpdate, kind = PixelButtonKind.Secondary, icon = PixelGlyphs.Refresh,
                enabled = checkMessage != CHECKING)
            checkMessage?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.labelMedium, color = palette.text)
            }
            if (updateState != UpdateUiState.None) {
                Spacer(Modifier.height(16.dp))
                UpdateCard(updateState, onUpdate)
            }
            PixelDivider(Modifier.padding(vertical = 16.dp))
            Text("字体：Fusion Pixel、Lora、思源宋体（SIL Open Font License 1.1）",
                style = MaterialTheme.typography.labelSmall, color = palette.textMuted)
        }
    }
}

private const val CHECKING = "正在检查…"

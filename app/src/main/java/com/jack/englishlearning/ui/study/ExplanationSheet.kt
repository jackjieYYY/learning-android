package com.jack.englishlearning.ui.study

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jack.englishlearning.domain.model.TranscriptParagraph
import com.jack.englishlearning.ui.pixel.*
import com.jack.englishlearning.ui.theme.ReadingEnglish
import com.jack.englishlearning.ui.theme.PixelTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExplanationSheet(
    paragraph: TranscriptParagraph,
    pausedPlayback: Boolean,
    onDismiss: () -> Unit,
    onPlayParagraph: () -> Unit
) {
    val explanation = paragraph.explanation ?: return
    val palette = PixelTheme.palette
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(0.dp),
        containerColor = palette.background,
        contentColor = palette.text,
        dragHandle = {
            Box(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp), contentAlignment = Alignment.Center) {
                Spacer(Modifier.size(width = 40.dp, height = 8.dp)
                    .pixelBlock(palette.panelColors(), textured = false, bevel = false))
            }
        },
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.85f)) {
            PixelDivider(color = palette.outline)
            Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("老师讲解", style = MaterialTheme.typography.headlineSmall, color = palette.text,
                        modifier = Modifier.weight(1f))
                    PixelButton("关闭讲解", onDismiss, kind = PixelButtonKind.Secondary, icon = PixelGlyphs.Close)
                }
                if (pausedPlayback) Text("视频已暂停，可关闭后继续播放",
                    style = MaterialTheme.typography.labelMedium, color = palette.textMuted,
                    modifier = Modifier.padding(top = 8.dp))
                Spacer(Modifier.height(16.dp))
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    PixelPanel(Modifier.fillMaxWidth(), seed = 11) {
                        Text(paragraph.sentences.joinToString(" ") { it.text }, style = ReadingEnglish, color = palette.text)
                        Spacer(Modifier.height(8.dp))
                        Text(paragraph.translation, style = MaterialTheme.typography.bodyLarge, color = palette.textMuted)
                    }
                    ExplanationSection("这段怎么理解") {
                        Text(explanation.summary, style = MaterialTheme.typography.bodyLarge, color = palette.text)
                    }
                    if (explanation.expressions.isNotEmpty()) ExplanationSection("值得学的表达") {
                        explanation.expressions.forEach { NoteItem(it.text, it.explanation) }
                    }
                    if (explanation.sentenceNotes.isNotEmpty()) ExplanationSection("句子拆解") {
                        explanation.sentenceNotes.forEach { NoteItem(it.quote, it.explanation) }
                    }
                    if (explanation.pitfalls.isNotEmpty()) ExplanationSection("容易理解错的地方") {
                        explanation.pitfalls.forEachIndexed { index, text ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("${index + 1}.", style = MaterialTheme.typography.bodyLarge, color = palette.textMuted)
                                Text(text, style = MaterialTheme.typography.bodyLarge, color = palette.text,
                                    modifier = Modifier.weight(1f))
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                PixelButton("从本段播放", onPlayParagraph, icon = PixelGlyphs.Play,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp).navigationBarsPadding())
            }
        }
    }
}

/** Heading plus its items: tight inside the section, the parent column spaces sections apart. */
@Composable
private fun ExplanationSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PixelSectionLabel(title, style = MaterialTheme.typography.titleLarge)
        Column(verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
    }
}

/** An English phrase in bold reading serif, with its Chinese explanation directly underneath. */
@Composable
private fun NoteItem(english: String, explanation: String) {
    val palette = PixelTheme.palette
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(english, style = ReadingEnglish.copy(fontWeight = FontWeight.Bold), color = palette.text)
        Text(explanation, style = MaterialTheme.typography.bodyLarge, color = palette.text)
    }
}

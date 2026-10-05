package com.jack.englishlearning.ui.study

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jack.englishlearning.domain.model.TranscriptParagraph

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExplanationSheet(
    paragraph: TranscriptParagraph,
    pausedPlayback: Boolean,
    onDismiss: () -> Unit,
    onPlayParagraph: () -> Unit
) {
    val explanation = paragraph.explanation ?: return
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.85f).padding(horizontal = 20.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("老师讲解", style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f).padding(top = 12.dp))
                TextButton(onClick = onDismiss) { Text("关闭讲解") }
            }
            if (pausedPlayback) Text("视频已暂停，可关闭后继续播放",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(bottom = 8.dp))
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(paragraph.sentences.joinToString(" ") { it.text },
                    style = MaterialTheme.typography.bodyLarge)
                Text(paragraph.translation, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                HorizontalDivider()
                ExplanationHeading("这段怎么理解")
                Text(explanation.summary, style = MaterialTheme.typography.bodyLarge)
                if (explanation.expressions.isNotEmpty()) {
                    ExplanationHeading("值得学的表达")
                    explanation.expressions.forEach { item ->
                        Text(item.text, style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary)
                        Text(item.explanation, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                if (explanation.sentenceNotes.isNotEmpty()) {
                    ExplanationHeading("句子拆解")
                    explanation.sentenceNotes.forEach { item ->
                        Text(item.quote, style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary)
                        Text(item.explanation, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                if (explanation.pitfalls.isNotEmpty()) {
                    ExplanationHeading("容易理解错的地方")
                    explanation.pitfalls.forEach { Text("• $it", style = MaterialTheme.typography.bodyLarge) }
                }
                Spacer(Modifier.height(8.dp))
            }
            Button(onClick = onPlayParagraph,
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) { Text("从本段播放") }
        }
    }
}

@Composable
private fun ExplanationHeading(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
}

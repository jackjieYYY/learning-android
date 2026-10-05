package com.jack.englishlearning.ui.study

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import com.jack.englishlearning.domain.model.TranscriptSentence
import com.jack.englishlearning.ui.theme.PixelTheme
import com.jack.englishlearning.ui.theme.ReadingEnglish

/** Ranges are constructed with the text, never inferred from punctuation or repeated words. */
internal class EnglishParagraphText(val sentences: List<TranscriptSentence>) {
    val ranges: List<IntRange>
    val text: String
    init {
        val builder = StringBuilder()
        ranges = sentences.map { sentence ->
            if (builder.isNotEmpty()) builder.append(' ')
            val start = builder.length
            builder.append(sentence.text)
            start until builder.length
        }
        text = builder.toString()
    }
    fun sentenceAt(offset: Int): TranscriptSentence? =
        ranges.indexOfFirst { offset in it }.takeIf { it >= 0 }?.let(sentences::get)
}

@Composable
internal fun EnglishParagraph(
    sentences: List<TranscriptSentence>,
    modifier: Modifier = Modifier,
    onSentence: (Long) -> Unit,
    onDoubleTap: () -> Unit,
    onExplanation: (() -> Unit)? = null
) {
    val paragraph = remember(sentences) { EnglishParagraphText(sentences) }
    var layout by remember(paragraph) { mutableStateOf<TextLayoutResult?>(null) }
    val seek by rememberUpdatedState(onSentence)
    val toggle by rememberUpdatedState(onDoubleTap)
    val explain by rememberUpdatedState(onExplanation)
    fun sentenceAt(position: androidx.compose.ui.geometry.Offset): TranscriptSentence? {
        val result = layout ?: return null
        val offset = result.getOffsetForPosition(position)
        // Ignore whitespace outside the rendered text; the layout otherwise snaps to a character.
        return if (offset in paragraph.text.indices && result.getBoundingBox(offset).contains(position))
            paragraph.sentenceAt(offset) else null
    }
    Text(paragraph.text, style = ReadingEnglish,
        color = PixelTheme.palette.text,
        onTextLayout = { layout = it },
        modifier = modifier
            .semantics {
                customActions = sentences.map { sentence ->
                    CustomAccessibilityAction("播放：${sentence.text}") { seek(sentence.startMs); true }
                } + if (onExplanation != null) listOf(
                    CustomAccessibilityAction("查看老师讲解") { explain?.invoke(); true }
                ) else emptyList()
            }
            .pointerInput(paragraph, onExplanation != null) {
                detectTapGestures(
                    onDoubleTap = { toggle() },
                    onLongPress = if (onExplanation != null) ({ position ->
                        if (sentenceAt(position) != null) explain?.invoke()
                    }) else null,
                    onTap = { position -> sentenceAt(position)?.let { seek(it.startMs) } }
                )
            })
}

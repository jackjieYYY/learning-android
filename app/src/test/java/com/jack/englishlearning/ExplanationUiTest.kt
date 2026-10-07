package com.jack.englishlearning

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.jack.englishlearning.domain.model.*
import com.jack.englishlearning.ui.study.EnglishParagraph
import com.jack.englishlearning.ui.study.ExplanationSheet
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.semantics.SemanticsActions
import org.robolectric.annotation.GraphicsMode
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w411dp-h891dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ExplanationUiTest {
    @get:Rule val compose = createComposeRule()
    private val sentences = listOf(TranscriptSentence(1250, "Hello."))
    private fun characterBox(index: Int = 0): androidx.compose.ui.geometry.Rect {
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText("Hello.").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        return layouts.single().getBoundingBox(index)
    }
    private fun characterCenter(): Offset = characterBox().center

    @Test fun tapAnywhereInsideGlyphSeeks() {
        val starts = mutableListOf<Long>()
        compose.setContent { MaterialTheme {
            EnglishParagraph(sentences, Modifier.width(260.dp),
                onSentence = { starts += it }, onDoubleTap = {})
        } }
        // Right halves resolve to the next caret offset; they must still hit the glyph underneath.
        val targets = (0 until "Hello.".length).flatMap { index ->
            val box = characterBox(index)
            listOf(Offset(box.left + 1f, box.center.y), Offset(box.right - 1f, box.center.y))
        }
        targets.forEach { target ->
            compose.onNodeWithText("Hello.").performTouchInput { click(target) }
            compose.mainClock.advanceTimeBy(500)
        }
        compose.runOnIdle { assertEquals(List(targets.size) { 1250L }, starts) }
    }

    @Test fun longPressNoLongerOpensExplanation() {
        var explained = 0
        var seeks = 0
        var toggles = 0
        compose.setContent { MaterialTheme {
            EnglishParagraph(sentences, Modifier.width(260.dp),
                onSentence = { seeks++ }, onDoubleTap = { toggles++ }, onExplanation = { explained++ })
        } }
        val target = characterCenter()
        compose.onNodeWithText("Hello.").performTouchInput { longClick(target) }
        compose.runOnIdle {
            assertEquals(0, explained)
            assertEquals(0, seeks)
            assertEquals(0, toggles)
        }
    }

    @Test fun singleAndDoubleTapStillControlPlayback() {
        var start: Long? = null
        var toggles = 0
        var explained = 0
        compose.setContent { MaterialTheme {
            EnglishParagraph(sentences, Modifier.width(260.dp),
                onSentence = { start = it }, onDoubleTap = { toggles++ }, onExplanation = { explained++ })
        } }
        val target = characterCenter()
        compose.onNodeWithText("Hello.").performTouchInput { click(target) }
        compose.mainClock.advanceTimeBy(500)
        compose.runOnIdle { assertEquals(1250L, start) }
        compose.onNodeWithText("Hello.").performTouchInput { doubleClick(target) }
        compose.runOnIdle {
            assertEquals(1, toggles)
            assertEquals(0, explained)
        }
    }

    @Test fun whitespaceAndVerticalScrollDoNotOpenExplanation() {
        var explained = 0
        compose.setContent { MaterialTheme {
            Column(Modifier.width(260.dp).height(300.dp).verticalScroll(rememberScrollState())) {
                EnglishParagraph(sentences, Modifier.fillMaxWidth(),
                    onSentence = {}, onDoubleTap = {}, onExplanation = { explained++ })
                repeat(30) { Text("Other paragraph $it") }
            }
        } }
        compose.onNodeWithText("Hello.").performTouchInput { longClick(Offset(width - 10f, 10f)) }
        compose.onNodeWithText("Hello.").performTouchInput {
            down(Offset(10f, 15f))
            moveBy(Offset(0f, -150f), delayMillis = 100)
            up()
        }
        compose.runOnIdle { assertEquals(0, explained) }
    }

    @Test fun pronunciationOnlySheetReplaysVerifiedSentenceStart() {
        var replayed: Long? = null
        val paragraph = TranscriptParagraph(sentences, "你好。", pronunciationNotes = listOf(
            PronunciationNote(0, "Hello.", "开头轻，末尾重。", "容易漏掉开头。", "听末尾音节。", "")))
        compose.setContent { MaterialTheme {
            ExplanationSheet(paragraph, false, onDismiss = {}, onPlayParagraph = {},
                onPlaySentence = { replayed = it })
        } }
        compose.mainClock.advanceTimeBy(1000)
        compose.onNodeWithText("发音与连读").assertExists()
        compose.onNodeWithText("这段怎么理解").assertDoesNotExist()
        compose.onNodeWithText("回听这句").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1250L, replayed) }
    }

    @Test fun sheetHidesEmptySectionsAndOffersCloseAndReplay() {
        var closed = 0
        var replayed = 0
        val paragraph = TranscriptParagraph(sentences, "你好。",
            ParagraphExplanation("简单问候。", emptyList(), emptyList(), emptyList()))
        compose.setContent { MaterialTheme {
            ExplanationSheet(paragraph, true, onDismiss = { closed++ }, onPlayParagraph = { replayed++ })
        } }
        compose.mainClock.advanceTimeBy(1000)
        compose.onNodeWithText("这段怎么理解").assertExists()
        compose.onNodeWithText("简单问候。").assertExists()
        compose.onNodeWithText("值得学的表达").assertDoesNotExist()
        compose.onNodeWithText("句子拆解").assertDoesNotExist()
        compose.onNodeWithText("容易理解错的地方").assertDoesNotExist()
        compose.onNodeWithText("视频已暂停，可关闭后继续播放").assertExists()
        compose.onNodeWithText("从本段播放").assertIsDisplayed().performClick()
        compose.onNodeWithText("关闭讲解").assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertEquals(1, closed)
            assertEquals(1, replayed)
        }
    }
}

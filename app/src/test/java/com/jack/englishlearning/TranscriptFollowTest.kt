package com.jack.englishlearning

import com.jack.englishlearning.domain.model.TranscriptParagraph
import com.jack.englishlearning.domain.model.TranscriptSentence
import com.jack.englishlearning.ui.study.paragraphAt
import com.jack.englishlearning.ui.study.transcriptCues
import org.junit.Assert.*
import org.junit.Test

class TranscriptFollowTest {
    private val paragraphs = listOf(
        TranscriptParagraph(listOf(TranscriptSentence(1000, "One."), TranscriptSentence(2000, "Two.")), "一。"),
        TranscriptParagraph(listOf(TranscriptSentence(5000, "Three.")), "二。"),
        TranscriptParagraph(listOf(TranscriptSentence(5000, "Four."), TranscriptSentence(8000, "Five.")), "三。")
    )
    @Test fun restoreSeekAndGapsUseTheLastStartedSentence() {
        val cues = transcriptCues(paragraphs)
        assertEquals(0, paragraphAt(cues, 0))
        assertEquals(0, paragraphAt(cues, 4999))
        assertEquals(2, paragraphAt(cues, 5000))
        assertEquals(2, paragraphAt(cues, 999999))
        assertEquals(0, paragraphAt(cues, 1500)) // seek backwards
        assertNull(paragraphAt(emptyList(), 0))
    }
}

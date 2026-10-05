package com.jack.englishlearning

import com.jack.englishlearning.domain.model.TranscriptSentence
import com.jack.englishlearning.ui.study.EnglishParagraphText
import org.junit.Assert.*
import org.junit.Test

class EnglishParagraphTextTest {
    @Test fun repeatedSentencesKeepTheirOwnTimesAndSeparatorDoesNotSeek() {
        val paragraph = EnglishParagraphText(listOf(
            TranscriptSentence(1000, "Hello."), TranscriptSentence(2000, "Hello.")))
        assertEquals("Hello. Hello.", paragraph.text)
        assertEquals(1000L, paragraph.sentenceAt(5)?.startMs)
        assertNull(paragraph.sentenceAt(6))
        assertEquals(2000L, paragraph.sentenceAt(7)?.startMs)
        assertEquals(2000L, paragraph.sentenceAt(12)?.startMs)
        assertNull(paragraph.sentenceAt(13))
        assertNull(paragraph.sentenceAt(-1))
    }
    @Test fun punctuationAndUnicodeDoNotRequireAutomaticSplitting() {
        val first = "Dr. Lee said “Hi 👋.”"
        val paragraph = EnglishParagraphText(listOf(
            TranscriptSentence(0, first), TranscriptSentence(9000, "Next sentence.")))
        assertEquals(0L, paragraph.sentenceAt(first.length - 1)?.startMs)
        assertEquals(9000L, paragraph.sentenceAt(first.length + 1)?.startMs)
    }
}

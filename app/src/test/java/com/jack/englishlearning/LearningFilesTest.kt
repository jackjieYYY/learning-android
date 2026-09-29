package com.jack.englishlearning

import org.junit.Assert.*
import org.junit.Test

class LearningFilesTest {
    @Test fun recognizesOnlyMp4() {
        assertTrue(LearningFiles.isVideo("lesson.MP4"))
        assertFalse(LearningFiles.isVideo("lesson.mp4.part"))
        assertFalse(LearningFiles.isVideo("lesson.mp3"))
    }
    @Test fun matchesSidecarAndReadableTitle() {
        assertEquals("my-video.bilingual.txt", LearningFiles.transcriptName("my-video.mp4"))
        assertEquals("my video one", LearningFiles.title("my-video_one.mp4"))
    }
    @Test fun keepsBilingualOrderAndNormalizesLineEndings() {
        assertEquals(listOf("中文", "English", "【标题】\n[Heading]"),
            LearningFiles.paragraphs("\uFEFF中文\r\n\r\nEnglish\r\n  \r\n【标题】\r\n[Heading]\r\n"))
    }
    @Test fun emptyTextDoesNotCreateRows() {
        assertTrue(LearningFiles.paragraphs(" \r\n\n ").isEmpty())
    }
}

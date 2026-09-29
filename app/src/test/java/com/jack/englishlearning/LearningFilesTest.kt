package com.jack.englishlearning

import com.jack.englishlearning.data.local.LearningFiles
import org.junit.Assert.assertEquals
import org.junit.Test

class LearningFilesTest {
    @Test fun bilingualParagraphsPreserveOrderAcrossLineEndings() {
        assertEquals(listOf("中文", "English", "【标题】\n[Heading]"),
            LearningFiles.paragraphs("\uFEFF中文\r\n\r\nEnglish\r\n  \r\n【标题】\r\n[Heading]\r\n"))
    }
}

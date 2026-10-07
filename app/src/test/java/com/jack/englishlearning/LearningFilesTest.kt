package com.jack.englishlearning

import com.jack.englishlearning.data.local.LearningFiles
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class LearningFilesTest {
    private val valid = """{"paragraphs":[{"sentences":[{"startMs":1250,"text":"Hello."},{"startMs":3000,"text":"Welcome."}],"translation":"你好，欢迎。"}]}"""

    @Test fun parsesParagraphsWithBomAndLineEndings() {
        val paragraphs = LearningFiles.paragraphs("\uFEFF\r\n$valid\r\n")
        assertEquals(1, paragraphs.size)
        assertEquals(listOf("Hello.", "Welcome."), paragraphs[0].sentences.map { it.text })
        assertEquals(listOf(1250L, 3000L), paragraphs[0].sentences.map { it.startMs })
        assertEquals("你好，欢迎。", paragraphs[0].translation)
    }
    @Test fun emptyDocumentHasNoParagraphs() {
        assertTrue(LearningFiles.paragraphs("\uFEFF \r\n").isEmpty())
        assertTrue(LearningFiles.paragraphs("""{"paragraphs":[]}""").isEmpty())
    }
    @Test fun rejectsInvalidStructureOrValues() {
        listOf("Old plain text", "{}", "[]", valid.replace("1250", "-1"),
            valid.replace("1250", "1.25"), valid.replace("1250", "\"1250\""),
            valid.replace("3000", "1000"), valid.replace("Hello.", " "),
            valid.replace("你好，欢迎。", ""), valid.replace("\"translation\"", "\"missing\""),
            """{"paragraphs":[{"sentences":[],"translation":"中文"}]}"""
        ).forEach { text -> assertThrows(IllegalArgumentException::class.java) { LearningFiles.paragraphs(text) } }
    }
    private val note = """{"summary":"问候后欢迎观众。","expressions":[{"text":"Hello.","explanation":"用于打招呼。"}],"sentenceNotes":[{"quote":"Hello. Welcome.","explanation":"两句简短的开场白。"}],"pitfalls":["Welcome 在这里是欢迎语。"]}"""

    private fun withNote(value: Any): String = JSONObject(valid).apply {
        getJSONArray("paragraphs").getJSONObject(0).put("explanation", value)
    }.toString()

    @Test fun parsesOptionalExplanationAndCrossSentenceQuote() {
        val paragraph = LearningFiles.paragraphs(withNote(JSONObject(note))).single()
        val explanation = paragraph.explanation!!
        assertEquals("问候后欢迎观众。", explanation.summary)
        assertEquals("Hello.", explanation.expressions.single().text)
        assertEquals("Hello. Welcome.", explanation.sentenceNotes.single().quote)
        assertEquals(1, explanation.pitfalls.size)
        assertEquals(listOf(1250L, 3000L), paragraph.sentences.map { it.startMs })
    }

    @Test fun missingNullAndEmptyArraysAreSupported() {
        assertNull(LearningFiles.paragraphs(valid).single().explanation)
        assertNull(LearningFiles.paragraphs(withNote(JSONObject.NULL)).single().explanation)
        val empty = JSONObject("""{"summary":"简单问候。","expressions":[],"sentenceNotes":[],"pitfalls":[]}""")
        assertTrue(LearningFiles.paragraphs(withNote(empty)).single().explanation!!.expressions.isEmpty())
    }

    @Test fun invalidExplanationNeverBreaksValidTranscript() {
        val base = LearningFiles.paragraphs(valid).single()
        val malformed = listOf<Any>("plain text", 42, JSONObject(),
            JSONObject(note.replace("问候后欢迎观众。", " ")),
            JSONObject(note.replace("Hello.", "Missing.")),
            JSONObject(note).put("pitfalls", org.json.JSONArray().put(1)),
            JSONObject(note).put("sentenceNotes", "wrong type"),
            JSONObject(note).put("extra", true),
            JSONObject(note).put("expressions", org.json.JSONArray().apply { repeat(4) {
                put(JSONObject("""{"text":"Hello.","explanation":"问候"}"""))
            } }))
        malformed.forEach { value ->
            assertEquals(base, LearningFiles.paragraphs(withNote(value)).single())
        }
    }

    @Test fun explanationCannotRelaxSubtitleValidation() {
        assertThrows(IllegalArgumentException::class.java) {
            LearningFiles.paragraphs(withNote(JSONObject(note)).replace("1250", "-1"))
        }
    }

    private val pronunciation = """[{"sentenceIndex":1,"quote":"Welcome.","actual":"开头音节较突出。","difficulty":"注意节奏。","listenFor":"听清开头。","generalRule":""}]"""

    private fun withPronunciation(value: Any): String = JSONObject(valid).apply {
        getJSONArray("paragraphs").getJSONObject(0).put("pronunciationNotes", value)
    }.toString()

    @Test fun pronunciationWorksWithoutExistingExplanationAndLinksToSentence() {
        val paragraph = LearningFiles.paragraphs(withPronunciation(org.json.JSONArray(pronunciation))).single()
        assertTrue(paragraph.hasTeachingNotes)
        assertNull(paragraph.explanation)
        val note = paragraph.pronunciationNotes.single()
        assertEquals(3000L, paragraph.sentences[note.sentenceIndex].startMs)
        assertEquals("Welcome.", note.quote)
        assertEquals("", note.generalRule)
    }

    @Test fun invalidPronunciationIsIgnoredWithoutLosingTranscriptOrExplanation() {
        val malformed = listOf<Any>("text", JSONObject.NULL,
            org.json.JSONArray(pronunciation.replace("\"sentenceIndex\":1", "\"sentenceIndex\":2")),
            org.json.JSONArray(pronunciation.replace("\"sentenceIndex\":1", "\"sentenceIndex\":true")),
            org.json.JSONArray(pronunciation.replace("Welcome.", "Hello.")),
            org.json.JSONArray(pronunciation.replace("听清开头。", " ")))
        malformed.forEach {
            val document = JSONObject(withNote(JSONObject(note)))
            document.getJSONArray("paragraphs").getJSONObject(0).put("pronunciationNotes", it)
            val paragraph = LearningFiles.paragraphs(document.toString()).single()
            assertTrue(paragraph.pronunciationNotes.isEmpty())
            assertNotNull(paragraph.explanation)
            assertEquals(2, paragraph.sentences.size)
        }
    }

    @Test fun rejectsTimeGoingBackAcrossParagraphs() {
        val paragraph = """{"sentences":[{"startMs":%d,"text":"Hi."}],"translation":"你好。"}"""
        assertThrows(IllegalArgumentException::class.java) {
            LearningFiles.paragraphs("""{"paragraphs":[${paragraph.format(2000)},${paragraph.format(1000)}]}""")
        }
    }
}

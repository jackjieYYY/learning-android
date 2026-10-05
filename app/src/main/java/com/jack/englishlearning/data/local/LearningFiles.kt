package com.jack.englishlearning.data.local

import com.jack.englishlearning.domain.model.ExplainedExpression
import com.jack.englishlearning.domain.model.ExplainedSentence
import com.jack.englishlearning.domain.model.ParagraphExplanation
import com.jack.englishlearning.domain.model.TranscriptParagraph
import com.jack.englishlearning.domain.model.TranscriptSentence
import org.json.JSONObject

object LearningFiles {
    fun isVideo(name: String) = name.endsWith(".mp4", ignoreCase = true)
    fun transcriptName(videoName: String) = videoName.dropLast(4) + ".bilingual.json"
    fun title(videoName: String) = videoName.dropLast(4).replace('-', ' ').replace('_', ' ')

    fun paragraphs(text: String): List<TranscriptParagraph> {
        val normalized = text.removePrefix("\uFEFF").trim()
        if (normalized.isEmpty()) return emptyList()
        try {
            val paragraphs = JSONObject(normalized).getJSONArray("paragraphs")
            var previousStart = -1L
            return List(paragraphs.length()) { paragraphIndex ->
                val paragraph = paragraphs.getJSONObject(paragraphIndex)
                val message = "双语 JSON 第 ${paragraphIndex + 1} 段格式错误：需包含非空 sentences 和 translation，句子时间须为非负整数且按顺序排列。"
                val translation = paragraph.get("translation")
                require(translation is String && translation.isNotBlank()) { message }
                val sentences = paragraph.getJSONArray("sentences")
                require(sentences.length() > 0) { message }
                val parsedSentences = List(sentences.length()) { sentenceIndex ->
                    val sentence = sentences.getJSONObject(sentenceIndex)
                    val start = sentence.get("startMs")
                    val body = sentence.get("text")
                    require((start is Int || start is Long) && body is String && body.isNotBlank()) { message }
                    val startMs = (start as Number).toLong()
                    require(startMs >= 0 && startMs >= previousStart) { message }
                    previousStart = startMs
                    TranscriptSentence(startMs, (body as String).trim())
                }
                TranscriptParagraph(parsedSentences, translation.trim(),
                    optionalExplanation(paragraph.opt("explanation"), parsedSentences.joinToString(" ") { it.text }))
            }
        } catch (error: org.json.JSONException) {
            throw IllegalArgumentException("双语 JSON 格式错误，请检查 paragraphs、sentences、startMs、text 和 translation。", error)
        }
    }

    // Optional teaching content must never prevent a valid transcript from playing.
    private fun optionalExplanation(value: Any?, english: String): ParagraphExplanation? {
        if (value == null || value == JSONObject.NULL) return null
        return try {
            require(value is JSONObject)
            keys(value, setOf("summary", "expressions", "sentenceNotes", "pitfalls"))
            val summary = nonBlank(value.get("summary"))
            val expressions = value.getJSONArray("expressions")
            val notes = value.getJSONArray("sentenceNotes")
            val pitfalls = value.getJSONArray("pitfalls")
            require(expressions.length() <= 3 && notes.length() <= 2 && pitfalls.length() <= 2)
            ParagraphExplanation(summary,
                List(expressions.length()) { index ->
                    val item = expressions.getJSONObject(index)
                    keys(item, setOf("text", "explanation"))
                    val text = nonBlank(item.get("text"))
                    require(english.contains(text))
                    ExplainedExpression(text, nonBlank(item.get("explanation")))
                },
                List(notes.length()) { index ->
                    val item = notes.getJSONObject(index)
                    keys(item, setOf("quote", "explanation"))
                    val quote = nonBlank(item.get("quote"))
                    require(english.contains(quote))
                    ExplainedSentence(quote, nonBlank(item.get("explanation")))
                },
                List(pitfalls.length()) { nonBlank(pitfalls.get(it)) })
        } catch (_: org.json.JSONException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun nonBlank(value: Any): String {
        require(value is String && value.isNotBlank())
        return value.trim()
    }

    private fun keys(value: JSONObject, expected: Set<String>) {
        require(value.keys().asSequence().toSet() == expected)
    }
}

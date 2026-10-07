package com.jack.englishlearning.domain.model

data class TranscriptSentence(val startMs: Long, val text: String)

data class TranscriptParagraph(
    val sentences: List<TranscriptSentence>,
    val translation: String,
    val explanation: ParagraphExplanation? = null,
    val pronunciationNotes: List<PronunciationNote> = emptyList()
) {
    val hasTeachingNotes: Boolean get() = explanation != null || pronunciationNotes.isNotEmpty()
}

/** sentenceIndex links replay to an existing, verified sentence start. */
data class PronunciationNote(
    val sentenceIndex: Int,
    val quote: String,
    val actual: String,
    val difficulty: String,
    val listenFor: String,
    val generalRule: String
)

data class ExplainedExpression(val text: String, val explanation: String)
data class ExplainedSentence(val quote: String, val explanation: String)

data class ParagraphExplanation(
    val summary: String,
    val expressions: List<ExplainedExpression>,
    val sentenceNotes: List<ExplainedSentence>,
    val pitfalls: List<String>
)

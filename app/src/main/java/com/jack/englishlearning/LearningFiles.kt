package com.jack.englishlearning

/** Text is deliberately not aligned to playback; preserve the author's paragraph order. */
object LearningFiles {
    fun isVideo(name: String) = name.endsWith(".mp4", ignoreCase = true)
    fun transcriptName(videoName: String) = videoName.dropLast(4) + ".bilingual.txt"
    fun title(videoName: String) = videoName.dropLast(4).replace('-', ' ').replace('_', ' ')
    fun paragraphs(text: String): List<String> = text.removePrefix("\uFEFF")
        .replace("\r\n", "\n").replace('\r', '\n')
        .split(Regex("\n[\\t ]*\n+"))
        .map(String::trim).filter(String::isNotEmpty)
}

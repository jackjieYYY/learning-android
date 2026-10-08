package com.jack.englishlearning.data.cloud

import org.json.JSONObject
import java.net.URI

const val LIBRARY_URL = "https://english-learning-library.dltest.workers.dev/"
const val MAX_CHUNK_BYTES = 20L * 1024 * 1024
const val MAX_JSON_BYTES = 4L * 1024 * 1024
private val hashPattern = Regex("[a-f0-9]{64}")
private val idPattern = Regex("[a-z0-9]+(?:-[a-z0-9]+)*")

data class CloudAsset(val path: String, val size: Long, val sha256: String)
data class CloudLesson(val id: String, val title: String, val version: String, val videoBytes: Long, val manifest: CloudAsset, val timelineVersion: String = "1", val timestamp: Long = 0)
data class CloudManifest(val id: String, val title: String, val videoSize: Long, val videoHash: String, val chunks: List<CloudAsset>, val transcript: CloudAsset, val timelineVersion: String = "1")
data class CloudLessonStatus(val lesson: CloudLesson, val downloaded: Boolean, val updateAvailable: Boolean, val video: com.jack.englishlearning.domain.model.LearningVideo? = null)

fun List<CloudLessonStatus>.newestFirst(): List<CloudLessonStatus> =
    sortedWith(compareByDescending<CloudLessonStatus> { it.lesson.timestamp }.thenByDescending { it.lesson.id })

object CloudFormat {
    fun baseUrl(value: String): String {
        val uri = URI(value.trim())
        require(uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null) {
            "请输入完整的 HTTPS 教材库地址，例如 https://example.workers.dev/"
        }
        require(uri.port == -1 || uri.port == 443) { "教材库地址必须使用 HTTPS 默认端口。" }
        val path = uri.rawPath.orEmpty()
        require(!path.contains('%') && path.split('/').none { it == ".." || it == "." }) { "教材库地址路径无效。" }
        return value.trim().trimEnd('/') + "/"
    }

    fun path(value: String): String {
        require(value.length in 1..1024 && Regex("[a-zA-Z0-9_./-]+").matches(value) && !value.startsWith('/') && value.split('/').all { it.isNotEmpty() && it != "." && it != ".." }) {
            "教材文件路径无效。"
        }
        return value
    }

    private fun hash(value: String): String = value.also { require(hashPattern.matches(it)) { "教材校验码无效。" } }
    private fun id(value: String): String = value.also { require(it.length <= 160 && idPattern.matches(it)) { "教材 ID 无效。" } }
    private fun size(json: JSONObject, key: String, maximum: Long): Long {
        val value = json.get(key)
        require(value is Int || value is Long) { "教材文件大小无效。" }
        return (value as Number).toLong().also { require(it in 1..maximum) { "教材文件大小超过限制。" } }
    }
    private fun timeline(json: JSONObject) = json.optString("timelineVersion", "1").also {
        require(Regex("[a-zA-Z0-9_-]{1,64}").matches(it)) { "教材时间轴版本无效。" }
    }
    private fun timestamp(json: JSONObject): Long {
        if (!json.has("timestamp")) return 0 // Cached catalogs from older releases.
        val value = json.get("timestamp")
        require(value is Int || value is Long) { "教材发布日期必须为 Unix 秒时间戳。" }
        return (value as Number).toLong().also { require(it in 0..253402300799L) { "教材发布日期无效。" } }
    }
    private fun title(json: JSONObject) = json.getString("title").also { require(it.isNotBlank() && it.length <= 500) }
    private fun asset(json: JSONObject, maximum: Long) = CloudAsset(path(json.getString("path")), size(json, "size", maximum), hash(json.getString("sha256")))
    private fun root(text: String) = JSONObject(text).also { require(it.getInt("schemaVersion") == 1) { "教材库版本不受支持，请更新 App。" } }

    fun catalog(text: String): List<CloudLesson> {
        val array = root(text).getJSONArray("lessons")
        require(array.length() <= 5000) { "教材数量超过限制。" }
        val lessons = List(array.length()) { index ->
            val json = array.getJSONObject(index)
            val manifest = asset(json.getJSONObject("manifest"), MAX_JSON_BYTES)
            val version = hash(json.getString("version"))
            require(version == manifest.sha256) { "教材版本与清单不一致。" }
            CloudLesson(id(json.getString("id")), title(json), version, size(json, "videoBytes", 100L * 1024 * 1024 * 1024), manifest, timeline(json), timestamp(json))
        }
        require(lessons.map { it.id }.distinct().size == lessons.size) { "教材 ID 重复。" }
        return lessons
    }

    fun encodeLesson(lesson: CloudLesson): String = JSONObject()
        .put("schemaVersion", 1).put("lessons", org.json.JSONArray().put(JSONObject()
            .put("id", lesson.id).put("title", lesson.title).put("version", lesson.version)
            .put("videoBytes", lesson.videoBytes).put("timelineVersion", lesson.timelineVersion).put("timestamp", lesson.timestamp)
            .put("manifest", JSONObject().put("path", lesson.manifest.path).put("size", lesson.manifest.size)
                .put("sha256", lesson.manifest.sha256)))).toString()

    fun manifest(text: String, lesson: CloudLesson): CloudManifest {
        val json = root(text)
        require(id(json.getString("id")) == lesson.id && title(json) == lesson.title) { "教材清单不匹配。" }
        require(timeline(json) == lesson.timelineVersion) { "教材时间轴版本不匹配。" }
        val video = json.getJSONObject("video")
        val videoSize = size(video, "size", 100L * 1024 * 1024 * 1024)
        require(videoSize == lesson.videoBytes)
        val array = video.getJSONArray("chunks")
        require(array.length() in 1..20000)
        val chunks = List(array.length()) { asset(array.getJSONObject(it), MAX_CHUNK_BYTES) }
        require(chunks.sumOf { it.size } == videoSize) { "视频分块总大小不匹配。" }
        require(chunks.map { it.path }.distinct().size == chunks.size) { "视频分块路径重复。" }
        return CloudManifest(lesson.id, lesson.title, videoSize, hash(video.getString("sha256")), chunks, asset(json.getJSONObject("transcript"), MAX_JSON_BYTES), lesson.timelineVersion)
    }
}

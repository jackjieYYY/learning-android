package com.jack.englishlearning.data.cloud

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import com.jack.englishlearning.data.local.LearningFiles
import com.jack.englishlearning.domain.model.LearningVideo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

fun interface CloudTransport {
    fun open(url: String): InputStream
}

class HttpsCloudTransport : CloudTransport {
    override fun open(url: String): InputStream {
        require(URL(url).protocol == "https")
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        connection.instanceFollowRedirects = false
        connection.setRequestProperty("Accept-Encoding", "identity")
        connection.setRequestProperty("Cache-Control", "no-cache")
        try {
            if (connection.responseCode != 200) throw IOException("教材下载失败：HTTP ${connection.responseCode}")
            return object : java.io.FilterInputStream(connection.inputStream) {
                override fun close() { try { super.close() } finally { connection.disconnect() } }
            }
        } catch (error: Exception) {
            connection.disconnect()
            throw error
        }
    }
}

/** Immutable media plus atomic active pointers: incomplete downloads never enter the library. */
class CloudLibrary(context: Context, private val transport: CloudTransport = HttpsCloudTransport(), val url: String = LIBRARY_URL) {
    private val home = File(context.filesDir, "cloud-library")

    private fun source(base: String) = File(home, sha256(base.toByteArray()))
    private fun lessonDirectory(base: String, id: String) = File(source(base), "lessons/$id")
    private fun readAtomic(file: File): String? = try { AtomicFile(file).openRead().bufferedReader().use { it.readText() } } catch (_: IOException) { null }
    private fun writeAtomic(file: File, text: String) {
        file.parentFile!!.mkdirs()
        val atomic = AtomicFile(file)
        val output = atomic.startWrite()
        try { output.write(text.toByteArray(Charsets.UTF_8)); atomic.finishWrite(output) }
        catch (error: Throwable) { atomic.failWrite(output); throw error }
    }

    private fun active(base: String, id: String): JSONObject? = try {
        readAtomic(File(lessonDirectory(base, id), "active.json"))?.let(::JSONObject)
    } catch (_: Exception) { null }

    private fun available(directory: File, active: JSONObject): Boolean = try {
        val video = File(directory, active.getString("video"))
        val transcript = File(directory, active.getString("transcript"))
        video.isFile && video.length() == active.getLong("videoSize") && transcript.isFile && transcript.length() == active.getLong("transcriptSize")
    } catch (_: Exception) { false }

    suspend fun videos(base: String = url): List<LearningVideo> = withContext(Dispatchers.IO) {
        if (base.isEmpty()) return@withContext emptyList()
        File(source(base), "lessons").listFiles().orEmpty().mapNotNull { directory ->
            val saved = active(base, directory.name) ?: return@mapNotNull null
            if (!available(directory, saved)) return@mapNotNull null
            LearningVideo(Uri.fromFile(File(directory, saved.getString("video"))).toString(), saved.getString("title"),
                Uri.fromFile(File(directory, saved.getString("transcript"))).toString())
        }.sortedByDescending { it.title }
    }

    suspend fun statuses(base: String = url): List<CloudLessonStatus> = withContext(Dispatchers.IO) {
        if (base.isEmpty()) return@withContext emptyList()
        val text = readAtomic(File(source(base), "catalog.json")) ?: return@withContext emptyList()
        val lessons = try { CloudFormat.catalog(text) } catch (_: Exception) { return@withContext emptyList() }
        lessons.map { lesson ->
            val saved = active(base, lesson.id)
            val installed = saved != null && available(lessonDirectory(base, lesson.id), saved)
            val video = if (installed) LearningVideo(
                Uri.fromFile(File(lessonDirectory(base, lesson.id), saved!!.getString("video"))).toString(),
                saved.getString("title"), Uri.fromFile(File(lessonDirectory(base, lesson.id), saved.getString("transcript"))).toString()
            ) else null
            CloudLessonStatus(lesson, installed, installed && saved!!.optString("version") != lesson.version, video)
        }
    }

    /** Stable progress key even when a new video hash changes the local filename. */
    fun positionKey(uri: String): String {
        val parsed = Uri.parse(uri)
        if (parsed.scheme != "file") return uri
        val file = File(parsed.path ?: return uri)
        val relative = try { file.relativeTo(home).invariantSeparatorsPath } catch (_: IllegalArgumentException) { return uri }
        val segments = relative.split('/')
        if (segments.size != 4 || segments[1] != "lessons" || relative.startsWith("..")) return uri
        val activeFile = File(file.parentFile, "active.json")
        val timeline = try { readAtomic(activeFile)?.let(::JSONObject)?.optString("timelineVersion", "1") ?: "1" } catch (_: Exception) { "1" }
        val key = "cloud:${segments[0]}:${segments[2]}"
        return if (timeline == "1") key else "$key:timeline:$timeline"
    }

    private suspend fun bytes(base: String, path: String, maximum: Long): ByteArray {
        val buffer = ByteArrayOutputStream()
        transport.open(base + CloudFormat.path(path)).use { input ->
            val block = ByteArray(64 * 1024)
            while (true) {
                currentCoroutineContext().ensureActive()
                val count = input.read(block)
                if (count < 0) break
                require(buffer.size().toLong() + count <= maximum) { "云端文件超过声明大小。" }
                buffer.write(block, 0, count)
            }
        }
        return buffer.toByteArray()
    }

    private suspend fun verifiedBytes(base: String, asset: CloudAsset): ByteArray = bytes(base, asset.path, asset.size).also {
        require(it.size.toLong() == asset.size && sha256(it) == asset.sha256) { "文件校验失败，请刷新重试。" }
    }

    suspend fun refresh(base: String = url): List<CloudLesson> = withContext(Dispatchers.IO) {
        require(base.isNotEmpty()) { "请先设置教材库地址。" }
        val text = bytes(base, "catalog.json", MAX_JSON_BYTES).toString(Charsets.UTF_8)
        val lessons = CloudFormat.catalog(text)
        writeAtomic(File(source(base), "catalog.json"), text)
        lessons
    }

    /** Downloads append to one bounded-memory staging MP4. Verified prefix survives process death. */
    suspend fun download(lesson: CloudLesson, base: String = url, progress: suspend (String) -> Unit = {}) = withContext(Dispatchers.IO) {
        downloadMutex.withLock {
                currentCoroutineContext().ensureActive()
                val directory = lessonDirectory(base, lesson.id)
                val saved = active(base, lesson.id)
                if (saved != null && saved.optString("version") == lesson.version && available(directory, saved)) return@withLock
                progress("正在下载 · ${lesson.title}")
                val manifest = CloudFormat.manifest(verifiedBytes(base, lesson.manifest).toString(Charsets.UTF_8), lesson)
                directory.mkdirs()
                val transcript = File(directory, "${manifest.transcript.sha256}.json")
                if (!validFile(transcript, manifest.transcript.size, manifest.transcript.sha256)) {
                    val data = verifiedBytes(base, manifest.transcript)
                    require(LearningFiles.paragraphs(data.toString(Charsets.UTF_8)).isNotEmpty()) { "双语文本为空。" }
                    writeAtomic(transcript, data.toString(Charsets.UTF_8))
                }
                val video = File(directory, "${manifest.videoHash}.mp4")
                if (!validFile(video, manifest.videoSize, manifest.videoHash)) {
                    val staging = File(directory, "${manifest.videoHash}.mp4.part")
                    val freeNeeded = (manifest.videoSize - staging.length()).coerceAtLeast(0) + 32L * 1024 * 1024
                    require(directory.usableSpace >= freeNeeded) { "手机空间不足，请至少释放 ${freeNeeded / 1024 / 1024} MiB 后重试。" }
                    RandomAccessFile(staging, "rw").use { file ->
                        var offset = 0L
                        for ((partIndex, chunk) in manifest.chunks.withIndex()) {
                            currentCoroutineContext().ensureActive()
                            val hasChunk = file.length() >= offset + chunk.size && hashRange(file, offset, chunk.size) == chunk.sha256
                            if (!hasChunk) {
                                file.setLength(offset)
                                file.seek(offset)
                                val checksum = MessageDigest.getInstance("SHA-256")
                                var received = 0L
                                try {
                                    transport.open(base + chunk.path).use { input ->
                                        val block = ByteArray(64 * 1024)
                                        while (true) {
                                            currentCoroutineContext().ensureActive()
                                            val count = input.read(block)
                                            if (count < 0) break
                                            require(received + count <= chunk.size) { "分块大小超出清单。" }
                                            file.write(block, 0, count)
                                            checksum.update(block, 0, count)
                                            received += count
                                        }
                                    }
                                    require(received == chunk.size && hex(checksum.digest()) == chunk.sha256) { "视频分块校验失败，请重试。" }
                                    file.fd.sync()
                                } catch (error: Throwable) {
                                    file.setLength(offset)
                                    throw error
                                }
                            }
                            offset += chunk.size
                            progress("${partIndex + 1}/${manifest.chunks.size} 块 · ${offset * 100 / manifest.videoSize}%")
                        }
                        file.setLength(manifest.videoSize)
                    }
                    require(validFile(staging, manifest.videoSize, manifest.videoHash)) { "完整视频校验失败。" }
                    check(staging.renameTo(video)) { "无法保存下载视频。" }
                }
                val installed = JSONObject().put("title", lesson.title).put("version", lesson.version)
                    .put("timelineVersion", lesson.timelineVersion)
                    .put("video", video.name).put("videoSize", manifest.videoSize)
                    .put("transcript", transcript.name).put("transcriptSize", manifest.transcript.size)
                writeAtomic(File(directory, "active.json"), installed.toString())
                progress("已下载 · ${lesson.title}")
        }
    }

    private suspend fun validFile(file: File, size: Long, hash: String): Boolean {
        if (!file.isFile || file.length() != size) return false
        val checksum = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val block = ByteArray(64 * 1024)
            while (true) {
                currentCoroutineContext().ensureActive()
                val count = input.read(block)
                if (count < 0) break
                checksum.update(block, 0, count)
            }
        }
        return hex(checksum.digest()) == hash
    }

    private suspend fun hashRange(file: RandomAccessFile, start: Long, size: Long): String {
        file.seek(start)
        val checksum = MessageDigest.getInstance("SHA-256")
        val block = ByteArray(64 * 1024)
        var remaining = size
        while (remaining > 0) {
            currentCoroutineContext().ensureActive()
            val count = file.read(block, 0, minOf(block.size.toLong(), remaining).toInt())
            if (count < 0) return ""
            checksum.update(block, 0, count)
            remaining -= count
        }
        return hex(checksum.digest())
    }

    companion object {
        private val downloadMutex = Mutex()
        fun sha256(bytes: ByteArray): String = hex(MessageDigest.getInstance("SHA-256").digest(bytes))
        private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it.toInt() and 255) }
    }
}

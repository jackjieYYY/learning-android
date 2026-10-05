package com.jack.englishlearning.data.update

import android.content.Context
import android.os.Build
import com.jack.englishlearning.data.cloud.CloudTransport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

const val UPDATE_REPOSITORY = "jackjieYYY/learning-android"
/** Fixed GitHub URL (not the REST API, so no anonymous rate limit); "latest" skips drafts and prereleases. */
const val UPDATE_URL = "https://github.com/$UPDATE_REPOSITORY/releases/latest/download/latest.json"
const val RELEASE_DOWNLOAD_PREFIX = "https://github.com/$UPDATE_REPOSITORY/releases/download/"

data class UpdateInfo(val versionCode: Long, val versionName: String, val apkUrl: String, val size: Long, val sha256: String, val notes: String)

object UpdateFormat {
    const val MAX_METADATA_BYTES = 64L * 1024
    const val MAX_APK_BYTES = 200L * 1024 * 1024
    private val versionPattern = Regex("""\d{1,3}\.\d{1,2}\.\d{1,2}""")
    private val hashPattern = Regex("[0-9a-f]{64}")
    private val fileNamePattern = Regex("""[A-Za-z0-9._-]{1,120}\.apk""")

    fun parse(text: String): UpdateInfo {
        val json = JSONObject(text)
        require(json.getInt("schemaVersion") == 1) { "更新信息版本不受支持。" }
        val versionName = json.getString("versionName").also { require(versionPattern.matches(it)) { "版本号无效。" } }
        val versionCode = json.getLong("versionCode").also { require(it in 1..Int.MAX_VALUE) { "版本代码无效。" } }
        val apkUrl = json.getString("apkUrl")
        val releasePrefix = "${RELEASE_DOWNLOAD_PREFIX}v$versionName/"
        require(apkUrl.startsWith(releasePrefix) && fileNamePattern.matches(apkUrl.removePrefix(releasePrefix))) { "安装包地址无效。" }
        val size = json.getLong("size").also { require(it in 1..MAX_APK_BYTES) { "安装包大小无效。" } }
        val sha256 = json.getString("sha256").also { require(hashPattern.matches(it)) { "安装包校验码无效。" } }
        val notes = json.optString("notes", "").trim().take(2000)
        return UpdateInfo(versionCode, versionName, apkUrl, size, sha256, notes)
    }
}

/** GitHub release URLs redirect to githubusercontent.com; HttpURLConnection never follows an https -> http downgrade. */
class HttpsUpdateTransport : CloudTransport {
    override fun open(url: String): InputStream {
        require(URL(url).protocol == "https")
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("Accept-Encoding", "identity")
        connection.setRequestProperty("Cache-Control", "no-cache")
        try {
            if (connection.responseCode != 200) throw IOException("更新下载失败：HTTP ${connection.responseCode}")
            check(connection.url.protocol == "https") { "更新地址不安全。" }
            return object : FilterInputStream(connection.inputStream) {
                override fun close() { try { super.close() } finally { connection.disconnect() } }
            }
        } catch (error: Exception) {
            connection.disconnect()
            throw error
        }
    }
}

/** Checks the release feed and stores a verified APK in the app-private cache. Installation is [UpdateInstaller]. */
class AppUpdater(
    private val context: Context,
    private val transport: CloudTransport = HttpsUpdateTransport(),
    private val verifyArchive: (File, UpdateInfo) -> Unit = { file, info -> verifyPackage(context, file, info) },
) {
    private val directory = File(context.cacheDir, "updates")

    suspend fun latest(): UpdateInfo = withContext(Dispatchers.IO) {
        UpdateFormat.parse(read(UPDATE_URL, UpdateFormat.MAX_METADATA_BYTES).toString(Charsets.UTF_8))
    }

    /** Remove downloaded packages that are not the given pending update (or all of them). */
    fun clean(keep: UpdateInfo? = null) {
        directory.listFiles().orEmpty().filter { keep == null || it.name != fileName(keep) }.forEach { it.delete() }
    }

    suspend fun download(info: UpdateInfo, progress: (Int) -> Unit = {}): File = withContext(Dispatchers.IO) {
        directory.mkdirs()
        val target = File(directory, fileName(info))
        if (target.isFile && target.length() == info.size && sha256(target) == info.sha256) {
            verifyArchive(target, info)
            return@withContext target
        }
        clean()
        directory.mkdirs()
        val freeNeeded = info.size + 16L * 1024 * 1024
        require(directory.usableSpace >= freeNeeded) { "手机空间不足，请至少释放 ${freeNeeded / 1024 / 1024} MiB 后重试。" }
        val staging = File(directory, "${fileName(info)}.part")
        try {
            val checksum = MessageDigest.getInstance("SHA-256")
            var received = 0L
            transport.open(info.apkUrl).use { input ->
                staging.outputStream().use { output ->
                    val block = ByteArray(64 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(block)
                        if (count < 0) break
                        require(received + count <= info.size) { "安装包超过声明大小。" }
                        output.write(block, 0, count)
                        checksum.update(block, 0, count)
                        received += count
                        progress((received * 100 / info.size).toInt())
                    }
                    output.fd.sync()
                }
            }
            require(received == info.size && hex(checksum.digest()) == info.sha256) { "安装包校验失败，请重试。" }
            verifyArchive(staging, info)
            check(staging.renameTo(target)) { "无法保存安装包。" }
            target
        } finally {
            staging.delete()
        }
    }

    private suspend fun read(url: String, maximum: Long): ByteArray {
        val buffer = ByteArrayOutputStream()
        transport.open(url).use { input ->
            val block = ByteArray(16 * 1024)
            while (true) {
                currentCoroutineContext().ensureActive()
                val count = input.read(block)
                if (count < 0) break
                require(buffer.size().toLong() + count <= maximum) { "更新信息过大。" }
                buffer.write(block, 0, count)
            }
        }
        return buffer.toByteArray()
    }

    companion object {
        fun fileName(info: UpdateInfo) = "${info.versionCode}-${info.sha256.take(16)}.apk"

        /** The package name and version inside the APK must match the feed; the system then enforces the signing key. */
        @Suppress("DEPRECATION")
        fun verifyPackage(context: Context, file: File, info: UpdateInfo) {
            val archive = context.packageManager.getPackageArchiveInfo(file.path, 0) ?: error("安装包无法解析。")
            val code = if (Build.VERSION.SDK_INT >= 28) archive.longVersionCode else archive.versionCode.toLong()
            require(archive.packageName == context.packageName && code == info.versionCode) { "安装包与更新信息不一致。" }
        }


        private fun sha256(file: File): String {
            val checksum = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val block = ByteArray(64 * 1024)
                while (true) {
                    val count = input.read(block)
                    if (count < 0) break
                    checksum.update(block, 0, count)
                }
            }
            return hex(checksum.digest())
        }

        private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it.toInt() and 255) }
    }
}

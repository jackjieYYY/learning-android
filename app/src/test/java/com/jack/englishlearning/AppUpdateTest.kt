package com.jack.englishlearning

import android.content.Context
import com.jack.englishlearning.data.cloud.CloudTransport
import com.jack.englishlearning.data.update.*
import com.jack.englishlearning.ui.update.UpdateUiState
import com.jack.englishlearning.ui.update.UpdateViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.security.MessageDigest

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class AppUpdateTest {
    private lateinit var context: Context
    private val apk = ByteArray(300_000) { (it % 251).toByte() }
    private val apkUrl = "${RELEASE_DOWNLOAD_PREFIX}v1.4.1/learning-android-1.4.1.apk"
    private val files = mutableMapOf<String, ByteArray>()
    private var offline = false
    private val transport = CloudTransport { url ->
        if (offline) throw IOException("offline")
        ByteArrayInputStream(files[url] ?: error("missing $url"))
    }

    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun feed(code: Long = 10401, name: String = "1.4.1", url: String = apkUrl, size: Long = apk.size.toLong(), hash: String = sha(apk)) =
        JSONObject().put("schemaVersion", 1).put("versionCode", code).put("versionName", name).put("apkUrl", url)
            .put("size", size).put("sha256", hash).put("notes", "修复若干问题").toString()

    @Before fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        context = RuntimeEnvironment.getApplication()
        File(context.cacheDir, "updates").deleteRecursively()
        files[UPDATE_URL] = feed().toByteArray()
        files[apkUrl] = apk
    }

    @After fun tearDown() { Dispatchers.resetMain() }

    private fun updater(verify: (File, UpdateInfo) -> Unit = { _, _ -> }) = AppUpdater(context, transport, verify)

    @Test fun parsesValidFeed() {
        val info = UpdateFormat.parse(feed())
        assertEquals(10401L, info.versionCode)
        assertEquals("1.4.1", info.versionName)
        assertEquals("修复若干问题", info.notes)
    }

    @Test fun rejectsUnsafeOrMalformedFeeds() {
        val bad = listOf(
            feed(url = "http://github.com/jackjieYYY/learning-android/releases/download/v1.4.1/a.apk"),
            feed(url = "https://evil.example/learning-android-1.4.1.apk"),
            feed(url = "https://github.com/someone/else/releases/download/v1.4.1/a.apk"),
            feed(url = "${RELEASE_DOWNLOAD_PREFIX}v1.4.0/learning-android-1.4.1.apk"),
            feed(url = "${RELEASE_DOWNLOAD_PREFIX}v1.4.1/../a.apk"),
            feed(url = "${RELEASE_DOWNLOAD_PREFIX}v1.4.1/a.apk?x=1"),
            feed(name = "1.4"), feed(code = 0), feed(size = 0), feed(size = UpdateFormat.MAX_APK_BYTES + 1),
            feed(hash = "ABC"), JSONObject(feed()).put("schemaVersion", 2).toString(),
        )
        for (text in bad) {
            assertThrows(text, Exception::class.java) { UpdateFormat.parse(text) }
        }
    }

    @Test fun downloadsVerifiesAndReusesApk() = runBlocking {
        var verified = 0
        val updater = updater { _, _ -> verified++ }
        val info = updater.latest()
        val progress = mutableListOf<Int>()
        val file = updater.download(info) { progress += it }
        assertArrayEquals(apk, file.readBytes())
        assertEquals(100, progress.last())
        files.remove(apkUrl)
        assertEquals(file, updater.download(info))
        assertEquals(2, verified)
    }

    @Test fun rejectsCorruptOrOversizedApkAndLeavesNoFile() = runBlocking {
        val updater = updater()
        val info = updater.latest()
        files[apkUrl] = apk.copyOf().also { it[0] = 9 }
        assertThrows(IllegalArgumentException::class.java) { runBlocking { updater.download(info) } }
        files[apkUrl] = apk + byteArrayOf(1)
        assertThrows(IllegalArgumentException::class.java) { runBlocking { updater.download(info) } }
        assertTrue(File(context.cacheDir, "updates").listFiles().orEmpty().isEmpty())
    }

    @Test fun rejectsApkWhosePackageDoesNotMatch() = runBlocking {
        val updater = updater { _, _ -> throw IllegalArgumentException("安装包与更新信息不一致。") }
        assertThrows(IllegalArgumentException::class.java) { runBlocking { updater.download(updater.latest()) } }
        assertTrue(File(context.cacheDir, "updates").listFiles().orEmpty().isEmpty())
    }

    private fun viewModel(current: Long = 10400, enabled: Boolean = true, permission: Boolean = true,
                          installed: MutableList<File> = mutableListOf(), events: MutableStateFlow<InstallEvent?> = MutableStateFlow(null)) =
        UpdateViewModel(updater(), current, enabled, { permission }, { installed += it }, events)

    private fun awaitState(model: UpdateViewModel, predicate: (UpdateUiState) -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!predicate(model.state)) {
            check(System.currentTimeMillis() < deadline) { "state stuck at ${model.state}" }
            Thread.sleep(10)
        }
    }

    @Test fun noUpdateWhenCurrentOrDisabledOrOffline() {
        viewModel(current = 10401).apply { check(); Thread.sleep(200); assertEquals(UpdateUiState.None, state) }
        viewModel(enabled = false).apply { check(); Thread.sleep(200); assertEquals(UpdateUiState.None, state) }
        offline = true
        viewModel().apply { check(); Thread.sleep(200); assertEquals(UpdateUiState.None, state) }
    }

    @Test fun userTapDownloadsThenInstalls() {
        val installed = mutableListOf<File>()
        val events = MutableStateFlow<InstallEvent?>(null)
        val model = viewModel(installed = installed, events = events)
        model.check()
        awaitState(model) { it is UpdateUiState.Available }
        assertTrue("checking must not download", installed.isEmpty())
        model.update()
        awaitState(model) { it is UpdateUiState.Installing }
        assertEquals(1, installed.size)
        events.value = InstallEvent.Failed("安装失败：手机空间不足。")
        awaitState(model) { it is UpdateUiState.Ready }
        assertEquals("安装失败：手机空间不足。", (model.state as UpdateUiState.Ready).message)
    }

    @Test fun missingInstallPermissionAsksUserFirst() {
        val installed = mutableListOf<File>()
        val model = viewModel(permission = false, installed = installed)
        model.check()
        awaitState(model) { it is UpdateUiState.Available }
        model.update()
        awaitState(model) { it is UpdateUiState.Ready }
        assertTrue(model.permissionRequest)
        assertTrue(installed.isEmpty())
    }
}

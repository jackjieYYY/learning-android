package com.jack.englishlearning

import android.content.Context
import com.jack.englishlearning.data.cloud.*
import com.jack.englishlearning.data.local.TranscriptReader
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class CloudLibraryTest {
    private val base = "https://library.example/"
    private lateinit var library: CloudLibrary
    private lateinit var context: Context
    private val files = mutableMapOf<String, ByteArray>()
    private val requests = mutableListOf<String>()
    private var failPath: String? = null
    private var corruptPath: String? = null
    private val transport = CloudTransport { url ->
        val path = url.removePrefix(base)
        requests += path
        if (path == failPath) throw IOException("connection lost")
        ByteArrayInputStream(if (path == corruptPath) "bad!".toByteArray() else files[path] ?: error("missing $path"))
    }

    @Before fun setup() {
        context = RuntimeEnvironment.getApplication()
        File(context.filesDir, "cloud-library").deleteRecursively()
        library = CloudLibrary(context, transport, base)
    }

    private fun asset(path: String, data: ByteArray): JSONObject {
        files[path] = data
        return JSONObject().put("path", path).put("size", data.size).put("sha256", CloudLibrary.sha256(data))
    }

    private fun publish(translation: String = "你好", video: String = "abcdefgh", timeline: String = "1", id: String = "lesson-one", append: Boolean = false): String {
        val chunks = JSONArray()
        video.chunked(4).forEachIndexed { index, part -> chunks.put(asset("video/${CloudLibrary.sha256(video.toByteArray())}/part-$index.bin", part.toByteArray())) }
        val text = JSONObject().put("paragraphs", JSONArray().put(JSONObject()
            .put("sentences", JSONArray().put(JSONObject().put("startMs", 0).put("text", "Hello.")))
            .put("translation", translation))).toString().toByteArray()
        val transcript = asset("transcripts/${CloudLibrary.sha256(text)}.json", text)
        val manifest = JSONObject().put("schemaVersion", 1).put("id", id).put("title", id)
            .put("timelineVersion", timeline)
            .put("video", JSONObject().put("size", video.length).put("sha256", CloudLibrary.sha256(video.toByteArray())).put("chunks", chunks))
            .put("transcript", transcript).toString().toByteArray()
        val version = CloudLibrary.sha256(manifest)
        val description = asset("manifests/$version.json", manifest)
        val lessons = if (append) JSONObject(files.getValue("catalog.json").toString(Charsets.UTF_8)).getJSONArray("lessons") else JSONArray()
        lessons.put(JSONObject().put("id", id).put("title", id).put("version", version)
            .put("timelineVersion", timeline).put("videoBytes", video.length).put("manifest", description))
        files["catalog.json"] = JSONObject().put("schemaVersion", 1).put("lessons", lessons).toString().toByteArray()
        return version
    }

    private suspend fun downloadFirst() = library.download(library.refresh().first())
    private fun chunkPath(video: String = "abcdefgh", index: Int) = "video/${CloudLibrary.sha256(video.toByteArray())}/part-$index.bin"

    @Test fun discoveryNeverDownloadsMediaAndOnlySelectedCourseIsDownloaded() = runBlocking {
        publish()
        publish(id = "lesson-two", video = "ijklmnop", append = true)
        val lessons = library.refresh()
        assertEquals(listOf("catalog.json"), requests)
        assertTrue(library.videos().isEmpty())
        assertEquals(2, library.statuses().size)
        library.download(lessons[1])
        assertEquals(listOf("lesson-two"), library.videos().map { it.title })
        assertFalse(requests.contains(chunkPath(index = 0)))
        assertFalse(library.statuses()[0].downloaded)
        assertTrue(library.statuses()[1].downloaded)
    }

    @Test fun interruptionPreservesVerifiedPrefixAndNeverExposesIncompleteLesson() = runBlocking {
        publish()
        failPath = chunkPath(index = 1)
        try { downloadFirst(); fail("Expected interrupted download") } catch (_: IOException) { }
        assertTrue(library.videos().isEmpty())
        assertFalse(library.statuses().single().downloaded)
        failPath = null
        requests.clear()
        downloadFirst()
        assertFalse(requests.contains(chunkPath(index = 0)))
        assertTrue(requests.contains(chunkPath(index = 1)))
        val video = library.videos().single()
        assertEquals("abcdefgh", File(java.net.URI(video.uri)).readText())
        assertTrue(library.statuses().single().downloaded)
        assertEquals(1, TranscriptReader(context).transcript(video.transcriptUri!!).size)
    }

    @Test fun corruptChunkIsRejectedAndOnlyFailedChunkIsRetried() = runBlocking {
        publish()
        corruptPath = chunkPath(index = 1)
        try { downloadFirst(); fail("Expected checksum rejection") } catch (_: IllegalArgumentException) { }
        assertTrue(library.videos().isEmpty())
        corruptPath = null
        requests.clear()
        downloadFirst()
        assertEquals(listOf(chunkPath(index = 1)), requests.filter { it.startsWith("video/") })
    }

    @Test fun translationUpdateRequiresSelectionReusesVideoAndFailedUpdateKeepsPreviousLesson() = runBlocking {
        publish()
        downloadFirst()
        val previous = library.videos().single()
        publish("修订翻译")
        requests.clear()
        val lesson = library.refresh().single()
        assertEquals(previous, library.videos().single())
        assertTrue(library.statuses().single().updateAvailable)
        assertEquals(listOf("catalog.json"), requests)
        library.download(lesson)
        val updated = library.videos().single()
        assertEquals(previous.uri, updated.uri)
        assertNotEquals(previous.transcriptUri, updated.transcriptUri)
        assertTrue(requests.none { it.startsWith("video/") })
        publish("新视频翻译", "ijklmnop")
        failPath = chunkPath("ijklmnop", 1)
        try { downloadFirst(); fail("Expected interruption") } catch (_: IOException) { }
        assertEquals(updated, library.videos().single())
        assertTrue(library.statuses().single().updateAvailable)
        failPath = null
        val oldKey = library.positionKey(updated.uri)
        downloadFirst()
        assertEquals(oldKey, library.positionKey(library.videos().single().uri))
    }

    @Test fun explicitSelectionPinsVersionEvenWhenCatalogPublishesAnotherVersion() = runBlocking {
        publish()
        val selected = library.refresh().single()
        publish("Later revision", "ijklmnop")
        requests.clear()
        library.download(CloudFormat.catalog(CloudFormat.encodeLesson(selected)).single())
        assertFalse(requests.contains("catalog.json"))
        assertEquals("abcdefgh", File(java.net.URI(library.videos().single().uri)).readText())
        library.refresh()
        assertTrue(library.statuses().single().updateAvailable)
    }

    @Test fun unchangedSelectionMakesNoMediaRequests() = runBlocking {
        publish()
        downloadFirst()
        requests.clear()
        downloadFirst()
        assertEquals(listOf("catalog.json"), requests)
    }

    @Test fun changedTimelineGetsSeparatePlaybackPosition() = runBlocking {
        publish()
        downloadFirst()
        val oldKey = library.positionKey(library.videos().single().uri)
        publish(video = "ijklmnop", timeline = "2")
        downloadFirst()
        assertNotEquals(oldKey, library.positionKey(library.videos().single().uri))
    }

    @Test fun sourceIsolationAndHardcodedDefault() = runBlocking {
        publish()
        downloadFirst()
        assertTrue(CloudLibrary(context, transport, "https://another.example/").videos().isEmpty())
        assertEquals(1, library.videos().size)
        assertEquals(LIBRARY_URL, CloudLibrary(context).url)
    }

    @Test fun invalidCatalogDoesNotReplaceLastKnownCatalog() = runBlocking {
        publish()
        library.refresh()
        files["catalog.json"] = "{\"schemaVersion\":99,\"lessons\":[]}".toByteArray()
        try { library.refresh(); fail("Expected unsupported version") } catch (_: IllegalArgumentException) { }
        assertEquals(1, library.statuses().size)
    }

    @Test fun rejectsTraversalAndNonHttpsEndpoints() {
        for (path in listOf("../secret", "https://evil.example/file", "/absolute", "a/%2e%2e/b", "a//b")) {
            try { CloudFormat.path(path); fail("Accepted $path") } catch (_: IllegalArgumentException) { }
        }
        for (url in listOf("http://library.example", "https://user:password@library.example", "https://library.example/?token=x")) {
            try { CloudFormat.baseUrl(url); fail("Accepted $url") } catch (_: IllegalArgumentException) { }
        }
        assertEquals(base, CloudFormat.baseUrl(base.trimEnd('/')))
    }
}

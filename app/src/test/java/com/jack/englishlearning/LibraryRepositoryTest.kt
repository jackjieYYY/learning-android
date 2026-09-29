package com.jack.englishlearning

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.DocumentsContract
import com.jack.englishlearning.data.LibraryRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class LibraryRepositoryTest {
    @Test fun scansRootAndImmediateFoldersAndPairsSidecars() = runBlocking {
        val provider = libraryProvider()
        val videos = LibraryRepository(RuntimeEnvironment.getApplication()).scan(treeUri())

        assertEquals(listOf("root video", "unit 1"), videos.map { it.title })
        assertEquals(provider.uriFor("root-video"), videos[0].uri)
        assertNull(videos[0].transcriptUri)
        assertEquals(provider.uriFor("unit-transcript"), videos[1].transcriptUri)
        // Files in deeper folders must not be scanned.
        assertFalse(videos.any { it.title == "nested" })
    }

    @Test fun revokedDirectoryAccessFailsRatherThanReturningAnEmptyLibrary() = runBlocking {
        val provider = libraryProvider()
        provider.accessRevoked = true

        try {
            LibraryRepository(RuntimeEnvironment.getApplication()).scan(treeUri())
            fail("Expected a directory access error")
        } catch (error: IllegalStateException) {
            assertTrue(error.message.orEmpty().contains("授权已失效"))
        }
    }

    private fun libraryProvider(): TestDocumentsProvider {
        val context = RuntimeEnvironment.getApplication() as Context
        val provider = TestDocumentsProvider()
        val info = android.content.pm.ProviderInfo().apply { authority = AUTHORITY; exported = true }
        provider.attachInfo(context, info)
        ShadowContentResolver.registerProviderInternal(AUTHORITY, provider)
        context.grantUriPermission(context.packageName, Uri.parse(treeUri()), Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return provider
    }

    private fun treeUri() = "content://$AUTHORITY/tree/root"

    private class TestDocumentsProvider : ContentProvider() {
        private data class Entry(val id: String, val parent: String?, val name: String, val mime: String)
        private val entries = listOf(
            Entry("root", null, "library", DocumentsContract.Document.MIME_TYPE_DIR),
            Entry("root-video", "root", "root-video.mp4", "video/mp4"),
            Entry("folder", "root", "unit", DocumentsContract.Document.MIME_TYPE_DIR),
            Entry("unit-video", "folder", "unit_1.MP4", "video/mp4"),
            Entry("unit-transcript", "folder", "UNIT_1.bilingual.TXT", "text/plain"),
            Entry("deeper", "folder", "deeper", DocumentsContract.Document.MIME_TYPE_DIR),
            Entry("nested-video", "deeper", "nested.mp4", "video/mp4")
        )
        var accessRevoked = false

        fun uriFor(id: String) = "content://$AUTHORITY/tree/root/document/$id"

        override fun onCreate() = true
        override fun getType(uri: Uri): String? = entries.firstOrNull { it.id == uri.lastPathSegment }?.mime
        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
            if (accessRevoked) throw SecurityException("Permission revoked")
            val parts = uri.pathSegments
            val found = if (parts.last() == "children") {
                entries.filter { it.parent == parts[parts.size - 2] }
            } else entries.filter { it.id == parts.last() }
            val columns = projection ?: arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            return MatrixCursor(columns).apply {
                found.forEach { entry -> addRow(Array<Any?>(columns.size) { index ->
                    when (columns[index]) {
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID -> entry.id
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME -> entry.name
                        DocumentsContract.Document.COLUMN_MIME_TYPE -> entry.mime
                        DocumentsContract.Document.COLUMN_FLAGS -> 0
                        else -> null
                    }
                }) }
            }
        }
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
    }

    companion object { private const val AUTHORITY = "com.jack.englishlearning.testdocuments" }
}

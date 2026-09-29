package com.jack.englishlearning.data.local

import android.content.Context
import android.content.Intent
import android.net.Uri

class LibraryRootStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("library", Context.MODE_PRIVATE)

    val root: String? get() = prefs.getString("root", null)

    /** Save the new grant before releasing the old one. A failed grant leaves the old root intact. */
    fun selectRoot(uri: Uri) {
        val resolver = context.contentResolver
        resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val previous = root
        prefs.edit().putString("root", uri.toString()).apply()
        if (previous != null && previous != uri.toString()) {
            try {
                resolver.releasePersistableUriPermission(Uri.parse(previous), Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: SecurityException) { /* Already revoked by provider. */ }
        }
    }
}

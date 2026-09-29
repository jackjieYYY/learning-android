package com.jack.englishlearning.data.local

import android.content.Context

class PlaybackPositionStore(context: Context) {
    private val prefs = context.getSharedPreferences("library", Context.MODE_PRIVATE)

    fun position(uri: String): Long = prefs.getLong("position:$uri", 0)

    fun savePosition(uri: String, position: Long) {
        prefs.edit().putLong("position:$uri", position.coerceAtLeast(0)).apply()
    }
}

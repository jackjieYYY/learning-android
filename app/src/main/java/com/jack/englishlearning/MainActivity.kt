package com.jack.englishlearning

import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.jack.englishlearning.ui.App
import com.jack.englishlearning.ui.theme.PixelAppTheme
import com.jack.englishlearning.ui.theme.PixelTheme
import com.jack.englishlearning.ui.theme.ThemeSettings
import com.jack.englishlearning.ui.theme.paletteFor

class MainActivity : ComponentActivity() {
    var inPictureInPicture by mutableStateOf(false)
        private set
    private val themeSettings by lazy { ThemeSettings(this) }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPictureInPicture = isInPictureInPictureMode
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val dark = paletteFor(themeSettings.mode, isSystemInDarkTheme()).isDark
            LaunchedEffect(dark) {
                // The chosen theme, not the system one, decides the status/navigation bar icon colour.
                val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
                    else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            PixelAppTheme(themeSettings.mode) {
                Box(Modifier.fillMaxSize().background(PixelTheme.palette.background)) {
                    App(themeSettings)
                }
            }
        }
    }
}

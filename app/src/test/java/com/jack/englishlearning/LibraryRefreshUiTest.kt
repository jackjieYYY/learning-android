package com.jack.englishlearning

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.jack.englishlearning.ui.library.LibraryScreen
import com.jack.englishlearning.ui.library.LibraryUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w411dp-h891dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LibraryRefreshUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun pullingEmptyLibraryRefreshesWithoutStartingDownloads() {
        var refreshes = 0
        var downloads = 0
        compose.setContent {
            MaterialTheme {
                LibraryScreen(LibraryUiState(), onRefresh = { refreshes++ },
                    onDownload = { downloads++ }, onPause = {}, onVideo = {})
            }
        }
        compose.onNodeWithText("暂无课程，下拉刷新。").assertExists()
        compose.onNodeWithText("刷新课程").assertDoesNotExist()
        compose.onNodeWithText("导入本地目录").assertDoesNotExist()
        compose.onNode(hasScrollAction()).performTouchInput {
            swipeDown(startY = 20f, endY = height * 0.85f, durationMillis = 700)
        }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(1, refreshes)
            assertEquals(0, downloads)
        }
    }
}

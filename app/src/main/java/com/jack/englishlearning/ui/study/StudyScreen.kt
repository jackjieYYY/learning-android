package com.jack.englishlearning.ui.study

import android.content.res.Configuration
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jack.englishlearning.MainActivity
import com.jack.englishlearning.data.LibraryRepository
import com.jack.englishlearning.domain.model.LearningVideo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StudyScreen(video: LearningVideo, repository: LibraryRepository, state: StudyUiState, onBack: () -> Unit) {
    val listState = rememberLazyListState()
    val inPip = (LocalActivity.current as MainActivity).inPictureInPicture
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    // Move the same player composition between layouts without releasing playback.
    val videoPane = remember(video.uri, repository) {
        movableContentOf<Modifier> { modifier -> VideoPane(video, repository, modifier) }
    }
    Scaffold(topBar = {
        if (!inPip) TopAppBar(title = { Text(video.title, maxLines = 1) }, navigationIcon = {
            TextButton(onClick = onBack) { Text("返回") }
        })
    }) { padding ->
        val contentModifier = Modifier.fillMaxSize().padding(if (inPip) PaddingValues(0.dp) else padding)
        val bilingualPane: @Composable (Modifier) -> Unit = { modifier ->
            Column(modifier) {
                Text("中英对照 · 手动滑动", style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                when {
                    state.loading -> CircularProgressIndicator(Modifier.padding(16.dp))
                    state.error != null -> Text(state.error, modifier = Modifier.padding(16.dp))
                    else -> SelectionContainer(Modifier.weight(1f).fillMaxWidth()) {
                        LazyColumn(state = listState, modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            items(state.paragraphs.size) { index ->
                                Text(state.paragraphs[index], fontSize = 18.sp, lineHeight = 28.sp)
                            }
                        }
                    }
                }
            }
        }
        when {
            inPip -> Box(contentModifier) {
                videoPane(Modifier.fillMaxSize())
            }
            isLandscape -> Row(contentModifier) {
                videoPane(Modifier.weight(1f).fillMaxHeight())
                VerticalDivider()
                bilingualPane(Modifier.weight(3f).fillMaxHeight())
            }
            else -> Column(contentModifier) {
                videoPane(Modifier.weight(1f).fillMaxWidth())
                HorizontalDivider()
                bilingualPane(Modifier.weight(3f).fillMaxWidth())
            }
        }
    }
}


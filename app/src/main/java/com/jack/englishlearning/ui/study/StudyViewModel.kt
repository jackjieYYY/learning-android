package com.jack.englishlearning.ui.study

import com.jack.englishlearning.domain.model.TranscriptParagraph
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jack.englishlearning.data.LibraryRepository
import com.jack.englishlearning.domain.model.LearningVideo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

data class StudyUiState(val paragraphs: List<TranscriptParagraph> = emptyList(), val loading: Boolean = true, val error: String? = null)

class StudyViewModel(private val repository: LibraryRepository) : ViewModel() {
    var state by mutableStateOf(StudyUiState())
        private set
    private var currentUri: String? = null
    private var loadJob: Job? = null

    fun clear() {
        loadJob?.cancel()
        currentUri = null
        state = StudyUiState()
    }

    fun load(video: LearningVideo) {
        if (video.uri == currentUri) return
        loadJob?.cancel()
        currentUri = video.uri
        state = StudyUiState()
        loadJob = viewModelScope.launch {
            try {
                val uri = video.transcriptUri
                if (uri == null) state = state.copy(error = "此视频没有双语文本，请添加同名 .bilingual.json 后返回列表刷新。")
                else {
                    val paragraphs = repository.transcript(uri)
                    state = state.copy(paragraphs = paragraphs, error = if (paragraphs.isEmpty()) "双语文本为空。" else null)
                }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (exception: Exception) {
                state = state.copy(error = exception.message ?: "双语文本读取失败。")
            }
            state = state.copy(loading = false)
        }
    }
}

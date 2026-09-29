package com.jack.englishlearning.ui.library

import android.net.Uri
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

data class LibraryUiState(
    val root: String? = null,
    val videos: List<LearningVideo> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null
)

class LibraryViewModel(private val repository: LibraryRepository) : ViewModel() {
    var state by mutableStateOf(LibraryUiState(root = repository.root))
        private set
    private var scanJob: Job? = null

    init { refresh() }

    fun selectRoot(uri: Uri) {
        try {
            repository.selectRoot(uri)
            state = state.copy(root = repository.root)
            refresh()
        } catch (_: SecurityException) {
            state = state.copy(error = "无法保留此目录的读取权限，请选择手机本地的学习目录。")
        }
    }

    fun refresh() {
        scanJob?.cancel()
        val root = state.root
        state = state.copy(videos = emptyList(), error = null, loading = root != null)
        if (root == null) return
        scanJob = viewModelScope.launch {
            try {
                state = state.copy(videos = repository.scan(root))
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (exception: Exception) {
                state = state.copy(error = exception.message ?: "读取目录失败，请重新选择。")
            }
            state = state.copy(loading = false)
        }
    }
}

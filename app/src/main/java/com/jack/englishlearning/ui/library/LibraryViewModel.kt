package com.jack.englishlearning.ui.library

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import com.jack.englishlearning.data.LibraryRepository
import com.jack.englishlearning.data.cloud.CloudLesson
import com.jack.englishlearning.data.cloud.CloudLessonStatus
import com.jack.englishlearning.data.cloud.LibrarySyncScheduler
import com.jack.englishlearning.domain.model.LearningVideo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

data class CourseDownload(val state: WorkInfo.State, val message: String) {
    /** Chunk progress reported by the worker as "… · 42%"; null before the first chunk. */
    val percent: Int? get() = Regex("""(\d{1,3})%""").findAll(message).lastOrNull()
        ?.groupValues?.get(1)?.toIntOrNull()?.coerceIn(0, 100)
}
data class LibraryUiState(
    val videos: List<LearningVideo> = emptyList(),
    val cloudLessons: List<CloudLessonStatus> = emptyList(),
    val downloads: Map<String, CourseDownload> = emptyMap(),
    val loading: Boolean = false,
    val error: String? = null
)

class LibraryViewModel(private val repository: LibraryRepository) : ViewModel() {
    var state by mutableStateOf(LibraryUiState())
        private set
    private var scanJob: Job? = null
    private var refreshJob: Job? = null

    init {
        loadLocal()
        viewModelScope.launch {
            repository.sync.work.collect { work ->
                val downloads = work.associate { info ->
                    val course = info.tags.first { it.startsWith(LibrarySyncScheduler.COURSE_TAG) }.removePrefix(LibrarySyncScheduler.COURSE_TAG)
                    val message = when (info.state) {
                        WorkInfo.State.RUNNING -> info.progress.getString("message") ?: "正在下载…"
                        WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> info.progress.getString("message") ?: "等待网络连接或下载队列"
                        WorkInfo.State.FAILED -> info.outputData.getString("message") ?: "下载失败，请重试。"
                        WorkInfo.State.CANCELLED -> "已暂停"
                        WorkInfo.State.SUCCEEDED -> "下载完成"
                    }
                    course to CourseDownload(info.state, message)
                }
                state = state.copy(downloads = downloads)
                loadLocal()
            }
        }
    }

    fun download(lesson: CloudLesson) { repository.sync.download(lesson) }
    fun pause(id: String) { repository.sync.pause(id) }

    /** Refreshing discovery must never enqueue video downloads. */
    fun refresh() {
        if (refreshJob?.isActive == true) return
        state = state.copy(error = null)
        loadLocal()
        refreshJob = viewModelScope.launch {
            state = state.copy(loading = true)
            try {
                repository.cloud.refresh()
                loadLocal()
            } catch (cancel: CancellationException) { throw cancel }
            catch (error: Exception) { state = state.copy(error = "无法更新课程列表，已下载教材仍可使用。${error.message.orEmpty()}") }
            finally { state = state.copy(loading = false) }
        }
    }

    private fun loadLocal() {
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            val cloudLessons = repository.cloud.statuses()
            val listedUris = cloudLessons.mapNotNull { it.video?.uri }.toSet()
            val orphaned = repository.cloud.videos().filter { it.uri !in listedUris }
            state = state.copy(videos = orphaned, cloudLessons = cloudLessons)
        }
    }
}

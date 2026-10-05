package com.jack.englishlearning.ui.update

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jack.englishlearning.data.update.AppUpdater
import com.jack.englishlearning.data.update.InstallEvent
import com.jack.englishlearning.data.update.UpdateInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.io.File

sealed interface UpdateUiState {
    data object None : UpdateUiState
    data class Available(val info: UpdateInfo, val message: String? = null) : UpdateUiState
    data class Downloading(val info: UpdateInfo, val percent: Int) : UpdateUiState
    data class Ready(val info: UpdateInfo, val apk: File, val message: String? = null) : UpdateUiState
    data class Installing(val info: UpdateInfo, val apk: File) : UpdateUiState
}

/** User-driven: checking is automatic, downloading and installing only happen after a tap. */
class UpdateViewModel(
    private val updater: AppUpdater,
    private val currentVersionCode: Long,
    private val enabled: Boolean,
    private val canInstall: () -> Boolean,
    private val install: (File) -> Unit,
    installEvents: Flow<InstallEvent?>,
) : ViewModel() {
    var state by mutableStateOf<UpdateUiState>(UpdateUiState.None)
        private set
    /** Set when the user must allow "install unknown apps" in system settings. */
    var permissionRequest by mutableStateOf(false)
        private set
    private var job: Job? = null

    init {
        viewModelScope.launch {
            installEvents.collect { event ->
                val current = state as? UpdateUiState.Installing ?: return@collect
                state = when (event) {
                    null, InstallEvent.WaitingForUser, InstallEvent.Installed -> current
                    InstallEvent.Cancelled -> UpdateUiState.Ready(current.info, current.apk, "已取消安装。")
                    is InstallEvent.Failed -> UpdateUiState.Ready(current.info, current.apk, event.message)
                }
            }
        }
    }

    fun check() {
        if (!enabled || job?.isActive == true || state is UpdateUiState.Installing) return
        job = viewModelScope.launch {
            try {
                val info = updater.latest()
                if (info.versionCode <= currentVersionCode) {
                    updater.clean()
                    state = UpdateUiState.None
                } else if (state.info() != info) {
                    updater.clean(keep = info)
                    state = UpdateUiState.Available(info)
                }
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { /* Offline or GitHub unreachable: stay quiet, the app works without updates. */ }
        }
    }

    fun update() {
        when (val current = state) {
            is UpdateUiState.Available -> download(current.info)
            is UpdateUiState.Ready -> installOrAskPermission(current.info, current.apk)
            else -> Unit
        }
    }

    fun permissionHandled() { permissionRequest = false }

    private fun download(info: UpdateInfo) {
        if (job?.isActive == true) job?.cancel()
        job = viewModelScope.launch {
            state = UpdateUiState.Downloading(info, 0)
            try {
                val apk = updater.download(info) { percent -> state = UpdateUiState.Downloading(info, percent) }
                state = UpdateUiState.Ready(info, apk)
                installOrAskPermission(info, apk)
            } catch (cancel: CancellationException) { throw cancel }
            catch (error: Exception) { state = UpdateUiState.Available(info, error.message ?: "下载失败，请重试。") }
        }
    }

    private fun installOrAskPermission(info: UpdateInfo, apk: File) {
        if (!apk.isFile) { state = UpdateUiState.Available(info, "安装包已被清理，请重新下载。"); return }
        if (!canInstall()) {
            state = UpdateUiState.Ready(info, apk, "请在系统设置中允许“英语听力”安装未知应用，返回后点“安装”。")
            permissionRequest = true
            return
        }
        state = UpdateUiState.Installing(info, apk)
        try { install(apk) }
        catch (error: Exception) { state = UpdateUiState.Ready(info, apk, "安装失败：${error.message.orEmpty()}") }
    }

    private fun UpdateUiState.info(): UpdateInfo? = when (this) {
        UpdateUiState.None -> null
        is UpdateUiState.Available -> info
        is UpdateUiState.Downloading -> info
        is UpdateUiState.Ready -> info
        is UpdateUiState.Installing -> info
    }
}

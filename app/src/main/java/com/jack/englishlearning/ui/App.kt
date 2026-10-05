package com.jack.englishlearning.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jack.englishlearning.BuildConfig
import com.jack.englishlearning.data.LibraryRepository
import com.jack.englishlearning.data.update.AppUpdater
import com.jack.englishlearning.data.update.UpdateInstaller
import com.jack.englishlearning.ui.update.UpdateCard
import com.jack.englishlearning.ui.update.UpdateViewModel
import com.jack.englishlearning.domain.model.LearningVideo
import com.jack.englishlearning.ui.library.LibraryScreen
import com.jack.englishlearning.ui.library.LibraryViewModel
import com.jack.englishlearning.ui.study.StudyScreen
import com.jack.englishlearning.ui.study.StudyViewModel
import com.jack.englishlearning.ui.settings.SettingsScreen
import com.jack.englishlearning.ui.theme.ThemeSettings

@Composable
fun App(themeSettings: ThemeSettings) {
    val context = LocalContext.current
    val repository = remember { LibraryRepository(context.applicationContext) }
    val factory = remember(repository) {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = when (modelClass) {
                LibraryViewModel::class.java -> LibraryViewModel(repository)
                StudyViewModel::class.java -> StudyViewModel(repository)
                UpdateViewModel::class.java -> UpdateViewModel(
                    AppUpdater(context.applicationContext), BuildConfig.VERSION_CODE.toLong(), BuildConfig.UPDATES_ENABLED,
                    canInstall = { UpdateInstaller.canInstall(context.applicationContext) },
                    install = { apk -> UpdateInstaller.install(context.applicationContext, apk) },
                    installEvents = UpdateInstaller.events,
                )
                else -> error("Unknown ViewModel: $modelClass")
            } as T
        }
    }
    val library: LibraryViewModel = viewModel(factory = factory)
    val update: UpdateViewModel = viewModel(factory = factory)
    LifecycleEventEffect(Lifecycle.Event.ON_START) { library.refresh(); update.check() }
    if (update.permissionRequest) LaunchedEffect(Unit) {
        update.permissionHandled()
        context.startActivity(UpdateInstaller.permissionIntent(context))
    }
    val study: StudyViewModel = viewModel(factory = factory)
    var selectedUri by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedTitle by rememberSaveable { mutableStateOf("") }
    var selectedTranscript by rememberSaveable { mutableStateOf<String?>(null) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    val uri = selectedUri
    if (showSettings) {
        BackHandler { showSettings = false }
        SettingsScreen(themeSettings.mode, themeSettings::update, update.state, update.checkMessage,
            onCheckUpdate = { update.check(manual = true) }, onUpdate = update::update,
            onBack = { showSettings = false })
    } else if (uri != null) {
        val video = LearningVideo(uri, selectedTitle, selectedTranscript)
        LaunchedEffect(uri) { study.load(video) }
        val leaveStudy = { study.clear(); selectedUri = null }
        BackHandler(onBack = leaveStudy)
        StudyScreen(video, repository, study.state, onBack = leaveStudy)
    } else {
        LibraryScreen(library.state, library::selectRoot, library::refresh, library::download, library::pause,
            onVideo = { video ->
                selectedTitle = video.title
                selectedTranscript = video.transcriptUri
                selectedUri = video.uri
            },
            onSettings = { showSettings = true },
            header = { UpdateCard(update.state, update::update) })
    }
}

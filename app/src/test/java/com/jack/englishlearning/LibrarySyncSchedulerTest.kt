package com.jack.englishlearning

import android.content.Context
import androidx.work.*
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.jack.englishlearning.data.cloud.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class LibrarySyncSchedulerTest {
    private lateinit var context: Context
    private lateinit var manager: WorkManager
    private val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).build()

    @Before fun setup() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("course-downloads", 0).edit().clear().commit()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, Configuration.Builder()
            .setExecutor(SynchronousExecutor()).build())
        manager = WorkManager.getInstance(context)
    }

    @Test fun upgradeCancelsLegacyAutomaticJobsWithoutEnqueuingAnyDownloads() {
        val immediate = OneTimeWorkRequestBuilder<LibrarySyncWorker>().setConstraints(constraints).build()
        val periodic = PeriodicWorkRequestBuilder<LibrarySyncWorker>(6, TimeUnit.HOURS)
            .setInitialDelay(6, TimeUnit.HOURS).setConstraints(constraints).build()
        manager.enqueueUniqueWork("library-sync-now", ExistingWorkPolicy.KEEP, immediate).result.get(10, TimeUnit.SECONDS)
        manager.enqueueUniquePeriodicWork("library-sync-periodic", ExistingPeriodicWorkPolicy.KEEP, periodic).result.get(10, TimeUnit.SECONDS)
        LibrarySyncScheduler(context)
        assertEquals(WorkInfo.State.CANCELLED, manager.getWorkInfoById(immediate.id).get(10, TimeUnit.SECONDS)?.state)
        assertEquals(WorkInfo.State.CANCELLED, manager.getWorkInfoById(periodic.id).get(10, TimeUnit.SECONDS)?.state)
        assertTrue(manager.getWorkInfosByTag(LibrarySyncScheduler.TAG).get(10, TimeUnit.SECONDS).isEmpty())
    }

    @Test fun explicitCourseSelectionIsQueuedAndCanBePausedIndependently() {
        val scheduler = LibrarySyncScheduler(context)
        val lesson = CloudLesson("lesson-one", "Lesson one", "a".repeat(64), 8,
            CloudAsset("manifest.json", 300, "a".repeat(64)))
        scheduler.download(lesson)
        scheduler.download(lesson.copy(id = "lesson-two"))
        val first = manager.getWorkInfosForUniqueWork("course-download:lesson-one").get(10, TimeUnit.SECONDS).single()
        val second = manager.getWorkInfosForUniqueWork("course-download:lesson-two").get(10, TimeUnit.SECONDS).single()
        assertEquals(WorkInfo.State.ENQUEUED, first.state)
        assertEquals(NetworkType.CONNECTED, first.constraints.requiredNetworkType)
        assertEquals(NetworkType.CONNECTED, second.constraints.requiredNetworkType)
        assertTrue(first.constraints.requiresStorageNotLow())
        assertTrue(first.tags.contains("version:${lesson.version}"))
        scheduler.pause(lesson.id)
        assertEquals(WorkInfo.State.CANCELLED, manager.getWorkInfoById(first.id).get(10, TimeUnit.SECONDS)?.state)
        assertEquals(WorkInfo.State.ENQUEUED, manager.getWorkInfoById(second.id).get(10, TimeUnit.SECONDS)?.state)
    }
}

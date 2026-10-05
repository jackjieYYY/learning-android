package com.jack.englishlearning.data.cloud

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.util.concurrent.TimeUnit

class LibrarySyncWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val selected = inputData.getString("lesson") ?: return Result.failure()
        return try {
            val lesson = CloudFormat.catalog(selected).single()
            setForeground(notification("正在下载 · ${lesson.title}"))
            CloudLibrary(applicationContext).download(lesson) { message ->
                setProgress(workDataOf("message" to message))
                setForeground(notification("${lesson.title} · $message"))
            }
            Result.success(workDataOf("message" to "下载完成"))
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (error: Exception) {
            val message = error.message ?: "教材下载失败，请重试。"
            if (error is IOException && runAttemptCount < 3) {
                setProgress(workDataOf("message" to "$message；等待自动重试"))
                Result.retry()
            } else Result.failure(workDataOf("message" to message))
        }
    }

    private fun notification(message: String): ForegroundInfo {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("library-sync", "教材下载", NotificationManager.IMPORTANCE_LOW))
        val notification = NotificationCompat.Builder(applicationContext, "library-sync")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("英语听力")
            .setContentText(message)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "暂停", WorkManager.getInstance(applicationContext).createCancelPendingIntent(id))
            .build()
        val notificationId = id.hashCode() and Int.MAX_VALUE
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ForegroundInfo(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else ForegroundInfo(notificationId, notification)
    }
}

/** Only explicit course selection can enqueue media downloads. No periodic download jobs. */
class LibrarySyncScheduler(context: Context) {
    private val manager = WorkManager.getInstance(context.applicationContext)
    private val prefs = context.getSharedPreferences("course-downloads", Context.MODE_PRIVATE)
    private val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).setRequiresStorageNotLow(true).build()

    init {
        if (!prefs.getBoolean("manual-download-migrated", false)) {
            manager.cancelUniqueWork("library-sync-now")
            manager.cancelUniqueWork("library-sync-periodic")
            prefs.edit().putBoolean("manual-download-migrated", true).apply()
        }
    }

    val work: Flow<List<WorkInfo>> get() = manager.getWorkInfosByTagFlow(TAG).map { infos ->
        infos.filter { info ->
            val course = info.tags.firstOrNull { it.startsWith(COURSE_TAG) }?.removePrefix(COURSE_TAG)
            course != null && prefs.getString("request:$course", null) == info.id.toString()
        }
    }

    fun download(lesson: CloudLesson) {
        val request = OneTimeWorkRequestBuilder<LibrarySyncWorker>()
            .setConstraints(constraints).setInputData(workDataOf("lesson" to CloudFormat.encodeLesson(lesson)))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag(TAG).addTag(COURSE_TAG + lesson.id).addTag("version:${lesson.version}").build()
        prefs.edit().putString("request:${lesson.id}", request.id.toString()).apply()
        manager.enqueueUniqueWork("course-download:${lesson.id}", ExistingWorkPolicy.REPLACE, request)
    }

    fun pause(id: String) { manager.cancelUniqueWork("course-download:$id") }

    companion object {
        const val TAG = "course-download"
        const val COURSE_TAG = "course:"
    }
}

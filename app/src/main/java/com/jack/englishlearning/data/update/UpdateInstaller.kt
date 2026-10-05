package com.jack.englishlearning.data.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

sealed interface InstallEvent {
    data object WaitingForUser : InstallEvent
    data object Installed : InstallEvent
    data object Cancelled : InstallEvent
    data class Failed(val message: String) : InstallEvent
}

/** Self-update through a PackageInstaller session; a successful install replaces and stops this process. */
object UpdateInstaller {
    private const val ACTION_RESULT = "com.jack.englishlearning.UPDATE_INSTALL_RESULT"
    private val mutableEvents = MutableStateFlow<InstallEvent?>(null)
    val events: StateFlow<InstallEvent?> = mutableEvents

    fun canInstall(context: Context) = context.packageManager.canRequestPackageInstalls()

    fun permissionIntent(context: Context) =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))

    fun install(context: Context, apk: File) {
        mutableEvents.value = null
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(apk.length())
            // Android 12+: an app updating itself may skip the confirmation dialog; the system still asks when not allowed.
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val sessionId = installer.createSession(params)
        try {
            installer.openSession(sessionId).use { session ->
                apk.inputStream().use { input ->
                    session.openWrite("base.apk", 0, apk.length()).use { output ->
                        input.copyTo(output, 64 * 1024)
                        session.fsync(output)
                    }
                }
                val intent = Intent(context, InstallResultReceiver::class.java).setAction(ACTION_RESULT).setPackage(context.packageName)
                // The installer fills in status extras, so the explicit PendingIntent must be mutable on Android 12+.
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
                session.commit(PendingIntent.getBroadcast(context, sessionId, intent, flags).intentSender)
            }
        } catch (error: Throwable) {
            installer.abandonSession(sessionId)
            throw error
        }
    }

    internal fun report(event: InstallEvent) { mutableEvents.value = event }

    fun clearEvent() { mutableEvents.value = null }

    fun failureMessage(status: Int, detail: String?): String = when (status) {
        PackageInstaller.STATUS_FAILURE_CONFLICT, PackageInstaller.STATUS_FAILURE_INCOMPATIBLE ->
            "安装失败：新版本签名与当前 App 不一致。请卸载旧版后从 GitHub Releases 手动安装。"
        PackageInstaller.STATUS_FAILURE_STORAGE -> "安装失败：手机空间不足。"
        PackageInstaller.STATUS_FAILURE_BLOCKED -> "安装被系统阻止，请检查是否允许本 App 安装未知应用。"
        PackageInstaller.STATUS_FAILURE_INVALID -> "安装包无效，请重新下载。"
        else -> "安装失败${detail?.let { "：$it" }.orEmpty()}"
    }
}

class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                    else @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_INTENT)
                if (confirm == null) {
                    UpdateInstaller.report(InstallEvent.Failed("无法打开系统安装确认。"))
                    return
                }
                UpdateInstaller.report(InstallEvent.WaitingForUser)
                context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            PackageInstaller.STATUS_SUCCESS -> UpdateInstaller.report(InstallEvent.Installed)
            PackageInstaller.STATUS_FAILURE_ABORTED -> UpdateInstaller.report(InstallEvent.Cancelled)
            else -> UpdateInstaller.report(InstallEvent.Failed(
                UpdateInstaller.failureMessage(status, intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE))))
        }
    }
}

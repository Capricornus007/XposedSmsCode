package com.github.magisk317.smscode.transition

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import com.github.tianma8023.xposed.smscode.BuildConfig
import com.github.magisk317.smscode.common.constant.TransitionConst
import com.github.magisk317.smscode.common.constant.TransitionExpiryPolicy
import com.github.magisk317.smscode.common.utils.XLog
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.TimeUnit

object TransitionExpiryEnforcer {
    private val inProgress = AtomicBoolean(false)

    fun enforceIfNeeded(context: Context, trigger: String) {
        if (!BuildConfig.IS_TRANSITION_BUILD) return
        if (!TransitionExpiryPolicy.isExpired()) return
        if (!inProgress.compareAndSet(false, true)) return

        runCatching {
            val uninstallSuccess = uninstallByRoot(context)
            if (uninstallSuccess) {
                XLog.w(
                    "Transition expiry enforced: root uninstall success reason=%s trigger=%s",
                    TransitionConst.TRANSITION_EXPIRED_REASON_CODE,
                    trigger,
                )
                return@runCatching
            }
            clearDataIfNeeded(context, trigger)
        }.onFailure {
            XLog.e("Transition expiry enforce failed trigger=$trigger", it)
        }

        inProgress.set(false)
    }

    private fun uninstallByRoot(context: Context): Boolean {
        val command = "pm uninstall ${context.packageName}"
        val result = runSuCommand(command)
        return result.exitCode == 0
    }

    private fun clearDataIfNeeded(context: Context, trigger: String) {
        if (!hasUserData(context)) {
            XLog.w(
                "Transition expiry: no user data to clear reason=%s trigger=%s",
                TransitionConst.TRANSITION_EXPIRY_CLEANUP_REASON,
                trigger,
            )
            return
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            XLog.w("Transition expiry: clearApplicationUserData unsupported on API<19")
            return
        }
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return
        val success = am.clearApplicationUserData()
        XLog.w(
            "Transition expiry cleanup result=%s reason=%s trigger=%s",
            success,
            TransitionConst.TRANSITION_EXPIRY_CLEANUP_REASON,
            trigger,
        )
    }

    private fun hasUserData(context: Context): Boolean {
        val dbFile = context.getDatabasePath("xsmscode_room.db")
        if (dbFile.exists() && dbFile.length() > 0L) return true
        val walFile = File("${dbFile.absolutePath}-wal")
        if (walFile.exists() && walFile.length() > 0L) return true
        val sharedPrefs = context.getSharedPreferences("xposed_prefs", Context.MODE_PRIVATE)
        return sharedPrefs.all.any { (key, value) ->
            !key.startsWith("internal_") && value != null
        }
    }

    private fun runSuCommand(command: String): SuCommandResult {
        return try {
            val process = ProcessBuilder("su", "-c", command)
                .redirectErrorStream(true)
                .start()
            val finished = process.waitFor(8, TimeUnit.SECONDS)
            if (!finished) {
                process.destroy()
                if (process.isAlive) process.destroyForcibly()
                return SuCommandResult(exitCode = -2, output = "")
            }
            val output = process.inputStream.bufferedReader().use { it.readText() }
            SuCommandResult(exitCode = process.exitValue(), output = output)
        } catch (_: Throwable) {
            SuCommandResult(exitCode = -1, output = "")
        }
    }

    private data class SuCommandResult(
        val exitCode: Int,
        val output: String,
    )
}

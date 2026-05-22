package com.github.magisk317.smscode.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Process
import com.github.magisk317.smscode.common.constant.PrefConst
import com.github.magisk317.smscode.common.utils.AppPreferencesDataStore
import io.github.magisk317.smscode.xposed.utils.XLog
import kotlinx.coroutines.runBlocking

class KillSelfControlReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_KILL_SELF) {
            XLog.w("KillSelfControlReceiver: ignored unexpected action=%s", intent.action.orEmpty())
            return
        }

        val expectedToken = runBlocking {
            AppPreferencesDataStore.getString(context, PrefConst.KEY_IPC_TOKEN, "")
        }
        val token = intent.getStringExtra(EXTRA_IPC_TOKEN).orEmpty()
        if (expectedToken.isBlank() || token.isBlank() || token != expectedToken) {
            XLog.w("KillSelfControlReceiver: rejected kill request due to invalid token")
            return
        }

        val delayMs = normalizeDelayMs(intent.getLongExtra(EXTRA_DELAY_MS, DEFAULT_DELAY_MS))
        val processName = context.applicationInfo?.processName ?: context.packageName
        XLog.w(
            "KillSelfControlReceiver: kill self requested, delay=%dms pid=%d process=%s",
            delayMs,
            Process.myPid(),
            processName,
        )

        Thread {
            try {
                Thread.sleep(delayMs)
            } catch (_: InterruptedException) {
                // ignore
            }
            XLog.w(
                "KillSelfControlReceiver: killing pid=%d process=%s",
                Process.myPid(),
                processName,
            )
            try {
                Process.killProcess(Process.myPid())
            } finally {
                runCatching { System.exit(0) }
            }
        }.start()
    }

    companion object {
        const val ACTION_KILL_SELF = "com.github.magisk317.smscode.ACTION_KILL_SELF"
        const val EXTRA_DELAY_MS = "delay_ms"
        const val EXTRA_IPC_TOKEN = "ipc_token"
        private const val DEFAULT_DELAY_MS = 80L
        private const val MAX_DELAY_MS = 5_000L

        fun normalizeDelayMs(value: Long): Long = value.coerceIn(0L, MAX_DELAY_MS)
    }
}

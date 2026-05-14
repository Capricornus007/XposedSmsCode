package com.github.magisk317.smscode.xp.hook.code.action.impl

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.github.magisk317.smscode.receiver.KillSelfControlReceiver
import com.github.magisk317.smscode.runtime.RuntimePrefsFacade as PrefsReader
import io.github.magisk317.smscode.xposed.hook.system.SystemInputInjectorHook
import io.github.magisk317.smscode.xposed.utils.XLog
import com.github.magisk317.smscode.data.db.entity.SmsMsg
import com.github.magisk317.smscode.xp.hook.code.action.CallableAction

class KillMeAction(
    pluginContext: Context,
    phoneContext: Context,
    smsMsg: SmsMsg,
    private val attemptId: Long? = null,
    private val notificationAutoCancelDelayMs: Long? = null,
) : CallableAction(pluginContext, phoneContext, smsMsg) {

    private val mHandler = Handler(Looper.getMainLooper())
    private var mResultReceiver: BroadcastReceiver? = null
    private var mTimeoutRunnable: Runnable? = null
    private val mKillLock = Any()

    override fun action(): Bundle? {
        if (attemptId != null) {
            waitForAutoInputResult()
        } else {
            killMe()
        }
        return null
    }

    private fun waitForAutoInputResult() {
        mResultReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action != SystemInputInjectorHook.resolveActionAutoInputResult()) return
                if (intent.getLongExtra("attemptId", -1L) != attemptId) return

                val success = intent.getBooleanExtra("success", false)
                XLog.w(
                    "KillMeAction: auto-input result received: attemptId=%d success=%s reason=%s",
                    attemptId,
                    success,
                    intent.getStringExtra("reason").orEmpty().ifBlank { "n/a" },
                )
                handleAutoInputResult()
            }
        }

        mHandler.post {
            synchronized(mKillLock) {
                val receiver = mResultReceiver ?: return@post
                runCatching {
                    val filter = IntentFilter(SystemInputInjectorHook.resolveActionAutoInputResult())
                    mPhoneContext.registerReceiver(receiver, filter, ContextCompat.RECEIVER_EXPORTED)
                    val timeout = Runnable { handleAutoInputTimeout(receiver) }
                    mTimeoutRunnable = timeout
                    mHandler.postDelayed(timeout, KILL_RESULT_TIMEOUT_MS)
                }.onFailure { error ->
                    XLog.w(
                        "KillMeAction: result receiver registration failed: %s",
                        error.message ?: error.javaClass.simpleName,
                    )
                    proceedWithKillChain()
                }
            }
        }
    }

    private fun handleAutoInputResult() {
        val receiver = synchronized(mKillLock) {
            mResultReceiver.also { mResultReceiver = null }
        } ?: return

        mTimeoutRunnable?.let { mHandler.removeCallbacks(it) }
        mTimeoutRunnable = null
        runCatching { mPhoneContext.unregisterReceiver(receiver) }
            .onFailure { XLog.w("KillMeAction: unregister result receiver failed") }

        proceedWithKillChain()
    }

    private fun handleAutoInputTimeout(receiver: BroadcastReceiver) {
        val removed = synchronized(mKillLock) {
            mResultReceiver.also { mResultReceiver = null }
        } ?: return

        mTimeoutRunnable = null
        runCatching { mPhoneContext.unregisterReceiver(removed) }
            .onFailure { XLog.w("KillMeAction: unregister timed-out receiver failed") }

        XLog.w("KillMeAction: auto-input result timeout (%d ms), proceeding with kill", KILL_RESULT_TIMEOUT_MS)
        proceedWithKillChain()
    }

    private fun proceedWithKillChain() {
        val cancelDelay = notificationAutoCancelDelayMs?.takeIf { it > 0L }
        if (cancelDelay != null) {
            val extraBuffer = NOTIFICATION_CANCEL_BUFFER_MS
            val totalDelay = cancelDelay + extraBuffer
            mHandler.postDelayed({ killMe() }, totalDelay)
            XLog.w("KillMeAction: waiting for notification auto-cancel (%d ms + %d ms buffer) before kill", cancelDelay, extraBuffer)
        } else {
            killMe()
        }
    }

    private fun killMe() {
        if (!PrefsReader.killMeEnabled(mPluginContext)) return
        if (requestSelfKillPrimary()) {
            return
        }
        XLog.w("KillMeAction: provider self-kill failed for process %s", mPhoneContext.packageName)
    }

    private fun requestSelfKillPrimary(): Boolean {
        return try {
            val token = PrefsReader.getIpcToken(mPluginContext)
            if (token.isBlank()) return false
            val intent = Intent(KillSelfControlReceiver.ACTION_KILL_SELF).apply {
                setClassName(mPluginContext.packageName, KillSelfControlReceiver::class.java.name)
                addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                putExtra(KillSelfControlReceiver.EXTRA_DELAY_MS, 80L)
                putExtra(KillSelfControlReceiver.EXTRA_IPC_TOKEN, token)
            }
            mPluginContext.sendBroadcast(intent)
            XLog.w("KillMeAction primary requested via KillSelfControlReceiver")
            true
        } catch (e: Throwable) {
            XLog.w("KillMeAction primary failed: %s", e.message ?: e.javaClass.simpleName)
            false
        }
    }

    private companion object {
        private const val KILL_RESULT_TIMEOUT_MS = 8_000L
        private const val NOTIFICATION_CANCEL_BUFFER_MS = 1_500L
    }
}

package com.github.magisk317.smscode.transition

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.github.tianma8023.xposed.smscode.BuildConfig
import com.github.magisk317.smscode.common.utils.XLog

class TransitionExpiryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!BuildConfig.IS_TRANSITION_BUILD) return
        val action = intent.action.orEmpty()
        XLog.w("TransitionExpiryReceiver action=%s", action)
        TransitionExpiryScheduler.scheduleDailyCheck(context)
        TransitionExpiryEnforcer.enforceIfNeeded(context, trigger = action.ifBlank { "receiver" })
    }

    companion object {
        const val ACTION_DAILY_CHECK = "com.github.tianma8023.xposed.smscode.ACTION_DAILY_EXPIRY_CHECK"
    }
}

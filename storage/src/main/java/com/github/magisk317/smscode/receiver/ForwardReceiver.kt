package com.github.magisk317.smscode.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.github.magisk317.smscode.forwarder.entity.MsgInfo
import com.github.magisk317.smscode.forwarder.utils.SendUtils
import com.tianma.xsmscode.common.constant.PrefConst
import com.tianma.xsmscode.common.utils.XLog
import kotlinx.coroutines.runBlocking
class ForwardReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        Thread {
            try {
                // 1. Action validation
                if (intent.action != PrefConst.ACTION_FORWARD_SMS) {
                    XLog.e("Rejecting broadcast with invalid action: %s", intent.action)
                    return@Thread
                }

                val sender = intent.getStringExtra("sender")
                val body = intent.getStringExtra("body")
                val date = intent.getLongExtra("date", 0L)
                val company = intent.getStringExtra("company")
                val smsCode = intent.getStringExtra("smsCode")
                val receivedToken = intent.getStringExtra("ipc_token")

                // 2. Verifying IPC Token: prevent third-party apps from spoofing broadcasts.
                // We retrieve local token from DataStore (which is synced to xposed_prefs).
                val expectedToken = runBlocking {
                    com.tianma.xsmscode.common.utils.AppPreferencesDataStore.getString(
                        context,
                        com.tianma.xsmscode.common.constant.PrefConst.KEY_IPC_TOKEN,
                        "",
                    )
                }

                if (expectedToken.isEmpty() || receivedToken != expectedToken) {
                    XLog.e("IPC Token mismatch! Security breach attempt or uninitialized token. Rejecting broadcast.")
                    return@Thread
                }

                XLog.i("IPC verified and received message from: %s", sender ?: "")

                val msgInfo = MsgInfo(
                    type = "sms",
                    from = sender ?: "",
                    content = body ?: "",
                    date = java.util.Date(date),
                    simInfo = company ?: "",
                )

                // Dispatch to the multi-channel forwarding engine.
                // isCodeSms: true = verification code SMS, false = regular SMS.
                // SendUtils will use this to filter per-sender receiveNonCode setting.
                val isCodeSms = !smsCode.isNullOrBlank()
                SendUtils.sendMsg(context, msgInfo, isCodeSms)
            } finally {
                pendingResult.finish()
            }
        }.start()
    }

    companion object {
        private const val TAG = "ForwardReceiver"
    }
}

package com.tianma.xsmscode.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.tianma.xsmscode.common.utils.ModuleActivationStore
import com.tianma.xsmscode.common.utils.ModuleUtils
import com.tianma.xsmscode.common.utils.XLog
import com.tianma.xsmscode.xp.hook.code.CodeWorker

/**
 * Non-Xposed SMS entry for normal app mode.
 * In Xposed-active environments we skip this path to avoid duplicate processing.
 */
class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val xposedActive = ModuleUtils.isModuleEnabled() || ModuleActivationStore.isActivatedRecently(context)
        if (xposedActive) {
            XLog.i("SmsReceiver skipped: Xposed path is active")
            return
        }

        val pendingResult = goAsync()
        Thread {
            try {
                val appContext = context.applicationContext
                val parseResult = CodeWorker(appContext, appContext, intent).parse()
                if (parseResult?.isBlockSms == true && isOrderedBroadcast) {
                    abortBroadcast()
                }
            } catch (e: Exception) {
                XLog.e("SmsReceiver handle SMS failed", e)
            } finally {
                pendingResult.finish()
            }
        }.start()
    }
}

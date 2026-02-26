package com.github.magisk317.smscode.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.github.magisk317.smscode.forwarder.entity.MsgInfo
import com.github.magisk317.smscode.forwarder.utils.SendUtils
import com.github.magisk317.smscode.forwarder.utils.SourceMetadataResolver
import com.tianma.xsmscode.common.constant.PrefConst
import com.tianma.xsmscode.common.utils.XLog
import android.telephony.SubscriptionManager
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
                val packageName = intent.getStringExtra("packageName")
                val receivedToken = intent.getStringExtra("ipc_token")
                val subId = readIntExtra(
                    intent,
                    "sub_id",
                    "subscription",
                    "subscription_id",
                    "android.telephony.extra.SUBSCRIPTION_INDEX",
                    "android.telephony.extra.SUBSCRIPTION_ID",
                )
                val rawSlot = readIntExtra(
                    intent,
                    "sim_slot",
                    "slot",
                    "simId",
                    "sim_id",
                    "simSlot",
                    "android.telephony.extra.SLOT_INDEX",
                )

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
                val normalizedSubId = subId ?: 0
                val resolvedSimSlot = resolveSimSlot(rawSlot, normalizedSubId)
                val contactName = SourceMetadataResolver.resolveContactName(context, sender ?: "")
                val phoneArea = SourceMetadataResolver.resolvePhoneArea(sender ?: "")
                XLog.i(
                    "Resolved metadata: sim_slot=%d sub_id=%d contact=%s area=%s",
                    resolvedSimSlot,
                    normalizedSubId,
                    contactName.ifBlank { "<empty>" },
                    phoneArea.ifBlank { "<empty>" },
                )

                val msgInfo = MsgInfo(
                    type = "sms",
                    from = sender ?: "",
                    content = body ?: "",
                    date = java.util.Date(date),
                    simInfo = company ?: "",
                    simSlot = resolvedSimSlot,
                    subId = normalizedSubId,
                    packageName = packageName ?: "",
                    contactName = contactName,
                    phoneArea = phoneArea,
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

    private fun resolveSimSlot(rawSlot: Int?, subId: Int): Int {
        if (subId > 0) {
            val slotFromSubId = runCatching { SubscriptionManager.getSlotIndex(subId) }.getOrDefault(-1)
            if (slotFromSubId >= 0) return slotFromSubId
        }
        val slot = rawSlot ?: return -1
        return when {
            slot in 0..1 -> slot
            slot == 2 -> 1
            else -> -1
        }
    }

    private fun readIntExtra(intent: Intent, vararg keys: String): Int? {
        for (key in keys) {
            if (!intent.hasExtra(key)) continue
            val intValue = intent.getIntExtra(key, Int.MIN_VALUE)
            if (intValue != Int.MIN_VALUE) return intValue
            val longValue = intent.getLongExtra(key, Long.MIN_VALUE)
            if (longValue != Long.MIN_VALUE) return longValue.toInt()
            intent.getStringExtra(key)?.toIntOrNull()?.let { return it }
        }
        return null
    }
}

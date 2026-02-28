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
import java.util.concurrent.ConcurrentHashMap

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
                val msgTypeStr = intent.getStringExtra("msgType") ?: "sms"
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
                if (msgTypeStr == "app_notify" && shouldDropDuplicateAppNotify(packageName, sender, body)) {
                    XLog.i(
                        "Drop duplicate app_notify: pkg=%s sender=%s",
                        packageName.orEmpty(),
                        sender.orEmpty(),
                    )
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

                val smsMsgType = if (msgTypeStr == "app_notify") com.tianma.xsmscode.data.db.entity.SmsMsg.MSG_TYPE_APP_NOTIFY else com.tianma.xsmscode.data.db.entity.SmsMsg.MSG_TYPE_SMS
                val msgInfo = MsgInfo(
                    type = msgTypeStr,
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
                var recordId: Long? = null

                if (msgTypeStr == "app_notify") {
                    try {
                        val smsMsgUri = com.tianma.xsmscode.data.db.DBProvider.SMS_MSG_CONTENT_URI
                        val resolver = context.contentResolver
                        val values = android.content.ContentValues().apply {
                            put("body", msgInfo.content)
                            put("company", msgInfo.simInfo)
                            put("date", msgInfo.date.time)
                            put("sender", msgInfo.from)
                            put("package_name", msgInfo.packageName)
                            put("msg_type", smsMsgType)
                        }
                        // Remove outdated records
                        val cursor = resolver.query(smsMsgUri, arrayOf("_id"), null, null, "date ASC")
                        if (cursor != null) {
                            val count = cursor.count
                            val limit = runBlocking {
                                com.tianma.xsmscode.common.utils.AppPreferencesDataStore.getString(
                                    context,
                                    com.tianma.xsmscode.common.constant.PrefConst.KEY_HISTORY_LIMIT,
                                    "0",
                                ).toIntOrNull() ?: 0
                            }
                            if (limit > 0 && count >= limit) {
                                val selection = "_id = ?"
                                val operations = ArrayList<android.content.ContentProviderOperation>()
                                for (i in 0 until (count - limit + 1)) {
                                    if (cursor.moveToNext()) {
                                        val id = cursor.getLong(0)
                                        val operation = android.content.ContentProviderOperation.newDelete(smsMsgUri)
                                            .withSelection(selection, arrayOf(id.toString()))
                                            .build()
                                        operations.add(operation)
                                    }
                                }
                                resolver.applyBatch(com.tianma.xsmscode.data.db.DBProvider.AUTHORITY, operations)
                            }
                            cursor.close()
                        }
                        recordId = resolver.insert(smsMsgUri, values)?.lastPathSegment?.toLongOrNull()
                    } catch (e: Exception) {
                        XLog.e("Failed to record app notification to DB", e)
                    }
                } else {
                    recordId = findRecordIdByFingerprint(
                        context = context,
                        sender = msgInfo.from,
                        body = msgInfo.content,
                        date = msgInfo.date.time,
                        msgType = smsMsgType,
                    )
                }
                if (recordId == null) {
                    recordId = findRecordIdByFingerprint(
                        context = context,
                        sender = msgInfo.from,
                        body = msgInfo.content,
                        date = msgInfo.date.time,
                        msgType = smsMsgType,
                    )
                }

                // Dispatch to the multi-channel forwarding engine.
                // isCodeSms: true = verification code SMS, false = regular SMS.
                // SendUtils will use this to filter per-sender receiveNonCode setting.
                val isCodeSms = !smsCode.isNullOrBlank()
                SendUtils.sendMsg(context, msgInfo, isCodeSms, recordId)
            } finally {
                pendingResult.finish()
            }
        }.start()
    }

    companion object {
        private const val TAG = "ForwardReceiver"
        private const val APP_NOTIFY_DEDUP_WINDOW_MS = 2500L
        private const val APP_NOTIFY_DEDUP_MAX_ENTRIES = 256
        private val recentAppNotify = ConcurrentHashMap<String, Long>()
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

    private fun shouldDropDuplicateAppNotify(
        packageName: String?,
        sender: String?,
        body: String?,
    ): Boolean {
        val key = buildString {
            append(packageName.orEmpty().trim())
            append('|')
            append(sender.orEmpty().trim())
            append('|')
            append(body.orEmpty().trim())
        }
        if (key == "||") return false

        val now = System.currentTimeMillis()
        val previous = recentAppNotify[key]
        if (previous != null && now - previous < APP_NOTIFY_DEDUP_WINDOW_MS) {
            return true
        }
        recentAppNotify[key] = now

        if (recentAppNotify.size > APP_NOTIFY_DEDUP_MAX_ENTRIES) {
            val cutoff = now - APP_NOTIFY_DEDUP_WINDOW_MS * 2
            recentAppNotify.entries.removeIf { it.value < cutoff }
        }
        return false
    }

    private fun findRecordIdByFingerprint(
        context: Context,
        sender: String,
        body: String,
        date: Long,
        msgType: Int,
    ): Long? {
        val resolver = context.contentResolver
        val smsMsgUri = com.tianma.xsmscode.data.db.DBProvider.SMS_MSG_CONTENT_URI
        val projection = arrayOf("_id", "sender", "body", "date", "msg_type")
        return try {
            resolver.query(smsMsgUri, projection, null, null, "date DESC")?.use { cursor ->
                val idIdx = cursor.getColumnIndex("_id")
                val senderIdx = cursor.getColumnIndex("sender")
                val bodyIdx = cursor.getColumnIndex("body")
                val dateIdx = cursor.getColumnIndex("date")
                val msgTypeIdx = cursor.getColumnIndex("msg_type")
                while (cursor.moveToNext()) {
                    val senderValue = if (senderIdx >= 0) cursor.getString(senderIdx) else null
                    val bodyValue = if (bodyIdx >= 0) cursor.getString(bodyIdx) else null
                    val dateValue = if (dateIdx >= 0) cursor.getLong(dateIdx) else -1L
                    val msgTypeValue = if (msgTypeIdx >= 0) cursor.getInt(msgTypeIdx) else com.tianma.xsmscode.data.db.entity.SmsMsg.MSG_TYPE_SMS
                    if (senderValue == sender && bodyValue == body && dateValue == date && msgTypeValue == msgType) {
                        return if (idIdx >= 0) cursor.getLong(idIdx) else null
                    }
                }
                null
            }
        } catch (t: Throwable) {
            XLog.w("findRecordIdByFingerprint failed: %s", t.message ?: t.javaClass.simpleName)
            null
        }
    }
}

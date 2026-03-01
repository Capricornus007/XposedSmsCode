package com.github.magisk317.smscode.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import com.github.magisk317.smscode.forwarder.entity.MsgInfo
import com.github.magisk317.smscode.forwarder.utils.SendUtils
import com.github.magisk317.smscode.forwarder.utils.SourceMetadataResolver
import com.tianma.xsmscode.common.constant.PrefConst
import com.tianma.xsmscode.common.utils.ForwardFlowLog
import com.tianma.xsmscode.common.utils.XLog
import com.tianma.xsmscode.data.db.DBManager
import android.telephony.SubscriptionManager
import kotlinx.coroutines.runBlocking
import java.util.concurrent.ConcurrentHashMap

class ForwardReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val ordered = isOrderedBroadcast
        val pendingResult = goAsync()
        val eventId = intent.getStringExtra("event_id").orEmpty()
        val traceId = buildTraceId(intent, eventId)
        Thread {
            fun markResult(code: Int, reason: String) {
                setOrderedResult(pendingResult, ordered, code, reason, eventId)
            }
            try {
                ForwardFlowLog.i(traceId, "ForwardReceiver onReceive action=${intent.action} event=${eventId.ifBlank { "<none>" }} ordered=$ordered")
                // 1. Action validation
                if (intent.action != PrefConst.ACTION_FORWARD_SMS) {
                    XLog.e("Rejecting broadcast with invalid action: %s", intent.action)
                    ForwardFlowLog.w(traceId, "Reject invalid action=${intent.action}")
                    markResult(RESULT_REJECT_ACTION, "invalid_action")
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
                val forwardSource = intent.getStringExtra("forward_source") ?: "unknown"
                val sentFromUid = resolveSentFromUidCompat()
                val sentFromPkg = resolveSentFromPackageCompat()
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
                val tokenMatched = expectedToken.isNotEmpty() && receivedToken == expectedToken
                val allowSystemBypass = shouldAllowSystemTokenBypass(
                    msgType = msgTypeStr,
                    forwardSource = forwardSource,
                    sentFromUid = sentFromUid,
                )

                if (!tokenMatched && !allowSystemBypass) {
                    XLog.e("IPC Token mismatch! Security breach attempt or uninitialized token. Rejecting broadcast.")
                    ForwardFlowLog.w(
                        traceId,
                        "Reject token mismatch event=${eventId.ifBlank { "<none>" }} pkg=${packageName.orEmpty()} expectedEmpty=${expectedToken.isEmpty()} receivedEmpty=${receivedToken.isNullOrBlank()} source=$forwardSource sentFromUid=${sentFromUid ?: -1} sentFromPkg=${sentFromPkg ?: "<none>"}",
                    )
                    markResult(RESULT_REJECT_TOKEN, "token_mismatch")
                    return@Thread
                }
                if (!tokenMatched && allowSystemBypass) {
                    XLog.w(
                        "IPC token unavailable, accepted by system bypass. source=%s msgType=%s sentFromUid=%d sentFromPkg=%s",
                        forwardSource,
                        msgTypeStr,
                        sentFromUid ?: -1,
                        sentFromPkg ?: "<none>",
                    )
                    ForwardFlowLog.w(
                        traceId,
                        "Token bypass accepted source=$forwardSource msgType=$msgTypeStr sentFromUid=${sentFromUid ?: -1} sentFromPkg=${sentFromPkg ?: "<none>"}",
                    )
                }
                if (msgTypeStr == "app_notify" && !shouldForwardAppNotify(context, packageName, traceId, forwardSource)) {
                    markResult(RESULT_REJECT_APP_GATE, "app_gate_drop")
                    return@Thread
                }
                if (msgTypeStr == "app_notify" && shouldDropDuplicateAppNotify(packageName, sender, body)) {
                    XLog.i(
                        "Drop duplicate app_notify: pkg=%s sender=%s source=%s",
                        packageName.orEmpty(),
                        sender.orEmpty(),
                        forwardSource,
                    )
                    ForwardFlowLog.i(
                        traceId,
                        "Drop duplicate app_notify pkg=${packageName.orEmpty()} sender=${sender.orEmpty()} source=$forwardSource",
                    )
                    markResult(RESULT_DROP_DUPLICATE, "duplicate_drop")
                    return@Thread
                }

                XLog.i("IPC verified and received message from: %s", sender ?: "")
                ForwardFlowLog.i(
                    traceId,
                    "IPC verified event=${eventId.ifBlank { "<none>" }} type=$msgTypeStr pkg=${packageName.orEmpty()} sender=${sender.orEmpty()} bodyLen=${body?.length ?: 0} source=$forwardSource sentFromUid=${sentFromUid ?: -1} sentFromPkg=${sentFromPkg ?: "<none>"}",
                )
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
                ForwardFlowLog.d(
                    traceId,
                    "Resolved metadata simSlot=$resolvedSimSlot subId=$normalizedSubId contact=${contactName.ifBlank { "<empty>" }} area=${phoneArea.ifBlank { "<empty>" }}",
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
                        ForwardFlowLog.e(traceId, "Record app_notify failed", e)
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
                ForwardFlowLog.i(
                    traceId,
                    "Ready to dispatch event=${eventId.ifBlank { "<none>" }} msgType=$msgTypeStr recordId=${recordId ?: -1} isCodeSms=${!smsCode.isNullOrBlank()} source=$forwardSource final_decision=forward",
                )

                // Dispatch to the multi-channel forwarding engine.
                // isCodeSms: true = verification code SMS, false = regular SMS.
                // SendUtils will use this to filter per-sender receiveNonCode setting.
                val isCodeSms = !smsCode.isNullOrBlank()
                try {
                    SendUtils.sendMsg(context, msgInfo, isCodeSms, recordId, traceId)
                    markResult(RESULT_OK, "forward_dispatched")
                } catch (t: Throwable) {
                    ForwardFlowLog.e(
                        traceId,
                        "Dispatch failed event=${eventId.ifBlank { "<none>" }} source=$forwardSource pkg=${packageName.orEmpty()}",
                        t,
                    )
                    markResult(RESULT_DISPATCH_FAILED, "dispatch_failed")
                }
            } finally {
                ForwardFlowLog.d(traceId, "ForwardReceiver finished")
                pendingResult.finish()
            }
        }.start()
    }

    companion object {
        private const val TAG = "ForwardReceiver"
        private const val APP_NOTIFY_DEDUP_WINDOW_MS = 2500L
        private const val APP_NOTIFY_DEDUP_MAX_ENTRIES = 256
        private const val RESULT_OK = 0
        private const val RESULT_REJECT_ACTION = -101
        private const val RESULT_REJECT_TOKEN = -102
        private const val RESULT_REJECT_APP_GATE = -103
        private const val RESULT_DROP_DUPLICATE = -104
        private const val RESULT_DISPATCH_FAILED = -105
        private val recentAppNotify = ConcurrentHashMap<String, Long>()
    }

    private fun shouldAllowSystemTokenBypass(
        msgType: String,
        forwardSource: String,
        sentFromUid: Int?,
    ): Boolean {
        // Keep strict token verification for all regular channels.
        // Only allow NotificationManagerHook(system_server) app notification as a fallback.
        return msgType == "app_notify" &&
            forwardSource == "nms_hook" &&
            sentFromUid == Process.SYSTEM_UID
    }

    private fun resolveSentFromUidCompat(): Int? {
        if (Build.VERSION.SDK_INT < 34) return null
        return try {
            getSentFromUid()
        } catch (_: Throwable) {
            null
        }
    }

    private fun resolveSentFromPackageCompat(): String? {
        if (Build.VERSION.SDK_INT < 34) return null
        return try {
            getSentFromPackage()
        } catch (_: Throwable) {
            null
        }
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

    private fun shouldForwardAppNotify(
        context: Context,
        packageName: String?,
        traceId: String,
        forwardSource: String,
    ): Boolean {
        val pkg = packageName.orEmpty().trim()
        if (pkg.isEmpty()) {
            ForwardFlowLog.w(
                traceId,
                "App notify gate pkg=<empty> source=$forwardSource final_decision=drop reason=empty_package",
            )
            XLog.w("App notify gate: empty package source=%s final_decision=drop", forwardSource)
            return false
        }
        return try {
            val appInfo = DBManager.get(context).queryAppInfoByPackageName(pkg)
            val enabled = appInfo?.forwarding == true
            val state = when {
                appInfo == null -> "missing"
                enabled -> "enabled"
                else -> "disabled"
            }
            val finalDecision = if (enabled) "forward" else "drop"
            ForwardFlowLog.i(
                traceId,
                "App notify gate pkg=$pkg source=$forwardSource state=$state final_decision=$finalDecision",
            )
            XLog.d(
                "App notify gate: pkg=%s source=%s state=%s final_decision=%s",
                pkg,
                forwardSource,
                state,
                finalDecision,
            )
            enabled
        } catch (t: Throwable) {
            ForwardFlowLog.e(
                traceId,
                "App notify gate query failed pkg=$pkg source=$forwardSource final_decision=drop",
                t,
            )
            XLog.e("App notify gate query failed: pkg=$pkg source=$forwardSource", t)
            false
        }
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

    private fun setOrderedResult(
        pendingResult: PendingResult,
        ordered: Boolean,
        code: Int,
        reason: String,
        eventId: String,
    ) {
        if (!ordered) return
        runCatching {
            pendingResult.setResultCode(code)
            pendingResult.setResultData("reason=$reason;event_id=${eventId.ifBlank { "<none>" }}")
        }
    }

    private fun buildTraceId(intent: Intent, eventId: String): String {
        if (eventId.isNotBlank()) return eventId
        val pkg = intent.getStringExtra("packageName") ?: "unknown"
        val now = System.currentTimeMillis().toString(36)
        val suffix = kotlin.math.abs((pkg + now).hashCode()).toString(36)
        return "${now}_$suffix"
    }
}

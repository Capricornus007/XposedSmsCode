package com.github.magisk317.smscode.xp.hook.code

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import com.github.magisk317.smscode.common.utils.SmsCodeUtils
import com.github.magisk317.smscode.common.utils.StringUtils
import com.github.magisk317.smscode.common.utils.XLog
import java.util.Collections
import java.util.LinkedHashSet
import java.util.concurrent.Executors
import kotlinx.coroutines.runBlocking

internal class SmsInboxObserver(
    private val pluginContext: Context,
    private val phoneContext: Context,
) {
    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            onChange(selfChange, null)
        }

        override fun onChange(selfChange: Boolean, uri: android.net.Uri?) {
            queryExecutor.execute { scanRecentInbox(uri?.toString().orEmpty()) }
        }
    }

    fun register() {
        runCatching {
            phoneContext.contentResolver.registerContentObserver(Telephony.Sms.CONTENT_URI, true, observer)
            XLog.i("SmsInboxObserver registered")
        }.onFailure {
            XLog.w("SmsInboxObserver register failed: %s", it.message ?: it.javaClass.simpleName)
        }
    }

    private fun scanRecentInbox(triggerUri: String) {
        val cutoff = System.currentTimeMillis() - RECENT_SMS_WINDOW_MS
        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.TYPE,
            Telephony.Sms.READ,
        )
        val selection = "${Telephony.Sms.TYPE}=? AND ${Telephony.Sms.DATE}>?"
        val selectionArgs = arrayOf(Telephony.Sms.MESSAGE_TYPE_INBOX.toString(), cutoff.toString())
        val sortOrder = "${Telephony.Sms.DATE} DESC limit $MAX_RECENT_SMS_COUNT"
        runCatching {
            phoneContext.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder,
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val smsId = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Sms._ID))
                    if (!markSeen(smsId)) continue
                    val body = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)).orEmpty()
                    val code = runBlocking { SmsCodeUtils.parseSmsCodeIfExists(pluginContext, body) }.orEmpty()
                    if (code.isBlank()) continue
                    val sender = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)).orEmpty()
                    val date = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Sms.DATE))
                    val read = cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Sms.READ)) != 0
                    XLog.w(
                        "Diag SMS provider observed: sms_id=%d trigger_uri=%s sender_hash=%s date=%d read=%s code=%s body=%s",
                        smsId,
                        triggerUri.ifBlank { Telephony.Sms.CONTENT_URI.toString() },
                        senderHash(sender),
                        date,
                        read,
                        StringUtils.escape(code),
                        StringUtils.escape(body),
                    )
                }
            }
        }.onFailure {
            XLog.w("SmsInboxObserver scan failed: %s", it.message ?: it.javaClass.simpleName)
        }
    }

    private fun markSeen(smsId: Long): Boolean = synchronized(recentSmsIds) {
        if (!recentSmsIds.add(smsId)) {
            return false
        }
        while (recentSmsIds.size > MAX_TRACKED_SMS_IDS) {
            val first = recentSmsIds.firstOrNull() ?: break
            recentSmsIds.remove(first)
        }
        true
    }

    private fun senderHash(sender: String): String {
        if (sender.isBlank()) return "none"
        return Integer.toHexString(sender.hashCode())
    }

    companion object {
        private const val RECENT_SMS_WINDOW_MS = 10 * 60 * 1000L
        private const val MAX_RECENT_SMS_COUNT = 32
        private const val MAX_TRACKED_SMS_IDS = 128
        private val queryExecutor = Executors.newSingleThreadExecutor()
        private val recentSmsIds = Collections.synchronizedSet(LinkedHashSet<Long>())
    }
}

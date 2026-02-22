package com.tianma.xsmscode.xp.hook.code.action.impl

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import android.provider.Telephony
import androidx.annotation.IntDef
import androidx.core.content.ContextCompat
import com.tianma.xsmscode.common.utils.PrefsReader
import com.tianma.xsmscode.common.utils.XLog
import com.tianma.xsmscode.data.db.entity.SmsMsg
import com.tianma.xsmscode.xp.hook.code.action.CallableAction

/**
 * 将验证码短信删除或者标记为已读
 */
class OperateSmsAction(pluginContext: Context, phoneContext: Context, smsMsg: SmsMsg) :
    CallableAction(pluginContext, phoneContext, smsMsg) {

    @IntDef(OP_DELETE, OP_MARK_AS_READ)
    @Retention(AnnotationRetention.SOURCE)
    private annotation class SmsOp

    override fun action(): Bundle? {
        val sender = mSmsMsg.sender
        val body = mSmsMsg.body
        if (PrefsReader.deleteSmsEnabled(mPluginContext)) {
            deleteSms(sender, body)
        } else if (PrefsReader.markAsReadEnabled(mPluginContext)) {
            markSmsAsRead(sender, body)
        }
        return null
    }

    private fun markSmsAsRead(sender: String?, body: String?) {
        XLog.d("Marking SMS as read...")
        val result = operateSmsWithRetry(sender, body, OP_MARK_AS_READ)
        if (result) {
            XLog.i("Mark SMS as read succeed")
        } else {
            XLog.i("Mark SMS as read failed")
        }
    }

    private fun deleteSms(sender: String?, body: String?) {
        XLog.d("Deleting SMS...")
        val result = operateSmsWithRetry(sender, body, OP_DELETE)
        if (result) {
            XLog.i("Delete SMS succeed")
        } else {
            XLog.i("Delete SMS failed")
        }
    }

    private fun operateSmsWithRetry(sender: String?, body: String?, @SmsOp smsOp: Int): Boolean {
        repeat(SMS_OP_RETRY_TIMES) { attempt ->
            if (operateSms(sender, body, smsOp)) {
                return true
            }
            if (attempt < SMS_OP_RETRY_TIMES - 1) {
                SystemClock.sleep(SMS_OP_RETRY_DELAY_MS)
            }
        }
        return false
    }

    private fun operateSms(sender: String?, body: String?, @SmsOp smsOp: Int): Boolean {
        var cursor: android.database.Cursor? = null
        try {
            if (ContextCompat.checkSelfPermission(mPhoneContext, Manifest.permission.READ_SMS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                XLog.e("Don't have permission to read/write sms")
                return false
            }
            val projection = arrayOf(
                Telephony.Sms._ID,
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.READ,
                Telephony.Sms.THREAD_ID,
                Telephony.Sms.DATE,
            )
            // 查看最近短信并匹配目标
            val sortOrder = Telephony.Sms.DATE + " desc limit 20"
            val uri = Telephony.Sms.CONTENT_URI
            // Use phone context to keep telephony provider access in telephony process identity.
            val resolver = mPhoneContext.contentResolver
            cursor = resolver.query(uri, projection, null, null, sortOrder)
            if (cursor == null) {
                XLog.d("Cursor is null")
                return false
            }
            while (cursor.moveToNext()) {
                val curAddress = cursor.getString(cursor.getColumnIndexOrThrow("address"))
                val curRead = cursor.getInt(cursor.getColumnIndexOrThrow("read"))
                val curBody = cursor.getString(cursor.getColumnIndexOrThrow("body"))
                val senderMatched = isAddressMatched(curAddress, sender)
                val bodyMatched = isBodyMatched(curBody, body)
                if (!senderMatched || !bodyMatched) {
                    continue
                }
                if (smsOp == OP_MARK_AS_READ && curRead != 0) {
                    // Treat already-read as success to avoid false failure reporting.
                    return true
                }
                if (bodyMatched) {
                    val smsMessageId = cursor.getString(cursor.getColumnIndexOrThrow("_id"))
                    val threadId = cursor.getLong(cursor.getColumnIndexOrThrow("thread_id"))
                    val where = Telephony.Sms._ID + " = ?"
                    val selectionArgs = arrayOf(smsMessageId)
                    if (smsOp == OP_DELETE) {
                        val rows = resolver.delete(uri, where, selectionArgs)
                        if (rows > 0) {
                            notifyExternalProviderChange()
                            return true
                        }
                    } else if (smsOp == OP_MARK_AS_READ) {
                        val values = ContentValues()
                        values.put(Telephony.Sms.READ, true)
                        values.put(Telephony.Sms.SEEN, true)
                        val rows = resolver.update(uri, values, where, selectionArgs)
                        if (rows > 0) {
                            markRelatedSmsAsReadInThread(threadId, sender)
                            notifyExternalProviderChange()
                            return true
                        }
                    }
                }
            }
        } catch (e: Exception) {
            XLog.e("Operate SMS failed: ", e)
        } finally {
            cursor?.close()
        }
        return false
    }

    private fun isAddressMatched(curAddress: String?, targetAddress: String?): Boolean {
        if (curAddress.isNullOrBlank() || targetAddress.isNullOrBlank()) return false
        if (curAddress == targetAddress) return true
        val normalizedCurrent = curAddress.filter { it.isDigit() }
        val normalizedTarget = targetAddress.filter { it.isDigit() }
        if (normalizedCurrent.isBlank() || normalizedTarget.isBlank()) {
            return false
        }
        return normalizedCurrent.endsWith(normalizedTarget) || normalizedTarget.endsWith(normalizedCurrent)
    }

    private fun markRelatedSmsAsReadInThread(threadId: Long, sender: String?) {
        if (threadId <= 0L || sender.isNullOrBlank()) return
        try {
            val where = (
                "${Telephony.Sms.THREAD_ID} = ? AND " +
                    "${Telephony.Sms.ADDRESS} = ? AND " +
                    "${Telephony.Sms.READ} = 0"
                )
            val selectionArgs = arrayOf(threadId.toString(), sender)
            val values = ContentValues().apply {
                put(Telephony.Sms.READ, true)
                put(Telephony.Sms.SEEN, true)
            }
            val rows = mPluginContext.contentResolver.update(
                Telephony.Sms.CONTENT_URI,
                values,
                where,
                selectionArgs,
            )
            if (rows > 0) {
                XLog.d("Marked $rows unread SMS as read in same thread: $threadId")
            }
        } catch (e: Exception) {
            XLog.w("Failed to mark related SMS as read in thread: $threadId", e)
        }
    }

    private fun isBodyMatched(curBody: String?, targetBody: String?): Boolean {
        if (curBody.isNullOrEmpty() || targetBody.isNullOrEmpty()) return false
        return curBody.startsWith(targetBody) || targetBody.startsWith(curBody)
    }

    private fun notifyExternalProviderChange() {
        try {
            val packages = linkedSetOf<String>()
            val defaultSmsPackage = Telephony.Sms.getDefaultSmsPackage(mPluginContext)
            if (!defaultSmsPackage.isNullOrBlank()) {
                packages.add(defaultSmsPackage)
            }
            packages.add(GOOGLE_MESSAGES_PACKAGE_NAME)

            sendExternalProviderChange(packages)
            Handler(Looper.getMainLooper()).postDelayed(
                { sendExternalProviderChange(packages) },
                EXTERNAL_PROVIDER_CHANGE_DELAY_MS,
            )
        } catch (e: Exception) {
            XLog.w("Failed to schedule external provider change broadcast", e)
        }
    }

    private fun sendExternalProviderChange(packages: Set<String>) {
        try {
            packages.forEach { packageName ->
                val intent = Intent(Telephony.Sms.Intents.ACTION_EXTERNAL_PROVIDER_CHANGE).apply {
                    addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                    setPackage(packageName)
                    putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, packageName)
                }
                mPhoneContext.sendBroadcast(intent)
                XLog.d("Sent external provider change broadcast to: $packageName")
            }
        } catch (e: Exception) {
            XLog.w("Failed to send external provider change broadcast", e)
        }
    }

    companion object {
        private const val OP_DELETE = 0
        private const val OP_MARK_AS_READ = 1
        private const val GOOGLE_MESSAGES_PACKAGE_NAME = "com.google.android.apps.messaging"
        private const val EXTERNAL_PROVIDER_CHANGE_DELAY_MS = 500L
        private const val SMS_OP_RETRY_TIMES = 4
        private const val SMS_OP_RETRY_DELAY_MS = 800L
    }
}

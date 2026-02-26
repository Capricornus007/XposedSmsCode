package com.tianma.xsmscode.xp.hook.code.action.impl

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.tianma.xsmscode.common.utils.PrefsReader
import com.tianma.xsmscode.common.utils.XLog
import com.tianma.xsmscode.data.db.DBProvider
import com.tianma.xsmscode.data.db.entity.SmsMsg
import com.tianma.xsmscode.xp.hook.code.action.CallableAction

/**
 * Capture SMS code, record it to local DB, and forward to external SmsCode App via Broadcast (IPC).
 */
class ForwardAction(pluginContext: Context, phoneContext: Context, smsMsg: SmsMsg) :
    CallableAction(pluginContext, phoneContext, smsMsg) {

    override fun action(): Bundle? {
        try {
            val isCodeSms = !mSmsMsg.smsCode.isNullOrBlank()
            XLog.w(
                "Diag ForwardAction: delegate to app process, isCode=%s",
                isCodeSms,
            )
            // Send IPC Broadcast to the integrated SmsCode App Module
            val intent = Intent(ACTION_FORWARD_SMS)
            // ForwardReceiver is now merged into the same APK; target the host app package.
            intent.setPackage(mPluginContext.packageName)
            
            intent.putExtra("sender", mSmsMsg.sender)
            intent.putExtra("body", mSmsMsg.body)
            intent.putExtra("date", mSmsMsg.date)
            intent.putExtra("company", mSmsMsg.company)
            intent.putExtra("smsCode", mSmsMsg.smsCode)
            intent.putExtra("packageName", mSmsMsg.packageName)

            // Securing IPC with Token: Only the receiver matching our token can process this msg.
            // We use PrefsReader to retrieve token via cross-process Provider.
            val token = PrefsReader.getIpcToken(mPluginContext)
            if (token.isBlank()) {
                XLog.e("IPC token is empty, skip forwarding broadcast for security.")
                persistForwardResult(
                    success = false,
                    target = "SmsCode Engine",
                    message = "IPC token missing",
                )
                return null
            }
            intent.putExtra("ipc_token", token)

            mPluginContext.sendBroadcast(intent)
            
            XLog.i("Successfully broadcasted SMS info to SmsCode Engine with token (length ${token.length}): ${mSmsMsg.smsCode}")

            // We mark it as successful internally, actual network sending is delegated
            persistForwardResult(
                success = true, 
                target = "SmsCode Engine", 
                message = "Delegated to unified push engine"
            )
        } catch (t: Throwable) {
            XLog.e("Failed to broadcast SMS info to SmsCode Engine", t)
            persistForwardResult(
                success = false,
                target = "SmsCode Engine",
                message = "IPC Broadcast Failed: ${t.message}"
            )
        }

        return null
    }

    private fun persistForwardResult(success: Boolean, target: String?, message: String) {
        val resolver = mPluginContext.contentResolver
        val smsMsgUri = DBProvider.SMS_MSG_CONTENT_URI
        val now = System.currentTimeMillis()
        val status = if (success) SmsMsg.FORWARD_STATUS_SUCCESS else SmsMsg.FORWARD_STATUS_FAILED
        val trimmedMessage = message.take(MAX_MESSAGE_LEN)
        val values = ContentValues().apply {
            put("forward_status", status)
            put("forward_target", target)
            put("forward_message", trimmedMessage)
            put("forward_time", now)
        }

        try {
            var matchedId: Long? = null
            val projection = arrayOf("_id", "sender", "body", "date")
            resolver.query(smsMsgUri, projection, null, null, "date DESC")?.use { cursor ->
                val idIdx = cursor.getColumnIndexOrThrow("_id")
                val senderIdx = cursor.getColumnIndexOrThrow("sender")
                val bodyIdx = cursor.getColumnIndexOrThrow("body")
                val dateIdx = cursor.getColumnIndexOrThrow("date")
                while (cursor.moveToNext()) {
                    val sender = cursor.getString(senderIdx)
                    val body = cursor.getString(bodyIdx)
                    val date = cursor.getLong(dateIdx)
                    if (sender == mSmsMsg.sender && body == mSmsMsg.body && date == mSmsMsg.date) {
                        matchedId = cursor.getLong(idIdx)
                        break
                    }
                }
            }

            if (matchedId != null) {
                val itemUri = ContentUris.withAppendedId(smsMsgUri, matchedId)
                resolver.update(itemUri, values, null, null)
            } else {
                values.put("sender", mSmsMsg.sender)
                values.put("body", mSmsMsg.body)
                values.put("date", mSmsMsg.date)
                values.put("company", mSmsMsg.company)
                values.put("sms_code", mSmsMsg.smsCode)
                values.put("package_name", mSmsMsg.packageName)
                resolver.insert(smsMsgUri, values)
            }
        } catch (t: Throwable) {
            XLog.w("Persist forward result failed: %s", t.message ?: "unknown")
        }
    }

    companion object {
        const val ACTION_FORWARD_SMS = "com.tianma.xsmscode.ACTION_FORWARD_SMS"
        private const val MAX_MESSAGE_LEN = 300
    }
}

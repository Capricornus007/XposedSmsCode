package com.tianma.xsmscode.feature.sync

import com.tianma.xsmscode.common.utils.XLog
import android.content.Context
import com.tianma.xsmscode.common.utils.SPUtils
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object SyncManager {
    private const val TAG = "SyncManager"
    private val client = OkHttpClient()

    suspend fun pushSmsToGroup(
        context: Context,
        code: String,
        sender: String,
        body: String?,
        timestamp: Long,
        company: String?,
        packageName: String?
    ) {
        if (!SPUtils.isFcmSyncEnabled(context)) {
            XLog.d(TAG, "FCM Sync disabled")
            return
        }

        val serverKey = SPUtils.getFcmServerKey(context)
        if (serverKey.isNullOrBlank()) {
            XLog.e(TAG, "Cannot push SMS: FCM Server Key is missing")
            return
        }

        val groupId = SPUtils.getSyncGroupId(context)
        if (groupId.isBlank()) {
            XLog.e(TAG, "Cannot push SMS: Sync Group ID is missing")
            return
        }

        XLog.i(TAG, "Pushing encrypted SMS to topic: group_$groupId")
        
        // Encrypt data with groupId as password
        val encryptedCode = com.tianma.xsmscode.common.utils.CryptoUtils.encrypt(code, groupId) ?: code
        val encryptedBody = body?.let { com.tianma.xsmscode.common.utils.CryptoUtils.encrypt(it, groupId) } ?: body

        val mediaType = "application/json; charset=utf-8".toMediaType()

        val data = JSONObject().apply {
            put("type", "sms_sync_encrypted") // New type for encrypted payloads
            put("code", encryptedCode)
            put("sender", sender)
            put("body", encryptedBody)
            put("timestamp", timestamp.toString())
            put("company", company)
            put("package_name", packageName)
        }

        val json = JSONObject().apply {
            put("to", "/topics/group_$groupId")
            put("data", data)
            put("priority", "high")
        }

        val requestBody = json.toString().toRequestBody(mediaType)
        val request = Request.Builder()
            .url("https://fcm.googleapis.com/fcm/send")
            .post(requestBody)
            .addHeader("Authorization", "key=$serverKey")
            .addHeader("Content-Type", "application/json")
            .build()

        withContext(Dispatchers.IO) {
            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        XLog.e(TAG, "Failed to broadcast to topic: ${response.code} ${response.message}")
                    } else {
                        XLog.d(TAG, "Topic broadcast success")
                    }
                }
            } catch (e: Exception) {
                XLog.e(TAG, "Exception broadcasting to topic", e)
            }
        }
    }
}

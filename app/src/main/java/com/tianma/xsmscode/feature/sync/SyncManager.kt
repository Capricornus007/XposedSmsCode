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
        XLog.i(TAG, "pushSmsToGroup called: code=$code, sender=$sender")
        if (!SPUtils.isFcmSyncEnabled(context)) {
            XLog.d(TAG, "FCM Sync disabled")
            return
        }

        // Get service account JSON for v1 API
        val serviceAccountJson = SPUtils.getFcmServiceAccountJson(context)
        if (serviceAccountJson.isNullOrBlank()) {
            XLog.e(TAG, "Cannot push SMS: Service Account JSON is missing")
            return
        }

        val groupId = SPUtils.getSyncGroupId(context)
        if (groupId.isBlank()) {
            XLog.e(TAG, "Cannot push SMS: Sync Group ID is missing")
            return
        }

        // Extract project ID from service account
        val projectId = com.tianma.xsmscode.feature.fcm.FCMTokenManager.extractProjectId(serviceAccountJson)
        if (projectId == null) {
            XLog.e(TAG, "Cannot push SMS: Failed to extract project ID from service account")
            return
        }

        XLog.i(TAG, "Pushing encrypted SMS to topic: group_$groupId (v1 API)")
        
        // Encrypt data with groupId as password
        val encryptedCode = com.tianma.xsmscode.common.utils.CryptoUtils.encrypt(code, groupId) ?: code
        val encryptedBody = body?.let { com.tianma.xsmscode.common.utils.CryptoUtils.encrypt(it, groupId) } ?: body

        // Get OAuth access token
        val tokenResult = com.tianma.xsmscode.feature.fcm.FCMTokenManager.getAccessToken(context, serviceAccountJson)
        if (tokenResult.isFailure) {
            XLog.e(TAG, "Failed to get access token: ${tokenResult.exceptionOrNull()?.message}")
            return
        }
        val accessToken = tokenResult.getOrNull()!!

        val mediaType = "application/json; charset=utf-8".toMediaType()

        // v1 API message format
        val message = JSONObject().apply {
            put("message", JSONObject().apply {
                put("topic", "group_$groupId")
                put("data", JSONObject().apply {
                    put("type", "sms_sync_encrypted")
                    put("code", encryptedCode)
                    put("sender", sender)
                    put("body", encryptedBody ?: "")
                    put("timestamp", timestamp.toString())
                    put("company", company ?: "")
                    put("package_name", packageName ?: "")
                })
                put("android", JSONObject().apply {
                    put("priority", "high")
                })
            })
        }

        val requestBody = message.toString().toRequestBody(mediaType)
        val request = Request.Builder()
            .url("https://fcm.googleapis.com/v1/projects/$projectId/messages:send")
            .post(requestBody)
            .addHeader("Authorization", "Bearer $accessToken")
            .addHeader("Content-Type", "application/json")
            .build()

        withContext(Dispatchers.IO) {
            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        XLog.e(TAG, "Failed to broadcast to topic: ${response.code} ${response.message}")
                        XLog.e(TAG, "Response body: ${response.body.string()}")
                    } else {
                        XLog.d(TAG, "Topic broadcast success (v1 API)")
                    }
                }
            } catch (e: Exception) {
                XLog.e(TAG, "Exception broadcasting to topic", e)
            }
        }
    }
}

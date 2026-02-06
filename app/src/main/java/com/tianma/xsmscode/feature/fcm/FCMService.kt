package com.tianma.xsmscode.feature.fcm

import android.util.Log
import com.github.tianma8023.xposed.smscode.BuildConfig
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.content.Context
import com.tianma.xsmscode.common.utils.SPUtils
import com.google.firebase.messaging.FirebaseMessaging
import com.tianma.xsmscode.common.utils.CryptoUtils
import com.tianma.xsmscode.common.utils.XLog
import org.json.JSONObject

class FCMService : FirebaseMessagingService() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "Refreshed token: $token")
        scope.launch {
            SPUtils.setFcmToken(applicationContext, token)
            // No longer uploading token to any central database
            subscribeToSyncGroup(applicationContext)
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Log.d(TAG, "From: ${remoteMessage.from}")

        // Check if message contains a data payload.
        if (remoteMessage.data.isNotEmpty()) {
            Log.d(TAG, "Message data payload: ${remoteMessage.data}")
            handleDataMessage(remoteMessage.data)
        }

        // Check if message contains a notification payload.
        remoteMessage.notification?.let {
            Log.d(TAG, "Message Notification Body: ${it.body}")
        }
    }

    private fun handleDataMessage(data: Map<String, String>) {
        val type = data["type"]

        if (type == "sms_sync" || type == "sms_sync_encrypted") {
            scope.launch {
                if (!SPUtils.isFcmSyncEnabled(applicationContext)) {
                    Log.d(TAG, "FCM Sync disabled, ignoring message")
                    return@launch
                }

                val currentGroupId = SPUtils.getSyncGroupId(applicationContext)
                val sender = data["sender"] ?: "Unknown"
                val encryptedCode = data["code"]
                val encryptedBody = data["body"]
                val timestamp = data["timestamp"]?.toLongOrNull() ?: System.currentTimeMillis()
                val company = data["company"]
                val packageName = data["package_name"]

                if (encryptedCode.isNullOrEmpty()) return@launch

                val code = if (type == "sms_sync_encrypted") {
                    CryptoUtils.decrypt(encryptedCode, currentGroupId)
                } else {
                    encryptedCode
                }

                val body = if (type == "sms_sync_encrypted" && encryptedBody != null) {
                    CryptoUtils.decrypt(encryptedBody, currentGroupId)
                } else {
                    encryptedBody
                }

                if (code.isNullOrEmpty()) {
                    Log.e(TAG, "Failed to decrypt message. Group ID mismatch?")
                    return@launch
                }

                Log.i(TAG, "Received Sync: sender=$sender")
                handleSmsSync(code, sender, body, timestamp, company, packageName)
            }
        }
    }

    private suspend fun handleSmsSync(
        code: String,
        sender: String,
        body: String?,
        timestamp: Long,
        company: String?,
        packageName: String?
    ) {
        val context = applicationContext
        
        // 1. Copy to Clipboard
        if (SPUtils.isCopyToClipboardEnabled(context)) {
             withContext(Dispatchers.Main) {
                 com.tianma.xsmscode.common.utils.ClipboardUtils.copyToClipboard(context, code)
                 // Optional: Show toast if not handled by Notification or if preferred
             }
        }

        // 2. Show Notification
        if (SPUtils.isShowCodeNotificationEnabled(context)) {
            showNotification(code, sender, company)
        }
    }

    private suspend fun showNotification(code: String, sender: String, company: String?) {
        val context = applicationContext
        val title = if (!company.isNullOrEmpty()) company else sender
        val content = getString(com.github.tianma8023.xposed.smscode.R.string.code_notification_content, code)
        val notificationId = (sender + code).hashCode()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager

        // Ensure channel exists (Oreo+)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channelId = com.tianma.xsmscode.common.constant.NotificationConst.CHANNEL_ID_SMSCODE_NOTIFICATION
            if (manager.getNotificationChannel(channelId) == null) {
                // If channel doesn't exist, we should create it.
                // However, usually it's created by the app init. 
                // Let's create it just in case to be safe.
                val channelName = com.tianma.xsmscode.common.constant.NotificationConst.CHANNEL_NAME_SMSCODE_NOTIFICATION
                val channel = android.app.NotificationChannel(
                    channelId,
                    channelName,
                    android.app.NotificationManager.IMPORTANCE_HIGH
                )
                manager.createNotificationChannel(channel)
            }
        }

        val copyIntent = com.tianma.xsmscode.xp.hook.code.CopyCodeReceiver.createIntent(code, notificationId)
        val pendingIntent = android.app.PendingIntent.getBroadcast(
            context,
            notificationId,
            copyIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val builder = androidx.core.app.NotificationCompat.Builder(
            context,
            com.tianma.xsmscode.common.constant.NotificationConst.CHANNEL_ID_SMSCODE_NOTIFICATION
        )
            .setSmallIcon(com.github.tianma8023.xposed.smscode.R.drawable.ic_app_icon)
            .setContentTitle(title)
            .setContentText(content)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setWhen(System.currentTimeMillis())

        if (SPUtils.isAutoCancelCodeNotificationEnabled(context)) {
            val retentionTimeSec = SPUtils.getNotificationRetentionTime(context)
            if (retentionTimeSec > 0) {
                 val retentionMs = retentionTimeSec * 1000L
                 builder.setTimeoutAfter(retentionMs)
                 // Note: We are not implementing the separate AlarmManager fallback here for simplicity,
                 // relying on setTimeoutAfter which works for active notifications. 
                 // If more robust handling is needed, we can copy scheduleAutoCancel logic.
            }
        }

        manager.notify(notificationId, builder.build())
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }

    companion object {
        private const val TAG = "FCMService"

        /**
         * Subscribe to the sync group topic.
         */
        fun subscribeToSyncGroup(context: Context) {
            val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
            scope.launch {
                val groupId = SPUtils.getSyncGroupId(context)
                val isEnabled = SPUtils.isFcmSyncEnabled(context)
                if (isEnabled && groupId.isNotBlank()) {
                    Log.d(TAG, "Subscribing to topic: group_$groupId")
                    FirebaseMessaging.getInstance().subscribeToTopic("group_$groupId")
                        .addOnCompleteListener { task ->
                            if (!task.isSuccessful) {
                                Log.e(TAG, "Topic subscription failed", task.exception)
                            } else {
                                Log.d(TAG, "Successfully subscribed to group_$groupId")
                            }
                        }
                } else {
                    Log.d(TAG, "Sync disabled or Group ID empty, skipping subscription")
                }
            }
        }

        /**
         * Unsubscribe from a topic.
         */
        fun unsubscribeFromSyncGroup(context: Context, groupId: String) {
            if (groupId.isNotBlank()) {
                Log.d(TAG, "Unsubscribing from topic: group_$groupId")
                FirebaseMessaging.getInstance().unsubscribeFromTopic("group_$groupId")
            }
        }
    }
}

package com.github.magisk317.smscode.xp.hook.code.action.impl

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import com.github.magisk317.smscode.common.constant.NotificationConst
import com.github.magisk317.smscode.hook.R
import com.github.magisk317.smscode.runtime.bridge.HookRuntimeBridge
import com.github.magisk317.smscode.xp.hook.code.CodeNotificationBroadcastContract
import io.github.magisk317.smscode.verification.CodeNotificationDeliveryHelper
import com.github.magisk317.smscode.data.db.entity.SmsMsg
import io.github.magisk317.smscode.verification.NotifyActionHelper
import com.github.magisk317.smscode.xp.hook.code.AutoCancelReceiver
import com.github.magisk317.smscode.xp.hook.code.CopyCodeReceiver
import com.github.magisk317.smscode.xp.hook.code.action.CallableAction
import com.github.magisk317.smscode.xp.hook.code.VerificationSmsMsg
import com.github.magisk317.smscode.xp.hook.code.toVerificationMessage
import io.github.magisk317.smscode.xposed.utils.XLog

/**
 * 显示验证码通知
 *
 * 优先使用 app-owned 通知（归属于 SmsCode app 进程，前台时体验更好）。
 * 当 app-owned 通知失败时（应用在后台/被杀等），自动 fallback 到
 * phone-owned 通知（直接从 hook 进程发送，不受后台活动限制）。
 */
class NotifyAction(
    pluginContext: Context,
    phoneContext: Context,
    smsMsg: SmsMsg,
    private val enabled: Boolean? = null,
    private val autoCancelEnabled: Boolean? = null,
    private val retentionTimeMs: Long? = null,
) : CallableAction(pluginContext, phoneContext, smsMsg) {

    override fun action(): Bundle? {
        XLog.i("NotifyAction.action() called: enabled=%s smsCode=%s", enabled, mSmsMsg.smsCode)
        return NotifyActionHelper<VerificationSmsMsg, Bundle?>(
            pluginContext = mPluginContext,
            smsMsg = mSmsMsg.toVerificationMessage(),
            enabled = enabled ?: HookRuntimeBridge.prefsAccess.showCodeNotification(mPluginContext),
            autoCancelEnabledProvider = { context ->
                autoCancelEnabled ?: HookRuntimeBridge.prefsAccess.autoCancelCodeNotification(context)
            },
            retentionTimeMsProvider = { context ->
                retentionTimeMs ?: (HookRuntimeBridge.prefsAccess.getNotificationRetentionTime(context) * 1000L)
            },
            tokenProvider = { context -> HookRuntimeBridge.prefsAccess.getIpcToken(context).takeIf(String::isNotBlank) },
            channelInitializer = { context -> ensureNotificationChannel(context) },
            diagnostics = {
                NotifyActionHelper.DeliveryDiagnostics(
                    canPost = true,
                    summary = "deferred_to_receiver",
                )
            },
            notifier = { request -> showAppOwnedNotification(request) },
        ).run()
    }

    private fun showAppOwnedNotification(
        request: NotifyActionHelper.AppOwnedNotificationRequest<VerificationSmsMsg>,
    ): Bundle? {
        val appResult = CodeNotificationDeliveryHelper.requestAppOwnedNotification(
            context = mPhoneContext,
            smsMsg = request.smsMsg,
            notificationId = request.notificationId,
            autoCancelEnabled = request.autoCancelEnabled,
            retentionTimeMs = request.retentionTimeMs,
            token = request.token,
            intentFactory = CodeNotificationBroadcastContract::createIntent,
        )
        if (appResult.success) return null

        // App-owned notification failed (app background/killed, broadcast timeout, etc.).
        // Fallback to phone-owned notification from the hook process directly.
        XLog.w(
            "App-owned code notification failed (reason=%s), falling back to phone-owned",
            appResult.reason,
        )
        return showPhoneOwnedNotification(request)
    }

    private fun showPhoneOwnedNotification(
        request: NotifyActionHelper.AppOwnedNotificationRequest<VerificationSmsMsg>,
    ): Bundle? {
        val manager = mPhoneContext.getSystemService(Context.NOTIFICATION_SERVICE)
            as? NotificationManager
            ?: return null

        ensurePhoneNotificationChannel(manager)

        val copyIntent = CopyCodeReceiver.createIntent(
            mPhoneContext,
            request.smsMsg.smsCode,
            request.notificationId,
        )
        val pendingIntent = PendingIntent.getBroadcast(
            mPhoneContext,
            request.notificationId,
            copyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = androidx.core.app.NotificationCompat.Builder(
            mPhoneContext,
            NotificationConst.CHANNEL_ID_SMSCODE_NOTIFICATION,
        )
            .setSmallIcon(R.drawable.ic_hook_app_icon)
            .setContentTitle(mPluginContext.getString(R.string.hook_app_name))
            .setContentText("SMS code: ${request.smsMsg.smsCode}")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setGroup(NotificationConst.GROUP_KEY_SMSCODE_NOTIFICATION)
            .build()

        return try {
            manager.notify(request.notificationId, notification)
            XLog.i("Phone-owned code notification posted id=%d", request.notificationId)
            null
        } catch (e: Exception) {
            XLog.w("Phone-owned notification failed: %s", e.message)
            null
        }
    }

    private fun ensureNotificationChannel(context: Context) {
        // Channel creation in the hook process is best-effort. The actual notification
        // is posted from the SmsCode app process via broadcast, where the receiver
        // creates the channel with the correct UID. We catch any SecurityException
        // because uid 10148 (com.android.mms) cannot manage channels for
        // com.github.tianma8023.xposed.smscode (uid 10353).
        runCatching {
            HookRuntimeBridge.notificationAccess.createNotificationChannel(
                mPhoneContext,
                NotificationConst.CHANNEL_ID_SMSCODE_NOTIFICATION,
                mPluginContext.getString(R.string.hook_smscode_channel),
                NotificationManager.IMPORTANCE_HIGH,
            )
        }
    }

    private fun ensurePhoneNotificationChannel(manager: NotificationManager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channelId = NotificationConst.CHANNEL_ID_SMSCODE_NOTIFICATION
        if (manager.getNotificationChannel(channelId) != null) return
        val channel = NotificationChannel(
            channelId,
            mPluginContext.getString(R.string.hook_smscode_channel),
            NotificationManager.IMPORTANCE_HIGH,
        )
        manager.createNotificationChannel(channel)
    }
}

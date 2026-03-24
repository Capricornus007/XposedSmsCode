package com.github.magisk317.smscode.xp.hook.code.action.impl

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.github.magisk317.smscode.common.constant.CodeNotificationOwner
import com.github.magisk317.smscode.core.R
import com.github.magisk317.smscode.common.constant.NotificationConst
import com.github.magisk317.smscode.common.utils.PrefsReader
import com.github.magisk317.smscode.xp.hook.code.CodeNotificationBroadcastContract
import io.github.magisk317.smscode.xposed.utils.XLog
import com.github.magisk317.smscode.data.db.entity.SmsMsg
import com.github.magisk317.smscode.xp.hook.code.AutoCancelReceiver
import com.github.magisk317.smscode.xp.hook.code.CopyCodeReceiver
import com.github.magisk317.smscode.xp.hook.code.action.CallableAction

/**
 * 显示验证码通知
 */
class NotifyAction(pluginContext: Context, phoneContext: Context, smsMsg: SmsMsg) :
    CallableAction(pluginContext, phoneContext, smsMsg) {

    override fun action(): Bundle? {
        if (!PrefsReader.showCodeNotification(mPluginContext)) {
            return null
        }
        return when (PrefsReader.getCodeNotificationOwner(mPluginContext)) {
            CodeNotificationOwner.PHONE -> showPhoneOwnedNotification(mSmsMsg)
            CodeNotificationOwner.APP -> showAppOwnedNotification(mSmsMsg)
            else -> {
                XLog.w("Skip code notification: owner not selected")
                null
            }
        }
    }

    private fun showAppOwnedNotification(smsMsg: SmsMsg): Bundle? {
        val notificationId = smsMsg.hashCode()
        val autoCancelEnabled = PrefsReader.autoCancelCodeNotification(mPluginContext)
        val retentionTimeMs = PrefsReader.getNotificationRetentionTime(mPluginContext) * 1000L
        val token = PrefsReader.getIpcToken(mPluginContext).takeIf { it.isNotBlank() }
        val intent = CodeNotificationBroadcastContract.createIntent(
            sender = smsMsg.sender,
            company = smsMsg.company,
            smsCode = smsMsg.smsCode,
            notificationId = notificationId,
            autoCancelEnabled = autoCancelEnabled,
            retentionTimeMs = retentionTimeMs,
            token = token,
        )
        mPhoneContext.sendBroadcast(intent)
        XLog.i(
            "Requested app-owned code notification id=%d autoCancel=%s retentionMs=%d tokenPresent=%s",
            notificationId,
            autoCancelEnabled,
            retentionTimeMs,
            token != null,
        )
        return null
    }

    @SuppressLint("UnspecifiedImmutableFlag", "NotificationPermission")
    private fun showPhoneOwnedNotification(smsMsg: SmsMsg): Bundle? {
        val manager = mPhoneContext.getSystemService(
            Context.NOTIFICATION_SERVICE,
        ) as NotificationManager? ?: return null

        val company = smsMsg.company
        val smsCode = smsMsg.smsCode
        val title = if (TextUtils.isEmpty(company)) smsMsg.sender else company
        val content = mPluginContext.getString(R.string.code_notification_content, smsCode)

        val notificationId = smsMsg.hashCode()

        val copyCodeIntent = CopyCodeReceiver.createIntent(mPluginContext, smsCode, notificationId)
        val contentIntent = PendingIntent.getBroadcast(
            mPhoneContext,
            notificationId,
            copyCodeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or pendingIntentFlag(),
        )

        val builder = NotificationCompat.Builder(mPluginContext, NotificationConst.CHANNEL_ID_SMSCODE_NOTIFICATION)
            .setSmallIcon(R.drawable.ic_app_icon)
            .setLargeIcon(BitmapFactory.decodeResource(mPluginContext.resources, R.drawable.ic_app_icon))
            .setWhen(System.currentTimeMillis())
            .setContentTitle(title)
            .setContentText(content)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setColor(ContextCompat.getColor(mPluginContext, R.color.ic_launcher_background))
            .setGroup(NotificationConst.GROUP_KEY_SMSCODE_NOTIFICATION)

        val autoCancelEnabled = PrefsReader.autoCancelCodeNotification(mPluginContext)
        if (autoCancelEnabled) {
            val retentionTime = PrefsReader.getNotificationRetentionTime(mPluginContext) * 1000L
            if (retentionTime > 0L) {
                builder.setTimeoutAfter(retentionTime)
                scheduleAutoCancel(notificationId, retentionTime)
            } else {
                XLog.i("Auto cancel skipped: retentionTimeMs=%d", retentionTime)
            }
        } else {
            XLog.i("Auto cancel disabled")
        }

        val notification = builder.build()

        manager.notify(notificationId, notification)
        XLog.i("Posted phone-owned code notification id=%d autoCancel=%s", notificationId, autoCancelEnabled)
        return null
    }

    private fun scheduleAutoCancel(notificationId: Int, retentionTimeMs: Long) {
        val alarmManager = mPluginContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager? ?: return
        val appUid = mPluginContext.applicationInfo?.uid ?: -1
        if (android.os.Process.myUid() != appUid) {
            XLog.i("Skip alarm auto cancel: uid=%d, appUid=%d", android.os.Process.myUid(), appUid)
            return
        }
        val intent = AutoCancelReceiver.createIntent(mPluginContext, notificationId)
        val pendingIntent = PendingIntent.getBroadcast(
            mPluginContext,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val triggerAt = System.currentTimeMillis() + retentionTimeMs
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        } else {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        }
        XLog.i("Schedule auto cancel alarm, id=%d, delayMs=%d", notificationId, retentionTimeMs)
    }

    private fun pendingIntentFlag(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE
        } else {
            0
        }
    }
}

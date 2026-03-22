package com.github.magisk317.smscode.xp.helper

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.github.tianma8023.xposed.smscode.BuildConfig
import com.github.magisk317.smscode.core.R
import com.github.magisk317.smscode.common.constant.NotificationConst
import com.github.magisk317.smscode.common.utils.NotificationUtils
import com.github.magisk317.smscode.xp.hook.code.helper.InputHelper
import io.github.magisk317.smscode.xposed.utils.XLog
import java.util.LinkedHashSet

object RelayConflictNoticeHelper {
    private const val MAX_TRACKED_EVENT_IDS = 64
    private val notifiedEventIds = LinkedHashSet<String>()

    fun initNotificationChannel(pluginContext: Context, phoneContext: Context) {
        NotificationUtils.createNotificationChannel(
            phoneContext,
            NotificationConst.CHANNEL_ID_RELAY_CONFLICT,
            pluginContext.getString(R.string.channel_name_relay_conflict_notification),
            NotificationManager.IMPORTANCE_HIGH,
        )
    }

    fun notifyConflictOnSms(pluginContext: Context, phoneContext: Context, eventId: String) {
        if (!markNotified(eventId)) {
            XLog.w("Relay conflict notice deduped: event_id=%s", eventId)
            return
        }
        showConflictNotification(pluginContext, phoneContext)
        showConflictToast(pluginContext, phoneContext)
        XLog.w(
            "Relay conflict notice sent: event_id=%s package=%s bypass=%s",
            eventId,
            ModuleConflictArbiter.TARGET_RELAY_PACKAGE,
            BuildConfig.ALLOW_CONFLICT_BYPASS,
        )
    }

    private fun markNotified(eventId: String): Boolean = synchronized(notifiedEventIds) {
        if (!notifiedEventIds.add(eventId)) {
            return false
        }
        while (notifiedEventIds.size > MAX_TRACKED_EVENT_IDS) {
            val first = notifiedEventIds.firstOrNull() ?: break
            notifiedEventIds.remove(first)
        }
        true
    }

    @SuppressLint("NotificationPermission", "UnspecifiedImmutableFlag")
    private fun showConflictNotification(pluginContext: Context, phoneContext: Context) {
        val manager = phoneContext.getSystemService(
            Context.NOTIFICATION_SERVICE,
        ) as NotificationManager? ?: return
        val launchIntent = pluginContext.packageManager.getLaunchIntentForPackage(BuildConfig.APPLICATION_ID)
        val contentIntent = launchIntent?.let {
            PendingIntent.getActivity(
                phoneContext,
                0,
                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or pendingIntentMutableFlag(),
            )
        }
        val content = pluginContext.getString(
            R.string.relay_conflict_notification_content,
            pluginContext.getString(R.string.relay_conflict_other_app_name),
            ModuleConflictArbiter.TARGET_RELAY_PACKAGE,
            pluginContext.getString(R.string.app_name),
        )
        val builder = NotificationCompat.Builder(pluginContext, NotificationConst.CHANNEL_ID_RELAY_CONFLICT)
            .setSmallIcon(R.drawable.ic_app_icon)
            .setLargeIcon(BitmapFactory.decodeResource(pluginContext.resources, R.drawable.ic_app_icon))
            .setWhen(System.currentTimeMillis())
            .setContentTitle(pluginContext.getString(R.string.relay_conflict_dialog_title))
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setAutoCancel(true)
            .setColor(ContextCompat.getColor(pluginContext, R.color.ic_launcher_background))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        if (contentIntent != null) {
            builder.setContentIntent(contentIntent)
        }
        manager.notify(NotificationConst.NOTIFICATION_ID_RELAY_CONFLICT, builder.build())
    }

    private fun showConflictToast(pluginContext: Context, phoneContext: Context) {
        InputHelper.sendToast(
            phoneContext,
            pluginContext.getString(R.string.relay_conflict_sms_toast),
            Toast.LENGTH_SHORT,
        )
    }

    private fun pendingIntentMutableFlag(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_IMMUTABLE
        } else {
            0
        }
    }
}

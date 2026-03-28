package com.github.magisk317.smscode.runtime

import android.app.NotificationManager
import android.content.Context
import com.github.magisk317.smscode.common.utils.NotificationUtils

object RuntimeNotificationFacade {
    data class DeliveryDiagnostics(
        val notificationsEnabled: Boolean,
        val postNotificationsGranted: Boolean,
        val channelImportance: Int?,
    ) {
        val canPost: Boolean
            get() = notificationsEnabled &&
                postNotificationsGranted &&
                channelImportance != NotificationManager.IMPORTANCE_NONE

        fun summary(): String {
            return "enabled=$notificationsEnabled permission=$postNotificationsGranted channel=${
                NotificationUtils.importanceLabel(channelImportance)
            }"
        }
    }

    fun createNotificationChannel(
        context: Context,
        channelId: String,
        channelName: String,
        importance: Int,
    ) {
        NotificationUtils.createNotificationChannel(context, channelId, channelName, importance)
    }

    fun inspectDelivery(context: Context, channelId: String): DeliveryDiagnostics {
        val diagnostics = NotificationUtils.inspectDelivery(context, channelId)
        return DeliveryDiagnostics(
            notificationsEnabled = diagnostics.notificationsEnabled,
            postNotificationsGranted = diagnostics.postNotificationsGranted,
            channelImportance = diagnostics.channelImportance,
        )
    }

    fun hasPostNotificationsPermission(context: Context): Boolean {
        return NotificationUtils.hasPostNotificationsPermission(context)
    }
}

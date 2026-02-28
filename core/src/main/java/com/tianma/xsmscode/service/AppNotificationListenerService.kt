package com.tianma.xsmscode.service

import android.app.Notification
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.tianma.xsmscode.common.constant.PrefConst
import com.tianma.xsmscode.common.utils.AppPreferencesDataStore
import com.tianma.xsmscode.common.utils.XLog
import kotlinx.coroutines.runBlocking
import com.tianma.xsmscode.data.db.entity.AppInfo
import com.tianma.xsmscode.feature.store.EntityStoreManager
import com.tianma.xsmscode.feature.store.EntityType

class AppNotificationListenerService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)
        // The original `if (sbn == null) return` is removed because sbn is now non-nullable.

        val packageName = sbn.packageName
        // Do not forward our own notifications or system notifications
        if (packageName == "com.tianma.xsmscode" || packageName == "android") {
            return
        }

        // Check if forwarding is enabled for this app
        val enabledApps = EntityStoreManager.loadEntitiesFromFile(
            applicationContext,
            EntityType.APP_CONFIG,
            AppInfo::class.java
        )
        if (enabledApps.none { it.packageName == packageName && it.forwarding }) {
            return
        }

        val notification = sbn.notification
        val title = notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val tickerText = notification.tickerText?.toString() ?: ""

        val body = if (text.isNotEmpty()) text else tickerText

        if (title.isBlank() && body.isBlank()) return
        if (shouldSkipNotification(notification)) return

        // TODO: check configuration rules (blacklist/whitelist) for notifications.
        
        val forwardIntent = Intent(PrefConst.ACTION_FORWARD_SMS)
        forwardIntent.setPackage(applicationContext.packageName)
        forwardIntent.putExtra("sender", title)
        forwardIntent.putExtra("body", body)
        forwardIntent.putExtra("date", sbn.postTime)
        forwardIntent.putExtra("packageName", packageName)
        forwardIntent.putExtra("msgType", "app_notify")
        
        // Resolve App Name
        val pm = applicationContext.packageManager
        val appName = try {
            val info = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(info).toString()
        } catch (e: Exception) {
            packageName
        }
        forwardIntent.putExtra("company", appName)

        val token = runBlocking {
            AppPreferencesDataStore.getString(applicationContext, PrefConst.KEY_IPC_TOKEN, "")
        }
        forwardIntent.putExtra("ipc_token", token)

        XLog.i("Notification intercepted: pkg=$packageName, title=$title, body=$body")
        sendBroadcast(forwardIntent)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
    }

    private fun shouldSkipNotification(notification: Notification): Boolean {
        val flags = notification.flags
        if ((flags and Notification.FLAG_FOREGROUND_SERVICE) != 0) {
            return true
        }
        if ((flags and Notification.FLAG_ONGOING_EVENT) != 0) {
            return true
        }
        if (notification.category == Notification.CATEGORY_SERVICE) {
            return true
        }
        val isGroupSummary = (flags and Notification.FLAG_GROUP_SUMMARY) != 0
        return isGroupSummary
    }
}

package com.tianma.xsmscode.xp.hook.notification

import android.app.Notification
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.UserHandle
import com.tianma.xsmscode.common.constant.PrefConst
import com.tianma.xsmscode.common.utils.XLog
import com.tianma.xsmscode.xp.hook.BaseHook
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

class NotificationManagerHook : BaseHook() {
    private data class ModuleEndpoint(
        val packageName: String,
        val prefAuthority: String,
        val dbAuthority: String,
    )

    override fun hookOnLoadPackage(): Boolean = true

    override fun onLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != "android") return

        try {
            val nmsClass = XposedHelpers.findClass("com.android.server.notification.NotificationManagerService", lpparam.classLoader)

            val methods = nmsClass.declaredMethods.filter { it.name == "enqueueNotificationInternal" }
            if (methods.isEmpty()) {
                XLog.w("NotificationManagerHook: no enqueueNotificationInternal method found")
                return
            }

            methods.forEach { method ->
                XposedBridge.hookMethod(
                    method,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            try {
                                handleEnqueueNotificationInternal(param)
                            } catch (t: Throwable) {
                                XLog.e("NotificationManagerHook error", t)
                            }
                        }
                    },
                )
            }
            XLog.w("NotificationManagerHook: successfully hooked enqueueNotificationInternal")
        } catch (t: Throwable) {
            XLog.e("NotificationManagerHook: failed to hook NotificationManagerService", t)
        }
    }

    private fun handleEnqueueNotificationInternal(param: XC_MethodHook.MethodHookParam) {
        var pkg: String? = null
        var notification: Notification? = null

        for (arg in param.args) {
            if (arg is String && pkg == null) {
                pkg = arg
            } else if (arg is Notification) {
                notification = arg
            }
        }

        if (pkg.isNullOrEmpty() || notification == null) return

        val systemContext = try {
            XposedHelpers.callMethod(param.thisObject, "getContext") as? Context
        } catch (_: Throwable) {
            null
        }

        if (systemContext == null) {
            XLog.w("NotificationManagerHook: failed to get Context")
            return
        }

        val endpoint = resolveModuleEndpoint(systemContext) ?: run {
            XLog.w("NotificationManagerHook: failed to resolve module endpoint")
            return
        }
        val modulePackage = endpoint.packageName

        if (pkg == modulePackage) return

        val title = notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val tickerText = notification.tickerText?.toString() ?: ""

        val body = if (text.isNotEmpty()) text else tickerText
        if (title.isBlank() && body.isBlank()) return
        if (shouldSkipNotification(notification)) return

        if (!queryAppForwardingEnabled(systemContext, endpoint, pkg)) {
            return
        }

        val forwardIntent = Intent(PrefConst.ACTION_FORWARD_SMS)
        forwardIntent.setPackage(modulePackage)
        forwardIntent.putExtra("sender", title)
        forwardIntent.putExtra("body", body)
        forwardIntent.putExtra("date", System.currentTimeMillis())
        forwardIntent.putExtra("packageName", pkg)
        forwardIntent.putExtra("msgType", "app_notify")

        val pm = systemContext.packageManager
        val appName = try {
            val info = pm.getApplicationInfo(pkg, 0)
            pm.getApplicationLabel(info).toString()
        } catch (_: Exception) {
            pkg
        }
        forwardIntent.putExtra("company", appName)

        val token = queryPrefString(
            systemContext = systemContext,
            endpoint = endpoint,
            key = PrefConst.KEY_IPC_TOKEN,
            defaultValue = "",
        )
        if (token.isBlank()) {
            XLog.w("NotificationManagerHook: ipc token empty, skip forwarding for pkg=%s", pkg)
            return
        }
        forwardIntent.putExtra("ipc_token", token)

        XLog.i("NotificationManagerHook intercepted: pkg=$pkg, title=$title, body=$body")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            try {
                val allUserHandle = UserHandle::class.java.getField("ALL").get(null) as UserHandle
                systemContext.sendBroadcastAsUser(forwardIntent, allUserHandle)
            } catch (_: Exception) {
                systemContext.sendBroadcast(forwardIntent)
            }
        } else {
            systemContext.sendBroadcast(forwardIntent)
        }
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

    private fun resolveModuleEndpoint(systemContext: Context): ModuleEndpoint? {
        val candidates = LinkedHashSet<String>()
        resolveForwardReceiverPackages(systemContext).forEach { candidates.add(it) }
        candidates.add("com.tianma.xsmscode")
        candidates.add("com.github.tianma8023.xposed.smscode")

        for (packageName in candidates) {
            val prefAuthority = "$packageName.pref.provider"
            val dbAuthority = "$packageName.db.provider"
            try {
                val prefProviderPackage = resolveProviderPackage(systemContext, prefAuthority)
                val dbProviderPackage = resolveProviderPackage(systemContext, dbAuthority)
                if (prefProviderPackage == packageName && dbProviderPackage == packageName) {
                    return ModuleEndpoint(
                        packageName = packageName,
                        prefAuthority = prefAuthority,
                        dbAuthority = dbAuthority,
                    )
                }
                XLog.w(
                    "NotificationManagerHook: candidate %s rejected. prefProvider=%s dbProvider=%s",
                    packageName,
                    prefProviderPackage ?: "<null>",
                    dbProviderPackage ?: "<null>",
                )
            } catch (t: Throwable) {
                XLog.w(
                    "NotificationManagerHook: candidate %s resolve failed: %s",
                    packageName,
                    t.message ?: t.javaClass.simpleName,
                )
            }
        }
        return null
    }

    private fun resolveForwardReceiverPackages(systemContext: Context): List<String> {
        val packages = LinkedHashSet<String>()
        return try {
            val intent = Intent(PrefConst.ACTION_FORWARD_SMS)
            val receivers = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                systemContext.packageManager.queryBroadcastReceivers(
                    intent,
                    PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong()),
                )
            } else {
                @Suppress("DEPRECATION")
                systemContext.packageManager.queryBroadcastReceivers(intent, PackageManager.MATCH_ALL)
            }
            receivers.forEach { resolveInfo ->
                resolveInfo.activityInfo?.packageName?.takeIf { it.isNotBlank() }?.let { packages.add(it) }
            }
            packages.toList()
        } catch (t: Throwable) {
            XLog.w(
                "NotificationManagerHook: queryBroadcastReceivers failed: %s",
                t.message ?: t.javaClass.simpleName,
            )
            emptyList()
        }
    }

    private fun resolveProviderPackage(systemContext: Context, authority: String): String? {
        return try {
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                systemContext.packageManager.resolveContentProvider(
                    authority,
                    PackageManager.ComponentInfoFlags.of(PackageManager.MATCH_ALL.toLong()),
                )
            } else {
                @Suppress("DEPRECATION")
                systemContext.packageManager.resolveContentProvider(authority, PackageManager.MATCH_ALL)
            }
            info?.packageName
        } catch (t: Throwable) {
            XLog.w(
                "NotificationManagerHook: resolveContentProvider(%s) failed: %s",
                authority,
                t.message ?: t.javaClass.simpleName,
            )
            null
        }
    }

    private fun queryPrefString(
        systemContext: Context,
        endpoint: ModuleEndpoint,
        key: String,
        defaultValue: String,
    ): String = queryPrefStringValue(systemContext, endpoint.prefAuthority, "string", key, defaultValue)

    private fun queryPrefStringValue(
        systemContext: Context,
        authority: String,
        typePath: String,
        key: String,
        defaultValue: String,
    ): String {
        val uri = Uri.parse("content://$authority/$typePath")
            .buildUpon()
            .appendQueryParameter("key", key)
            .appendQueryParameter("default", defaultValue)
            .build()
        return try {
            systemContext.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(0) ?: defaultValue
                } else {
                    defaultValue
                }
            } ?: defaultValue
        } catch (t: Throwable) {
            XLog.w(
                "NotificationManagerHook: query pref failed. authority=%s key=%s err=%s",
                authority,
                key,
                t.message ?: t.javaClass.simpleName,
            )
            defaultValue
        }
    }

    private fun queryAppForwardingEnabled(
        systemContext: Context,
        endpoint: ModuleEndpoint,
        packageName: String,
    ): Boolean {
        val uri = Uri.withAppendedPath(Uri.parse("content://${endpoint.dbAuthority}/app_info"), packageName)
        return try {
            systemContext.contentResolver.query(uri, arrayOf("forwarding"), null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) {
                    return false
                }
                readCursorBoolean(cursor, "forwarding", false)
            } ?: false
        } catch (t: Throwable) {
            XLog.w(
                "NotificationManagerHook: query app forwarding failed. package=%s err=%s",
                packageName,
                t.message ?: t.javaClass.simpleName,
            )
            false
        }
    }

    private fun readCursorBoolean(cursor: Cursor, columnName: String, defaultValue: Boolean): Boolean {
        val index = cursor.getColumnIndex(columnName)
        if (index < 0) return defaultValue
        return when (cursor.getType(index)) {
            Cursor.FIELD_TYPE_INTEGER -> cursor.getInt(index) != 0
            Cursor.FIELD_TYPE_STRING -> {
                val raw = cursor.getString(index).orEmpty()
                raw == "1" || raw.equals("true", ignoreCase = true)
            }
            else -> defaultValue
        }
    }
}

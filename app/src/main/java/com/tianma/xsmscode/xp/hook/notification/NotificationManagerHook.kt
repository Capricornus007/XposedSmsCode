package com.tianma.xsmscode.xp.hook.notification

import android.app.Notification
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.Process
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

    @Volatile
    private var cachedEndpoint: ModuleEndpoint? = null

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

        if (pkg.isNullOrEmpty() || notification == null) {
            XLog.d(
                "NotificationManagerHook: skip invalid args. pkg=%s hasNotification=%s",
                pkg ?: "<null>",
                notification != null,
            )
            return
        }

        val systemContext = try {
            XposedHelpers.callMethod(param.thisObject, "getContext") as? Context
        } catch (_: Throwable) {
            null
        }

        if (systemContext == null) {
            XLog.w("NotificationManagerHook: failed to get Context")
            return
        }

        val endpoint = getModuleEndpoint(systemContext) ?: run {
            XLog.w("NotificationManagerHook: failed to resolve module endpoint")
            return
        }
        val modulePackage = endpoint.packageName

        if (pkg == modulePackage) {
            XLog.d("NotificationManagerHook: skip self notification. pkg=%s", pkg)
            return
        }

        val title = notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val tickerText = notification.tickerText?.toString() ?: ""

        val body = if (text.isNotEmpty()) text else tickerText
        if (title.isBlank() && body.isBlank()) {
            XLog.d("NotificationManagerHook: skip blank content. pkg=%s", pkg)
            return
        }
        val skipReason = getSkipReason(notification)
        if (skipReason != null) {
            XLog.d(
                "NotificationManagerHook: skip by policy. pkg=%s reason=%s flags=0x%s category=%s",
                pkg,
                skipReason,
                Integer.toHexString(notification.flags),
                notification.category ?: "<null>",
            )
            return
        }

        if (!queryAppForwardingEnabled(systemContext, endpoint, pkg)) {
            XLog.d("NotificationManagerHook: skip forwarding (disabled/unreadable). pkg=%s", pkg)
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
            callerPackageHint = pkg,
        )
        if (token.isBlank()) {
            XLog.w("NotificationManagerHook: ipc token empty, skip forwarding for pkg=%s", pkg)
            return
        }
        forwardIntent.putExtra("ipc_token", token)

        XLog.i("NotificationManagerHook intercepted: pkg=$pkg, title=$title, body=$body")

        withClearedCallingIdentity("sendBroadcast:$pkg") {
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
    }

    private fun getSkipReason(notification: Notification): String? {
        val flags = notification.flags
        if ((flags and Notification.FLAG_FOREGROUND_SERVICE) != 0) {
            return "foreground_service"
        }
        if ((flags and Notification.FLAG_ONGOING_EVENT) != 0) {
            return "ongoing_event"
        }
        if (notification.category == Notification.CATEGORY_SERVICE) {
            return "category_service"
        }
        val isGroupSummary = (flags and Notification.FLAG_GROUP_SUMMARY) != 0
        return if (isGroupSummary) "group_summary" else null
    }

    private fun getModuleEndpoint(systemContext: Context): ModuleEndpoint? {
        cachedEndpoint?.let { return it }
        val resolved = withClearedCallingIdentity("resolveModuleEndpoint") {
            resolveModuleEndpoint(systemContext)
        } ?: return null
        cachedEndpoint = resolved
        XLog.d("NotificationManagerHook: cache endpoint package=%s", resolved.packageName)
        return resolved
    }

    private fun resolveModuleEndpoint(systemContext: Context): ModuleEndpoint? {
        val candidates = LinkedHashSet<String>()
        resolveForwardReceiverPackages(systemContext).forEach { candidates.add(it) }
        candidates.add("com.tianma.xsmscode")
        candidates.add("com.github.tianma8023.xposed.smscode")
        var fallbackCandidate: String? = null

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
                if (fallbackCandidate == null && isPackageInstalled(systemContext, packageName)) {
                    fallbackCandidate = packageName
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
        if (!fallbackCandidate.isNullOrBlank()) {
            XLog.w(
                "NotificationManagerHook: using unverified endpoint fallback package=%s",
                fallbackCandidate,
            )
            return ModuleEndpoint(
                packageName = fallbackCandidate,
                prefAuthority = "$fallbackCandidate.pref.provider",
                dbAuthority = "$fallbackCandidate.db.provider",
            )
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
        callerPackageHint: String? = null,
    ): String = queryPrefStringValue(
        systemContext = systemContext,
        authority = endpoint.prefAuthority,
        typePath = "string",
        key = key,
        defaultValue = defaultValue,
        callerPackageHint = callerPackageHint,
    )

    private fun queryPrefStringValue(
        systemContext: Context,
        authority: String,
        typePath: String,
        key: String,
        defaultValue: String,
        callerPackageHint: String? = null,
    ): String {
        val uri = Uri.parse("content://$authority/$typePath")
            .buildUpon()
            .appendQueryParameter("key", key)
            .appendQueryParameter("default", defaultValue)
            .build()
        val primary = withClearedCallingIdentity("queryPref:$authority/$key") {
            queryPrefStringInternal(systemContext, uri, defaultValue)
        }
        if (primary != null) return primary

        val fallbackContext = buildCallerPackageContext(
            systemContext = systemContext,
            callerPackageHint = callerPackageHint,
        )
        if (fallbackContext != null) {
            val fallback = queryPrefStringInternal(fallbackContext, uri, defaultValue)
            if (fallback != null) {
                XLog.d(
                    "NotificationManagerHook: query pref fallback succeeded. authority=%s key=%s caller=%s",
                    authority,
                    key,
                    callerPackageHint ?: "<none>",
                )
                return fallback
            }
        }
        XLog.w(
            "NotificationManagerHook: query pref unavailable. authority=%s key=%s caller=%s",
            authority,
            key,
            callerPackageHint ?: "<none>",
        )
        return defaultValue
    }

    private fun queryAppForwardingEnabled(
        systemContext: Context,
        endpoint: ModuleEndpoint,
        packageName: String,
    ): Boolean {
        val uri = Uri.withAppendedPath(Uri.parse("content://${endpoint.dbAuthority}/app_info"), packageName)
        val primary = withClearedCallingIdentity("queryAppForwarding:$packageName") {
            queryAppForwardingInternal(systemContext, uri, packageName)
        }
        if (primary != null) {
            return primary
        }

        val fallbackContext = buildCallerPackageContext(
            systemContext = systemContext,
            callerPackageHint = packageName,
        )
        if (fallbackContext != null) {
            val fallback = queryAppForwardingInternal(fallbackContext, uri, packageName)
            if (fallback != null) {
                XLog.d("NotificationManagerHook: app forwarding fallback hit. pkg=%s enabled=%s", packageName, fallback)
                return fallback
            }
        }
        return false
    }

    private fun queryPrefStringInternal(context: Context, uri: Uri, defaultValue: String): String? {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(0) ?: defaultValue
                } else {
                    defaultValue
                }
            } ?: run {
                XLog.w("NotificationManagerHook: pref query returned null cursor. uri=%s", uri)
                null
            }
        } catch (t: Throwable) {
            XLog.w(
                "NotificationManagerHook: pref query failed. uri=%s err=%s callerUid=%d selfUid=%d",
                uri,
                t.message ?: t.javaClass.simpleName,
                Binder.getCallingUid(),
                Process.myUid(),
            )
            null
        }
    }

    private fun queryAppForwardingInternal(context: Context, uri: Uri, packageName: String): Boolean? {
        return try {
            context.contentResolver.query(uri, arrayOf("forwarding"), null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) {
                    XLog.d("NotificationManagerHook: app_info row missing. pkg=%s", packageName)
                    return false
                }
                val enabled = readCursorBoolean(cursor, "forwarding", false)
                XLog.d("NotificationManagerHook: app forwarding state. pkg=%s enabled=%s", packageName, enabled)
                enabled
            } ?: run {
                XLog.w("NotificationManagerHook: app forwarding query returned null cursor. pkg=%s uri=%s", packageName, uri)
                null
            }
        } catch (t: Throwable) {
            XLog.w(
                "NotificationManagerHook: query app forwarding failed. package=%s err=%s callerUid=%d selfUid=%d",
                packageName,
                t.message ?: t.javaClass.simpleName,
                Binder.getCallingUid(),
                Process.myUid(),
            )
            null
        }
    }

    private fun buildCallerPackageContext(systemContext: Context, callerPackageHint: String?): Context? {
        val callerUid = Binder.getCallingUid()
        if (callerUid == Process.SYSTEM_UID) {
            return null
        }
        val packages = linkedSetOf<String>()
        callerPackageHint?.takeIf { it.isNotBlank() }?.let { packages.add(it) }
        try {
            systemContext.packageManager.getPackagesForUid(callerUid)?.forEach { pkg ->
                if (pkg.isNotBlank()) packages.add(pkg)
            }
        } catch (_: Throwable) {
            // Ignore and continue with hint only.
        }
        for (pkg in packages) {
            val context = tryCreatePackageContext(systemContext, pkg)
            if (context != null) {
                XLog.d(
                    "NotificationManagerHook: using caller context pkg=%s callerUid=%d",
                    pkg,
                    callerUid,
                )
                return context
            }
        }
        XLog.w(
            "NotificationManagerHook: failed to build caller context. callerUid=%d callerPkgHint=%s",
            callerUid,
            callerPackageHint ?: "<none>",
        )
        return null
    }

    private fun tryCreatePackageContext(systemContext: Context, packageName: String): Context? {
        return try {
            @Suppress("DEPRECATION")
            systemContext.createPackageContext(packageName, Context.CONTEXT_IGNORE_SECURITY)
        } catch (_: Throwable) {
            null
        }
    }

    private fun isPackageInstalled(systemContext: Context, packageName: String): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                systemContext.packageManager.getPackageInfo(
                    packageName,
                    PackageManager.PackageInfoFlags.of(PackageManager.MATCH_ALL.toLong()),
                )
            } else {
                @Suppress("DEPRECATION")
                systemContext.packageManager.getPackageInfo(packageName, PackageManager.MATCH_ALL)
            }
            true
        } catch (_: Throwable) {
            false
        }
    }

    private inline fun <T> withClearedCallingIdentity(reason: String, block: () -> T): T {
        val callerUid = Binder.getCallingUid()
        val callerPid = Binder.getCallingPid()
        if (callerUid != Process.SYSTEM_UID) {
            XLog.d(
                "NotificationManagerHook: clearCallingIdentity reason=%s callerUid=%d callerPid=%d",
                reason,
                callerUid,
                callerPid,
            )
        }
        val token = Binder.clearCallingIdentity()
        return try {
            block()
        } finally {
            Binder.restoreCallingIdentity(token)
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

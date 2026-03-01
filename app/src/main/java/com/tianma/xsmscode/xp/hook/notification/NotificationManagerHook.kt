package com.tianma.xsmscode.xp.hook.notification

import android.app.Notification
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.Process
import android.os.UserHandle
import com.github.tianma8023.xposed.smscode.BuildConfig
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
    )

    @Volatile
    private var cachedEndpoint: ModuleEndpoint? = null

    @Volatile
    private var cachedIpcToken: String? = null

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

        val eventId = buildEventId(pkg)
        val forwardIntent = Intent(PrefConst.ACTION_FORWARD_SMS)
        forwardIntent.setClassName(modulePackage, FORWARD_RECEIVER_CLASS_NAME)
        forwardIntent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
        forwardIntent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        forwardIntent.putExtra("sender", title)
        forwardIntent.putExtra("body", body)
        forwardIntent.putExtra("date", System.currentTimeMillis())
        forwardIntent.putExtra("packageName", pkg)
        forwardIntent.putExtra("msgType", "app_notify")
        forwardIntent.putExtra("forward_source", "nms_hook")
        forwardIntent.putExtra("event_id", eventId)

        val pm = systemContext.packageManager
        val appName = try {
            val info = pm.getApplicationInfo(pkg, 0)
            pm.getApplicationLabel(info).toString()
        } catch (_: Exception) {
            pkg
        }
        forwardIntent.putExtra("company", appName)

        val token = resolveIpcToken(
            systemContext = systemContext,
            endpoint = endpoint,
            callerPackageHint = pkg,
        )
        if (token.isBlank()) {
            XLog.w("NotificationManagerHook: ipc token empty, skip forwarding for pkg=%s", pkg)
            return
        }
        forwardIntent.putExtra("ipc_token", token)

        XLog.i("NotificationManagerHook intercepted: pkg=%s event=%s title=%s body=%s", pkg, eventId, title, body)
        dispatchForwardBroadcast(systemContext, forwardIntent, pkg, eventId)
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
            try {
                val prefProviderPackage = resolveProviderPackage(systemContext, prefAuthority)
                if (prefProviderPackage == packageName) {
                    return ModuleEndpoint(
                        packageName = packageName,
                        prefAuthority = prefAuthority,
                    )
                }
                if (fallbackCandidate == null && isPackageInstalled(systemContext, packageName)) {
                    fallbackCandidate = packageName
                }
                XLog.w(
                    "NotificationManagerHook: candidate %s rejected. prefProvider=%s",
                    packageName,
                    prefProviderPackage ?: "<null>",
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
        modulePackageName = endpoint.packageName,
        authority = endpoint.prefAuthority,
        typePath = "string",
        key = key,
        defaultValue = defaultValue,
        callerPackageHint = callerPackageHint,
    )

    private fun resolveIpcToken(
        systemContext: Context,
        endpoint: ModuleEndpoint,
        callerPackageHint: String? = null,
    ): String {
        val freshToken = queryPrefString(
            systemContext = systemContext,
            endpoint = endpoint,
            key = PrefConst.KEY_IPC_TOKEN,
            defaultValue = "",
            callerPackageHint = callerPackageHint,
        )
        if (freshToken.isNotBlank()) {
            if (cachedIpcToken != freshToken) {
                XLog.d("NotificationManagerHook: ipc token refreshed from provider")
            }
            cachedIpcToken = freshToken
            return freshToken
        }

        val cached = cachedIpcToken
        if (!cached.isNullOrBlank()) {
            XLog.w(
                "NotificationManagerHook: ipc token provider unavailable, using cached token. caller=%s",
                callerPackageHint ?: "<none>",
            )
            return cached
        }
        return ""
    }

    private fun queryPrefStringValue(
        systemContext: Context,
        modulePackageName: String,
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

        val fallbackContext = tryCreatePackageContext(systemContext, modulePackageName)
        if (fallbackContext != null) {
            val fallback = withClearedCallingIdentity("queryPrefFallback:$authority/$key") {
                queryPrefStringInternal(fallbackContext, uri, defaultValue)
            }
            if (fallback != null) {
                XLog.d(
                    "NotificationManagerHook: query pref fallback succeeded. authority=%s key=%s module=%s caller=%s",
                    authority,
                    key,
                    modulePackageName,
                    callerPackageHint ?: "<none>",
                )
                return fallback
            }
        }
        XLog.w(
            "NotificationManagerHook: query pref unavailable. authority=%s key=%s module=%s caller=%s",
            authority,
            key,
            modulePackageName,
            callerPackageHint ?: "<none>",
        )
        return defaultValue
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

    private fun dispatchForwardBroadcast(
        systemContext: Context,
        intent: Intent,
        sourcePackage: String,
        eventId: String,
    ) {
        withClearedCallingIdentity("sendBroadcast:$sourcePackage#$eventId") {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1) {
                systemContext.sendBroadcast(intent)
                return@withClearedCallingIdentity
            }

            val targetUsers = resolveTargetUsers(Binder.getCallingUid())
            for (userHandle in targetUsers) {
                try {
                    sendBroadcastAsUser(systemContext, intent, userHandle, sourcePackage, eventId)
                    return@withClearedCallingIdentity
                } catch (t: Throwable) {
                    XLog.w(
                        "NotificationManagerHook: sendBroadcastAsUser failed. pkg=%s event=%s user=%s err=%s",
                        sourcePackage,
                        eventId,
                        describeUserHandle(userHandle),
                        t.message ?: t.javaClass.simpleName,
                    )
                }
            }

            XLog.w(
                "NotificationManagerHook: fallback sendBroadcast without user. pkg=%s event=%s",
                sourcePackage,
                eventId,
            )
            systemContext.sendBroadcast(intent)
        }
    }

    private fun sendBroadcastAsUser(
        systemContext: Context,
        intent: Intent,
        userHandle: UserHandle,
        sourcePackage: String,
        eventId: String,
    ) {
        val sendIntent = Intent(intent)
        if (BuildConfig.DEBUG) {
            val resultReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, callbackIntent: Intent?) {
                    XLog.i(
                        "NotificationManagerHook: ordered ack pkg=%s event=%s user=%s resultCode=%d resultData=%s extras=%s",
                        sourcePackage,
                        eventId,
                        describeUserHandle(userHandle),
                        resultCode,
                        resultData ?: "<null>",
                        getResultExtras(true)?.toString() ?: "<null>",
                    )
                }
            }
            systemContext.sendOrderedBroadcastAsUser(
                sendIntent,
                userHandle,
                null,
                resultReceiver,
                null,
                0,
                null,
                null,
            )
        } else {
            systemContext.sendBroadcastAsUser(sendIntent, userHandle)
        }
        XLog.d(
            "NotificationManagerHook: broadcast sent. pkg=%s event=%s user=%s ordered=%s",
            sourcePackage,
            eventId,
            describeUserHandle(userHandle),
            BuildConfig.DEBUG,
        )
    }

    private fun resolveTargetUsers(callerUid: Int): List<UserHandle> {
        val result = ArrayList<UserHandle>(2)
        runCatching {
            if (callerUid >= 0) {
                result.add(UserHandle.getUserHandleForUid(callerUid))
            }
        }.onFailure {
            XLog.w("NotificationManagerHook: resolve caller user failed. callerUid=%d", callerUid)
        }
        runCatching {
            val allUserHandle = UserHandle::class.java.getField("ALL").get(null) as? UserHandle
            if (allUserHandle != null && result.none { it == allUserHandle }) {
                result.add(allUserHandle)
            }
        }
        if (result.isEmpty()) {
            runCatching {
                val owner = UserHandle::class.java.getField("OWNER").get(null) as? UserHandle
                if (owner != null) result.add(owner)
            }
        }
        return result
    }

    private fun describeUserHandle(userHandle: UserHandle): String {
        return runCatching {
            val method = UserHandle::class.java.getMethod("getIdentifier")
            method.invoke(userHandle)?.toString() ?: userHandle.toString()
        }.getOrDefault(userHandle.toString())
    }

    private fun buildEventId(packageName: String): String {
        val now = System.currentTimeMillis().toString(36)
        val suffix = kotlin.math.abs((packageName + now).hashCode()).toString(36)
        return "nms_${now}_$suffix"
    }

    companion object {
        private const val FORWARD_RECEIVER_CLASS_NAME = "com.github.magisk317.smscode.receiver.ForwardReceiver"
    }
}

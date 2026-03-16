package com.github.magisk317.smscode.xp.hook.permission

import android.os.UserHandle
import com.github.magisk317.smscode.common.constant.PermConst.PACKAGE_PERMISSIONS
import com.github.magisk317.smscode.common.utils.XLog
import de.robv.android.xposed.XposedHelpers

/**
 * Permission grant helpers for Android 16+ hooks.
 */
object PermissionGrantHelper36 {

    // HyperOS 3 adaptation:
    // model=25113PN0EC, build=OS3.0.44.0.WPCCNXM, device=pudding (Android 16 / SDK 36)

    fun grantAllTargetPermissions(pms: Any) {
        XLog.d("System ready - granting permissions for target packages")
        val resolvedPms = resolvePermissionManagerService(pms) ?: pms
        val userIds = try {
            getAllUserIds(resolvedPms)
        } catch (e: Throwable) {
            XLog.w("Cannot get user IDs, using default user 0", e)
            intArrayOf(0)
        }

        for ((packageName, permissions) in PACKAGE_PERMISSIONS) {
            for (userId in userIds) {
                grantPermissionsForPackage(pms, packageName, permissions, userId)
            }
        }
    }

    fun afterOnPackageInstalled(param: de.robv.android.xposed.XC_MethodHook.MethodHookParam, pmsOverride: Any? = null) {
        val packageName = resolvePackageName(param.args) ?: return
        val permissions = PACKAGE_PERMISSIONS[packageName] ?: return
        val rawUserId = tryResolveRawUserId(param.args)
        val pms = pmsOverride ?: param.thisObject
        val resolvedPms = resolvePermissionManagerService(pms) ?: pms

        val userIds = if (rawUserId == USER_ALL) {
            try {
                getAllUserIds(resolvedPms)
            } catch (e: Throwable) {
                XLog.w("Cannot get user IDs, using default user 0", e)
                intArrayOf(0)
            }
        } else if (rawUserId != null) {
            intArrayOf(rawUserId)
        } else {
            intArrayOf(0)
        }

        for (userId in userIds) {
            grantPermissionsForPackage(pms, packageName, permissions, userId)
        }
    }

    fun resolvePackageName(args: Array<Any?>): String? {
        for (arg in args) {
            if (arg == null) continue
            val pkgName = try {
                XposedHelpers.callMethod(arg, "getPackageName") as? String
            } catch (_: Throwable) {
                null
            }
            if (!pkgName.isNullOrEmpty()) {
                return pkgName
            }
        }
        PermissionDebugProbe.dumpArgs("resolvePackageName: no packageName", args)
        XLog.w("onPackageInstalled: cannot resolve package name from args")
        return null
    }

    fun tryResolveRawUserId(args: Array<Any?>): Int? {
        var candidate: Int? = null
        args.forEach { arg ->
            when (arg) {
                is Int -> candidate = arg
                is UserHandle -> {
                    candidate = try {
                        XposedHelpers.callMethod(arg, "getIdentifier") as Int
                    } catch (_: Throwable) {
                        null
                    }
                }
            }
        }
        return candidate
    }

    fun getAllUserIds(pms: Any): IntArray {
        tryGetUserIdsFromTarget(pms)?.let { return it }

        tryResolveUserIdsFromUserManager(pms)?.let { return it }

        val pmInt = tryGetFieldQuiet(pms, "mPackageManagerInt")
        tryGetUserIdsFromTarget(pmInt)?.let { return it }

        val pmInternal = tryGetFieldQuiet(pms, "packageManagerInternal")
        tryGetUserIdsFromTarget(pmInternal)?.let { return it }

        val pmLocal = tryGetFieldQuiet(pms, "packageManagerLocal")
        tryGetUserIdsFromTarget(pmLocal)?.let { return it }

        return intArrayOf(0)
    }

    fun grantPermissionsForPackage(
        pms: Any,
        packageName: String,
        permissions: List<String>,
        userId: Int,
    ) {
        val impl = try {
            XposedHelpers.getObjectField(pms, "mPermissionManagerServiceImpl")
        } catch (e: Throwable) {
            PermissionDebugProbe.logFailure("grantPermissionsForPackage: mPermissionManagerServiceImpl", e, pms)
            pms
        }

        for (permission in permissions) {
            if (permission == PERM_KILL_BACKGROUND_PROCESSES) {
                // HyperOS 3 adaptation:
                // model=25113PN0EC, build=OS3.0.44.0.WPCCNXM, device=pudding (Android 16 / SDK 36)
                // Provider self-kill is the primary strategy on this ROM.
                XLog.w("Skip granting %s to %s: provider self-kill is primary on HyperOS 3", permission, packageName)
                continue
            }
            val runtimeAttempt = tryGrantRuntimePermission(impl, packageName, permission, userId)
            if (runtimeAttempt.success) {
                XLog.d("Granted $permission to $packageName (user $userId) via runtime")
                continue
            }
            val reason = runtimeAttempt.error ?: "unknown"
            XLog.w("Cannot grant $permission to $packageName: $reason")
        }
    }

    private data class GrantAttempt(
        val success: Boolean,
        val error: String?,
    )

    private fun tryGrantRuntimePermission(
        impl: Any,
        packageName: String,
        permission: String,
        userId: Int,
    ): GrantAttempt {
        return try {
            // Android 16 grantRuntimePermission signature:
            // grantRuntimePermission(String packageName, String permName,
            //     String persistentDeviceId, int userId)
            XposedHelpers.callMethod(
                impl,
                "grantRuntimePermission",
                packageName,
                permission,
                PERSISTENT_DEVICE_ID_DEFAULT,
                userId,
            )
            GrantAttempt(true, null)
        } catch (e: Throwable) {
            GrantAttempt(false, e.message ?: e.javaClass.simpleName)
        }
    }

    private fun resolvePermissionManagerService(pms: Any): Any? {
        val name = pms.javaClass.name
        if (name.contains("PermissionManagerService")) {
            return pms
        }
        return runCatching {
            val loader = pms.javaClass.classLoader
            val localServices = XposedHelpers.findClass("com.android.server.LocalServices", loader)
            val internalClazz = XposedHelpers.findClass(
                "com.android.server.pm.permission.PermissionManagerServiceInternal",
                loader,
            )
            val internal = XposedHelpers.callStaticMethod(localServices, "getService", internalClazz)
            if (internal != null) {
                runCatching { XposedHelpers.getObjectField(internal, "this$0") }.getOrDefault(internal)
            } else {
                null
            }
        }.getOrNull()
    }

    private fun tryResolveUserIdsFromUserManager(pms: Any): IntArray? {
        val fields = listOf("userManagerInternal", "userManagerService")
        for (fieldName in fields) {
            val userManager = tryGetField(pms, fieldName) ?: continue
            tryGetUserIdsFromTarget(userManager)?.let { return it }
        }
        return null
    }

    private fun parseUserIds(result: Any?): IntArray? {
        if (result == null) return null
        if (result is IntArray) return result
        if (result is Array<*>) {
            val list = result.mapNotNull { extractUserId(it) }
            if (list.isNotEmpty()) return list.toIntArray()
        }
        if (result is List<*>) {
            val list = result.mapNotNull { extractUserId(it) }
            if (list.isNotEmpty()) return list.toIntArray()
        }
        return null
    }

    private fun extractUserId(item: Any?): Int? {
        if (item == null) return null
        if (item is Int) return item
        return runCatching {
            XposedHelpers.getIntField(item, "id")
        }.getOrElse {
            runCatching { XposedHelpers.callMethod(item, "getIdentifier") as Int }.getOrNull()
                ?: runCatching { XposedHelpers.callMethod(item, "getId") as Int }.getOrNull()
        }
    }

    private fun tryGetField(target: Any, fieldName: String): Any? {
        return runCatching { XposedHelpers.getObjectField(target, fieldName) }.getOrNull()
            ?: runCatching {
                PermissionDebugProbe.logFailure("getField:$fieldName", Throwable("missing"), target)
                null
            }.getOrNull()
    }

    private fun tryGetFieldQuiet(target: Any, fieldName: String): Any? {
        return runCatching { XposedHelpers.getObjectField(target, fieldName) }.getOrNull()
    }

    private fun tryGetUserIdsFromTarget(target: Any?): IntArray? {
        if (target == null) return null
        val direct = tryCallMethod(target, "getAllUserIds")
            ?: tryCallMethod(target, "getUserIds")
            ?: tryCallMethod(target, "getUsers")
            ?: tryCallMethod(target, "getUserInfos")
            ?: tryCallMethod(target, "getUsers", true)
            ?: tryCallMethod(target, "getUserIds", true)
        return parseUserIds(direct)
    }
    private fun tryCallMethod(target: Any, methodName: String, vararg args: Any?): Any? {
        return runCatching { XposedHelpers.callMethod(target, methodName, *args) }.getOrNull()
    }

    private const val PERSISTENT_DEVICE_ID_DEFAULT = "default:0"
    private const val USER_ALL = -1
    private const val PERM_KILL_BACKGROUND_PROCESSES = "android.permission.KILL_BACKGROUND_PROCESSES"
}

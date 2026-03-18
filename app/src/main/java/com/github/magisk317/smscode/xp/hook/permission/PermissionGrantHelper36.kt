package com.github.magisk317.smscode.xp.hook.permission

import android.os.UserHandle
import com.github.magisk317.smscode.common.constant.PermConst.PACKAGE_PERMISSIONS
import com.github.magisk317.smscode.common.utils.XLog
import com.github.magisk317.smscode.xp.hookapi.HookHelpers
import com.github.magisk317.smscode.xp.hookapi.MethodHookParam
import java.lang.reflect.Method

/**
 * Permission grant helpers for Android 16+ hooks.
 */
object PermissionGrantHelper36 {

    // HyperOS 3 adaptation:
    // model=25113PN0EC, build=OS3.0.44.0.WPCCNXM, device=pudding (Android 16 / SDK 36)
    @Volatile
    private var cachedGrantRuntimeMethod: Method? = null
    @Volatile
    private var loggedGrantRuntimeSignatures = false
    @Volatile
    private var loggedPermissionImplMissing = false

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

    fun afterOnPackageInstalled(param: MethodHookParam, pmsOverride: Any? = null) {
        val packageName = resolvePackageName(param.args) ?: return
        val permissions = PACKAGE_PERMISSIONS[packageName] ?: return
        val rawUserId = tryResolveRawUserId(param.args)
        val pms = (pmsOverride ?: param.thisObject) ?: return
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
                HookHelpers.callMethod(arg, "getPackageName") as? String
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
                        HookHelpers.callMethod(arg, "getIdentifier") as Int
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
            HookHelpers.getObjectField(pms, "mPermissionManagerServiceImpl") ?: pms
        } catch (e: Throwable) {
            if (!loggedPermissionImplMissing) {
                loggedPermissionImplMissing = true
                PermissionDebugProbe.logFailure("grantPermissionsForPackage: mPermissionManagerServiceImpl", e, pms)
            }
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
        val cached = cachedGrantRuntimeMethod
        if (cached != null) {
            val args = buildGrantRuntimeArgs(cached.parameterTypes, packageName, permission, userId)
            if (args != null) {
                return runCatching {
                    cached.invoke(impl, *args)
                    GrantAttempt(true, null)
                }.getOrElse {
                    cachedGrantRuntimeMethod = null
                    GrantAttempt(false, it.message ?: it.javaClass.simpleName)
                }
            } else {
                cachedGrantRuntimeMethod = null
            }
        }

        val clazz = impl.javaClass
        val methods = collectMethods(clazz, "grantRuntimePermission")
        for (method in methods) {
            val args = buildGrantRuntimeArgs(method.parameterTypes, packageName, permission, userId) ?: continue
            val attempt = runCatching {
                method.invoke(impl, *args)
                cachedGrantRuntimeMethod = method
                GrantAttempt(true, null)
            }.getOrElse {
                GrantAttempt(false, it.message ?: it.javaClass.simpleName)
            }
            if (attempt.success) {
                return attempt
            }
        }

        if (!loggedGrantRuntimeSignatures) {
            loggedGrantRuntimeSignatures = true
            PermissionDebugProbe.dumpMethodSignatures(
                "grantRuntimePermission signatures",
                clazz,
                "grantRuntimePermission",
            )
        }
        return GrantAttempt(false, "No suitable method for ${clazz.name}#grantRuntimePermission")
    }

    private fun resolvePermissionManagerService(pms: Any): Any? {
        val name = pms.javaClass.name
        if (name.contains("PermissionManagerService") || name.contains("PermissionService")) {
            return pms
        }
        return runCatching {
            val loader = pms.javaClass.classLoader
            val localServices = HookHelpers.findClass("com.android.server.LocalServices", loader)
            val internalClazz = HookHelpers.findClass(
                "com.android.server.pm.permission.PermissionManagerServiceInternal",
                loader,
            )
            val internal = HookHelpers.callStaticMethod(localServices, "getService", internalClazz)
            if (internal != null) {
                runCatching { HookHelpers.getObjectField(internal, "this$0") }.getOrDefault(internal)
            } else {
                val permissionServiceClazz = HookHelpers.findClassIfExists(
                    "com.android.server.permission.access.permission.PermissionService",
                    loader,
                )
                if (permissionServiceClazz != null) {
                    HookHelpers.callStaticMethod(localServices, "getService", permissionServiceClazz)
                } else {
                    null
                }
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
            HookHelpers.getIntField(item, "id")
        }.getOrElse {
            runCatching { HookHelpers.callMethod(item, "getIdentifier") as Int }.getOrNull()
                ?: runCatching { HookHelpers.callMethod(item, "getId") as Int }.getOrNull()
        }
    }

    private fun tryGetField(target: Any, fieldName: String): Any? {
        return runCatching { HookHelpers.getObjectField(target, fieldName) }.getOrNull()
            ?: runCatching {
                PermissionDebugProbe.logFailure("getField:$fieldName", Throwable("missing"), target)
                null
            }.getOrNull()
    }

    private fun tryGetFieldQuiet(target: Any, fieldName: String): Any? {
        return runCatching { HookHelpers.getObjectField(target, fieldName) }.getOrNull()
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
        return runCatching { HookHelpers.callMethod(target, methodName, *args) }.getOrNull()
    }

    private const val PERSISTENT_DEVICE_ID_DEFAULT = "default:0"
    private const val USER_ALL = -1
    private const val PERM_KILL_BACKGROUND_PROCESSES = "android.permission.KILL_BACKGROUND_PROCESSES"

    private fun buildGrantRuntimeArgs(
        parameterTypes: Array<Class<*>>,
        packageName: String,
        permission: String,
        userId: Int,
    ): Array<Any?>? {
        val stringValues = ArrayDeque<Any?>(listOf(packageName, permission, PERSISTENT_DEVICE_ID_DEFAULT, null))
        val intValues = ArrayDeque<Any?>(listOf(userId, 0))
        val longValues = ArrayDeque<Any?>(listOf(0L))
        val boolValues = ArrayDeque<Any?>(listOf(false))
        val args = arrayOfNulls<Any?>(parameterTypes.size)
        for (i in parameterTypes.indices) {
            val type = parameterTypes[i]
            when {
                type == String::class.java -> args[i] = if (stringValues.isNotEmpty()) stringValues.removeFirst() else null
                type == Int::class.javaPrimitiveType || type == Int::class.javaObjectType ->
                    args[i] = if (intValues.isNotEmpty()) intValues.removeFirst() else 0
                type == Long::class.javaPrimitiveType || type == Long::class.javaObjectType ->
                    args[i] = if (longValues.isNotEmpty()) longValues.removeFirst() else 0L
                type == Boolean::class.javaPrimitiveType || type == Boolean::class.javaObjectType ->
                    args[i] = if (boolValues.isNotEmpty()) boolValues.removeFirst() else false
                else -> args[i] = null
            }
        }
        return args
    }

    private fun collectMethods(clazz: Class<*>, methodName: String): List<Method> {
        val methods = mutableListOf<Method>()
        var current: Class<*>? = clazz
        while (current != null) {
            current.declaredMethods
                .filter { it.name == methodName }
                .forEach { method ->
                    method.isAccessible = true
                    methods += method
                }
            current = current.superclass
        }
        return methods
    }
}

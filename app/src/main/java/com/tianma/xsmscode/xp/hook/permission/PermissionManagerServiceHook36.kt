package com.tianma.xsmscode.xp.hook.permission

import android.os.Build
import android.os.UserHandle
import com.tianma.xsmscode.common.constant.PermConst.PACKAGE_PERMISSIONS
import com.tianma.xsmscode.common.utils.XLog
import com.tianma.xsmscode.xp.helper.MethodHookWrapper
import com.tianma.xsmscode.xp.hook.BaseSubHook
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

/**
 * Since Android 16 (API 36+)
 *
 * In Android 16 QPR2, PermissionManagerServiceImpl was removed.
 * The implementation was merged into PermissionManagerService which
 * now extends IPermissionManager.Stub directly and delegates to
 * PermissionManagerServiceInterface via mPermissionManagerServiceImpl.
 *
 * Key changes from Android 14:
 * - Class: PermissionManagerServiceImpl -> PermissionManagerService
 * - Method: restorePermissionState() -> removed (no equivalent)
 * - Fields: mState, mRegistry -> removed
 * - PermissionCallback inner class -> removed
 * - grantRuntimePermission now takes a persistentDeviceId parameter
 *
 * Strategy: Hook onPackageInstalled() and use grantRuntimePermission()
 * to grant the needed permissions after a package is installed/updated.
 * Also hook onSystemReady() to grant permissions for already-installed packages.
 */
class PermissionManagerServiceHook36(classLoader: ClassLoader) : BaseSubHook(classLoader) {

    override fun startHook() {
        try {
            hookOnSystemReady()
            hookOnPackageInstalled()
        } catch (e: Throwable) {
            XLog.e("Failed to hook PermissionManagerService for Android 16+", e)
        }
    }

    /**
     * Hook onSystemReady() to grant permissions for already-installed packages at boot.
     */
    private fun hookOnSystemReady() {
        XLog.d("Hooking onSystemReady() for Android 36+")
        val pmsClass = XposedHelpers.findClass(CLASS_PMS, mClassLoader)
        val method = XposedHelpers.findMethodExactIfExists(pmsClass, "onSystemReady")
        if (method == null) {
            XLog.e("Cannot find onSystemReady in PermissionManagerService")
            return
        }

        XposedBridge.hookMethod(
            method,
            object : MethodHookWrapper() {
                @Throws(Throwable::class)
                override fun after(param: MethodHookParam) {
                    grantAllTargetPermissions(param.thisObject)
                }
            },
        )
    }

    /**
     * Hook onPackageInstalled() to grant permissions when a target package
     * is installed or updated after boot.
     */
    private fun hookOnPackageInstalled() {
        XLog.d("Hooking onPackageInstalled() for Android 36+")
        val pmsClass = XposedHelpers.findClass(CLASS_PMS, mClassLoader)
        val androidPackageClass = XposedHelpers.findClass(CLASS_ANDROID_PACKAGE, mClassLoader)

        val method = pmsClass.declaredMethods.firstOrNull {
            it.name == "onPackageInstalled" &&
                it.parameterTypes.isNotEmpty() &&
                it.parameterTypes[0] == androidPackageClass
        }

        if (method == null) {
            XLog.w("Cannot find onPackageInstalled in PermissionManagerService")
            return
        }

        XposedBridge.hookMethod(
            method,
            object : MethodHookWrapper() {
                @Throws(Throwable::class)
                override fun after(param: MethodHookParam) {
                    afterOnPackageInstalled(param)
                }
            },
        )
    }

    /**
     * Grant permissions for all target packages.
     * Called once after system is ready.
     */
    private fun grantAllTargetPermissions(pms: Any) {
        XLog.d("System ready - granting permissions for target packages")
        val userIds = try {
            getAllUserIds(pms)
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

    /**
     * After a package is installed, check if it's a target and grant permissions.
     */
    @Suppress("UNCHECKED_CAST")
    private fun afterOnPackageInstalled(param: XC_MethodHook.MethodHookParam) {
        val pkg = param.args[0]
        val packageName = XposedHelpers.callMethod(pkg, "getPackageName") as String

        val permissions = PACKAGE_PERMISSIONS[packageName] ?: return
        if (param.args.size < 4) {
            XLog.w("onPackageInstalled: args size < 4")
            return
        }

        // param.args[3] = rawUserId
        val rawUserId = param.args[3] as? Int
        if (rawUserId == null) {
            XLog.w("onPackageInstalled: args[3] is not Int")
            return
        }
        val pms = param.thisObject

        val userIds = if (rawUserId == USER_ALL) {
            try {
                getAllUserIds(pms)
            } catch (e: Throwable) {
                XLog.w("Cannot get user IDs, using default user 0", e)
                intArrayOf(0)
            }
        } else {
            intArrayOf(rawUserId)
        }

        for (userId in userIds) {
            grantPermissionsForPackage(pms, packageName, permissions, userId)
        }
    }

    /**
     * Grant a list of permissions to a package via grantRuntimePermission().
     * Uses the mPermissionManagerServiceImpl field to access the actual implementation.
     */
    private fun grantPermissionsForPackage(
        pms: Any,
        packageName: String,
        permissions: List<String>,
        userId: Int,
    ) {
        val impl = try {
            XposedHelpers.getObjectField(pms, "mPermissionManagerServiceImpl")
        } catch (e: Throwable) {
            XLog.w("Cannot access mPermissionManagerServiceImpl, using PMS directly", e)
            pms
        }

        for (permission in permissions) {
            try {
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
                XLog.d("Granted $permission to $packageName (user $userId)")
            } catch (e: Throwable) {
                // Permission might already be granted, or it's a signature permission
                // that can't be granted via grantRuntimePermission. This is expected
                // for some permission types.
                XLog.w("Cannot grant $permission to $packageName: ${e.message}")
            }
        }
    }

    /**
     * Get all user IDs via PackageManagerInternal.
     * Check if it returns IntArray or List.
     */
    private fun getAllUserIds(pms: Any): IntArray {
        val pmInt = XposedHelpers.getObjectField(pms, "mPackageManagerInt")
        val result = XposedHelpers.callMethod(pmInt, "getUsers", true)

        if (result is IntArray) {
            return result
        }

        if (result is List<*>) {
            val list = ArrayList<Int>()
            for (item in result) {
                if (item != null) {
                    // item is android.content.pm.UserInfo
                    val id = XposedHelpers.getIntField(item, "id")
                    list.add(id)
                }
            }
            return list.toIntArray()
        }

        return intArrayOf(0)
    }

    companion object {
        private const val CLASS_PMS =
            "com.android.server.pm.permission.PermissionManagerService"
        private const val CLASS_ANDROID_PACKAGE =
            "com.android.server.pm.pkg.AndroidPackage"
        // VirtualDeviceManager.PERSISTENT_DEVICE_ID_DEFAULT
        private const val PERSISTENT_DEVICE_ID_DEFAULT = "default:0"
        private const val USER_ALL = -1
    }
}

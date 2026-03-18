package com.github.magisk317.smscode.xp.hook.permission

import androidx.annotation.RequiresApi
import com.github.magisk317.smscode.common.constant.PermConst.PACKAGE_PERMISSIONS
import com.github.magisk317.smscode.common.utils.XLog
import com.github.magisk317.smscode.xp.helper.MethodHookWrapper
import com.github.magisk317.smscode.xp.hook.BaseSubHook
import com.github.magisk317.smscode.xp.hookapi.MethodHook
import com.github.magisk317.smscode.xp.hookapi.HookBridge
import com.github.magisk317.smscode.xp.hookapi.HookHelpers
import java.lang.reflect.Method
import com.github.magisk317.smscode.xp.hookapi.MethodHookParam

/**
 * Since Android 11(API 30)<br/>
 * Hook com.android.server.pm.permission.PermissionManagerService
 */
class PermissionManagerServiceHook30(classLoader: ClassLoader) : BaseSubHook(classLoader) {

    @RequiresApi(30)
    override fun startHook() {
        try {
            hookGrantPermissions()
        } catch (e: Throwable) {
            XLog.e("Failed to hook PermissionManagerService", e)
        }
    }

    private fun hookGrantPermissions() {
        XLog.d("Hooking grantPermissions() for Android 30+")
        val method = findTargetMethod()
        if (method == null) {
            XLog.e("Cannot find the method to grant relevant permission")
            return
        }
        HookBridge.hookMethod(
            method,
            object : MethodHookWrapper() {
                @Throws(Throwable::class)
                override fun after(param: MethodHookParam) {
                    afterGrantPermissionsSinceAndroid11(param)
                }
            },
        )
    }

    private fun findTargetMethod(): Method? {
        val pmsClass = HookHelpers.findClass(CLASS_PERMISSION_MANAGER_SERVICE, mClassLoader)
        val androidPackageClass = HookHelpers.findClass(CLASS_ANDROID_PACKAGE, mClassLoader)
        val callbackClass = HookHelpers.findClassIfExists(CLASS_PERMISSION_CALLBACK, mClassLoader)

        var method = HookHelpers.findMethodExactIfExists(
            pmsClass,
            "restorePermissionState",
            /* AndroidPackage pkg   */
            androidPackageClass,
            /* boolean replace             */
            Boolean::class.javaPrimitiveType,
            /* String packageOfInterest    */
            String::class.java,
            /* PermissionCallback callback */
            callbackClass,
        )

        if (method == null) { // method restorePermissionState() not found
            val methods = HookHelpers.findMethodsByExactParameters(
                pmsClass,
                Void.TYPE,
                /* AndroidPackage pkg   */
                androidPackageClass,
                /* boolean replace             */
                Boolean::class.javaPrimitiveType,
                /* String packageOfInterest    */
                String::class.java,
                /* PermissionCallback callback */
                callbackClass,
            )
            if (methods.isNotEmpty()) {
                method = methods[0]
            }
        }
        if (method == null) {
            PermissionDebugProbe.dumpClass("PermissionManagerServiceHook30 missing restorePermissionState", pmsClass)
        }
        return method
    }

    private fun afterGrantPermissionsSinceAndroid11(param: MethodHookParam) {
        // com.android.server.pm.parsing.pkg.AndroidPackage 对象
        val pkg = param.args[0]
        val packageNameInPkg = HookHelpers.callMethod(pkg, "getPackageName") as String

        for (packageName in PACKAGE_PERMISSIONS.keys) {
            if (packageName == packageNameInPkg) {
                XLog.d("PackageName: %s", packageName)

                // PermissionManagerService 对象
                val permissionManagerService = param.thisObject
                // PackageManagerInternal 对象 mPackageManagerInt
                val mPackageManagerInt = HookHelpers.getObjectField(permissionManagerService, "mPackageManagerInt")

                // PackageSetting 对象 ps
                val ps = HookHelpers.callMethod(mPackageManagerInt, "getPackageSetting", packageName)

                // com.android.server.pm.permission.PermissionsState 对象
                val permissionsState = HookHelpers.callMethod(ps, "getPermissionsState")

                // Manifest.xml 中声明的permission列表
                val requestedPermissions = HookHelpers.callMethod(pkg, "getRequestedPermissions") as? List<*>

                // com.android.server.pm.permission.PermissionSettings mSettings 对象
                val settings = HookHelpers.getObjectField(permissionManagerService, "mSettings")
                // ArrayMap<String, com.android.server.pm.permission.BasePermission> mPermissions 对象
                val permissions = HookHelpers.getObjectField(settings, "mPermissions")

                val permissionsToGrant = PACKAGE_PERMISSIONS[packageName] ?: continue
                for (permissionToGrant in permissionsToGrant) {
                    if (requestedPermissions?.contains(permissionToGrant) != true) {
                        val granted = HookHelpers.callMethod(
                            permissionsState,
                            "hasInstallPermission",
                            permissionToGrant,
                        ) as Boolean
                        if (!granted) {
                            val bpToGrant = HookHelpers.callMethod(permissions, "get", permissionToGrant)
                            val result = HookHelpers.callMethod(
                                permissionsState,
                                "grantInstallPermission",
                                bpToGrant,
                            ) as Int
                            XLog.d("Add $bpToGrant; result = $result")
                        } else {
                            XLog.d("Already have $permissionToGrant permission")
                        }
                    }
                }
            }
        }
    }

    companion object {
        private const val CLASS_PERMISSION_MANAGER_SERVICE = "com.android.server.pm.permission.PermissionManagerService"
        private const val CLASS_ANDROID_PACKAGE = "com.android.server.pm.parsing.pkg.AndroidPackage"
        private const val CLASS_PERMISSION_CALLBACK = "com.android.server.pm.permission.PermissionManagerServiceInternal.PermissionCallback"
    }
}

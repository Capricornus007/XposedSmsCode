package com.github.magisk317.smscode.xp.hook.permission

import com.github.magisk317.smscode.common.utils.XLog
import com.github.magisk317.smscode.xp.helper.MethodHookWrapper
import com.github.magisk317.smscode.xp.hook.BaseSubHook
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
        val methods = pmsClass.declaredMethods.filter { it.name == "onSystemReady" || it.name == "systemReady" }
        if (methods.isEmpty()) {
            XLog.w("Cannot find onSystemReady/systemReady in PermissionManagerService")
            PermissionDebugProbe.dumpClass("PermissionManagerServiceHook36 onSystemReady missing", pmsClass)
            return
        }
        methods.forEach { method ->
            XposedBridge.hookMethod(
                method,
                object : MethodHookWrapper() {
                    @Throws(Throwable::class)
                    override fun after(param: MethodHookParam) {
                        PermissionGrantHelper36.grantAllTargetPermissions(param.thisObject)
                    }
                },
            )
        }
    }

    /**
     * Hook onPackageInstalled() to grant permissions when a target package
     * is installed or updated after boot.
     */
    private fun hookOnPackageInstalled() {
        XLog.d("Hooking onPackageInstalled() for Android 36+")
        val pmsClass = XposedHelpers.findClass(CLASS_PMS, mClassLoader)
        val methods = pmsClass.declaredMethods.filter { method ->
            method.name in PACKAGE_INSTALL_CALLBACK_NAMES &&
                method.parameterTypes.isNotEmpty() &&
                method.parameterTypes.any { it.name == CLASS_ANDROID_PACKAGE || it.name.contains("AndroidPackage") }
        }
        if (methods.isEmpty()) {
            XLog.w("Cannot find package-installed callback in PermissionManagerService")
            PermissionDebugProbe.dumpClass("PermissionManagerServiceHook36 package-installed missing", pmsClass)
            return
        }
        methods.forEach { method ->
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
    }

    /**
     * After a package is installed, check if it's a target and grant permissions.
     */
    private fun afterOnPackageInstalled(param: XC_MethodHook.MethodHookParam) {
        PermissionGrantHelper36.afterOnPackageInstalled(param)
    }

    companion object {
        private const val CLASS_PMS =
            "com.android.server.pm.permission.PermissionManagerService"
        private const val CLASS_ANDROID_PACKAGE =
            "com.android.server.pm.pkg.AndroidPackage"
        private val PACKAGE_INSTALL_CALLBACK_NAMES = setOf(
            "onPackageInstalled",
            "onPackageAdded",
        )
    }
}

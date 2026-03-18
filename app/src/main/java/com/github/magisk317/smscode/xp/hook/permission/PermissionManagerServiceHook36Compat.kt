package com.github.magisk317.smscode.xp.hook.permission

import com.github.magisk317.smscode.common.constant.PermConst.PACKAGE_PERMISSIONS
import com.github.magisk317.smscode.common.utils.XLog
import com.github.magisk317.smscode.xp.helper.MethodHookWrapper
import com.github.magisk317.smscode.xp.hook.BaseSubHook
import com.github.magisk317.smscode.xp.hookapi.MethodHook
import com.github.magisk317.smscode.xp.hookapi.HookBridge
import com.github.magisk317.smscode.xp.hookapi.HookHelpers
import java.util.concurrent.atomic.AtomicBoolean
import com.github.magisk317.smscode.xp.hookapi.MethodHookParam

/**
 * Android 16+ permission hook compatibility layer (ROM-aware by capability detection).
 */
class PermissionManagerServiceHook36Compat(classLoader: ClassLoader) : BaseSubHook(classLoader) {

    private val bootPhaseGranted = AtomicBoolean(false)
    private val systemReadyGranted = AtomicBoolean(false)

    override fun startHook() {
        var hooked = false
        hooked = hookInternalImpl() || hooked
        hooked = hookPermissionService() || hooked
        hookPermissionPolicyService()

        if (!hooked) {
            XLog.w("No permission hook target matched for Android 16+")
        }
    }

    private fun hookInternalImpl(): Boolean {
        val implClass = HookHelpers.findClassIfExists(CLASS_INTERNAL_IMPL, mClassLoader) ?: return false
        var hooked = false

        hooked = hookMethodsByName(implClass, "onSystemReady") { param ->
            val pms = param.thisObject ?: return@hookMethodsByName
            if (systemReadyGranted.compareAndSet(false, true)) {
                PermissionGrantHelper36.grantAllTargetPermissions(resolvePms(pms))
            }
        } || hooked

        hooked = hookMethodsByName(implClass, "onPackageInstalled") { param ->
            val pms = param.thisObject ?: return@hookMethodsByName
            PermissionGrantHelper36.afterOnPackageInstalled(param, resolvePms(pms))
        } || hooked

        hooked = hookMethodsByName(implClass, "onPackageAdded") { param ->
            val pms = param.thisObject ?: return@hookMethodsByName
            handlePackageAdded(param, resolvePms(pms))
        } || hooked

        if (hooked) {
            XLog.d("Hooked PermissionManagerServiceInternalImpl for Android 16+")
        }
        return hooked
    }

    private fun hookPermissionService(): Boolean {
        val serviceClass = HookHelpers.findClassIfExists(CLASS_PERMISSION_SERVICE, mClassLoader) ?: return false
        var hooked = false

        hooked = hookMethodsByName(serviceClass, "onSystemReady") { param ->
            val pms = param.thisObject ?: return@hookMethodsByName
            if (systemReadyGranted.compareAndSet(false, true)) {
                PermissionGrantHelper36.grantAllTargetPermissions(resolvePms(pms))
            }
        } || hooked

        hooked = hookMethodsByName(serviceClass, "onPackageInstalled") { param ->
            val pms = param.thisObject ?: return@hookMethodsByName
            PermissionGrantHelper36.afterOnPackageInstalled(param, resolvePms(pms))
        } || hooked

        hooked = hookMethodsByName(serviceClass, "onPackageAdded") { param ->
            val pms = param.thisObject ?: return@hookMethodsByName
            handlePackageAdded(param, resolvePms(pms))
        } || hooked

        if (hooked) {
            XLog.d("Hooked PermissionService for Android 16+")
        }
        return hooked
    }

    private fun hookPermissionPolicyService() {
        val policyClass = HookHelpers.findClassIfExists(CLASS_PERMISSION_POLICY_SERVICE, mClassLoader) ?: return
        hookMethodsByName(policyClass, "onBootPhase") { param ->
            val phase = (param.args.firstOrNull() as? Int) ?: return@hookMethodsByName
            val readyPhase = getSystemServicesReadyPhase()
            if (phase < readyPhase) return@hookMethodsByName
            if (!bootPhaseGranted.compareAndSet(false, true)) return@hookMethodsByName

            val pms = resolvePmsFromLocalServices() ?: run {
                XLog.w("PermissionPolicyService bootPhase: cannot resolve PermissionManagerService")
                return@hookMethodsByName
            }
            PermissionGrantHelper36.grantAllTargetPermissions(pms)
        }
    }

    private fun handlePackageAdded(param: MethodHookParam, pms: Any) {
        val packageName = PermissionGrantHelper36.resolvePackageName(param.args) ?: return
        val permissions = PACKAGE_PERMISSIONS[packageName] ?: return

        val rawUserId = PermissionGrantHelper36.tryResolveRawUserId(param.args)
        val userIds = if (rawUserId == USER_ALL) {
            PermissionGrantHelper36.getAllUserIds(pms)
        } else if (rawUserId != null) {
            intArrayOf(rawUserId)
        } else {
            PermissionGrantHelper36.getAllUserIds(pms)
        }

        for (userId in userIds) {
            PermissionGrantHelper36.grantPermissionsForPackage(pms, packageName, permissions, userId)
        }
    }

    private fun resolvePms(obj: Any): Any {
        return runCatching { HookHelpers.getObjectField(obj, "this$0") }.getOrNull() ?: obj
    }

    private fun resolvePmsFromLocalServices(): Any? {
        return runCatching {
            val localServices = HookHelpers.findClass(CLASS_LOCAL_SERVICES, mClassLoader)
            val internalClazz = HookHelpers.findClass(CLASS_PERMISSION_MANAGER_INTERNAL, mClassLoader)
            val internal = HookHelpers.callStaticMethod(localServices, "getService", internalClazz)
            if (internal != null) resolvePms(internal) else null
        }.getOrNull()
    }

    private fun getSystemServicesReadyPhase(): Int {
        return runCatching {
            val cls = HookHelpers.findClass(CLASS_SYSTEM_SERVICE, mClassLoader)
            HookHelpers.getStaticIntField(cls, "PHASE_SYSTEM_SERVICES_READY")
        }.getOrDefault(DEFAULT_SYSTEM_SERVICES_READY_PHASE)
    }

    private fun hookMethodsByName(
        clazz: Class<*>,
        methodName: String,
        afterHook: (MethodHookParam) -> Unit,
    ): Boolean {
        val methods = clazz.declaredMethods.filter { it.name == methodName }
        if (methods.isEmpty()) {
            PermissionDebugProbe.dumpClass("No method $methodName in ${clazz.name}", clazz)
            return false
        }

        methods.forEach { method ->
            HookBridge.hookMethod(
                method,
                object : MethodHookWrapper() {
                    override fun after(param: MethodHookParam) {
                        afterHook(param)
                    }
                },
            )
        }
        return true
    }

    companion object {
        private const val CLASS_INTERNAL_IMPL =
            "com.android.server.pm.permission.PermissionManagerService\$PermissionManagerServiceInternalImpl"
        private const val CLASS_PERMISSION_SERVICE =
            "com.android.server.permission.access.permission.PermissionService"
        private const val CLASS_PERMISSION_POLICY_SERVICE =
            "com.android.server.policy.PermissionPolicyService"
        private const val CLASS_LOCAL_SERVICES = "com.android.server.LocalServices"
        private const val CLASS_PERMISSION_MANAGER_INTERNAL =
            "com.android.server.pm.permission.PermissionManagerServiceInternal"
        private const val CLASS_SYSTEM_SERVICE = "com.android.server.SystemService"
        private const val DEFAULT_SYSTEM_SERVICES_READY_PHASE = 500
        private const val USER_ALL = -1
    }
}

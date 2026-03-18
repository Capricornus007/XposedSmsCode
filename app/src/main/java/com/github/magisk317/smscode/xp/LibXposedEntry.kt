package com.github.magisk317.smscode.xp

import com.github.tianma8023.xposed.smscode.BuildConfig
import io.github.magisk317.smscode.core.hook.BaseHook
import com.github.magisk317.smscode.xp.hook.code.SmsHandlerHook
import com.github.magisk317.smscode.xp.hook.google.GoogleMessagesHook
import com.github.magisk317.smscode.xp.hook.me.ModuleUtilsHook
import io.github.magisk317.smscode.core.hook.permission.PermissionGranterHook
import io.github.magisk317.smscode.core.hook.system.SystemInputInjectorHook
import com.github.magisk317.smscode.xp.hook.telephony.SmsProviderHook
import io.github.magisk317.smscode.core.hookapi.HookEnv
import io.github.magisk317.smscode.core.hookapi.LibXposedHookApi
import io.github.magisk317.smscode.core.hookapi.LoadParam
import io.github.magisk317.smscode.core.hookapi.ZygoteParam
import io.github.magisk317.smscode.core.runtime.CoreRuntime
import io.github.magisk317.smscode.core.runtime.CoreRuntimeAccess
import io.github.magisk317.smscode.core.utils.XLog
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam

class LibXposedEntry : XposedModule() {
    private val hookList: List<BaseHook> = listOf(
        SmsHandlerHook(),
        GoogleMessagesHook(),
        ModuleUtilsHook(),
        PermissionGranterHook(),
        SystemInputInjectorHook(),
        SmsProviderHook(),
    )

    private var processName: String = "unknown"

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        installCoreRuntime()
        HookEnv.init(LibXposedHookApi(this))
        processName = if (param.isSystemServer) "android" else param.processName

        for (hook in hookList) {
            if (hook.hookInitZygote()) {
                hook.initZygote(ZygoteParam())
            }
        }

        try {
            XLog.setLogLevel(BuildConfig.LOG_LEVEL)
        } catch (t: Throwable) {
            XLog.e("", t)
        }
    }

    override fun onSystemServerStarting(param: SystemServerStartingParam) {
        val loadParam = LoadParam("android", processName, param.classLoader)
        dispatchLoad(loadParam)
    }

    override fun onPackageReady(param: PackageReadyParam) {
        val loadParam = LoadParam(param.packageName, processName, param.classLoader)
        dispatchLoad(loadParam)
    }

    private fun dispatchLoad(loadParam: LoadParam) {
        installCoreRuntime()
        XLog.d("LibXposedEntry: Loaded package: ${loadParam.packageName} process: ${loadParam.processName}")
        if ("android" == loadParam.packageName || "system" == loadParam.packageName) {
            XLog.w(
                "LibXposedEntry: Android/system package loaded: pkg=%s process=%s",
                loadParam.packageName,
                loadParam.processName,
            )
        }
        for (hook in hookList) {
            if (hook.hookOnLoadPackage()) {
                hook.onLoadPackage(loadParam)
            }
        }
    }

    private fun installCoreRuntime() {
        CoreRuntime.install(object : CoreRuntimeAccess {
            override val logTag: String = BuildConfig.LOG_TAG
            override val logLevel: Int = BuildConfig.LOG_LEVEL
            override val logToXposed: Boolean = BuildConfig.LOG_TO_XPOSED
            override val debug: Boolean = BuildConfig.DEBUG
            override val applicationId: String = BuildConfig.APPLICATION_ID
            // Keep legacy action namespace for system input broadcast compatibility.
            override val actionNamespace: String = "com.github.magisk317.smscode"
        })
    }
}

package com.github.magisk317.smscode.xp

import com.github.tianma8023.xposed.smscode.BuildConfig
import com.github.magisk317.smscode.common.utils.XLog
import com.github.magisk317.smscode.xp.hook.BaseHook
import com.github.magisk317.smscode.xp.hook.code.SmsHandlerHook
import com.github.magisk317.smscode.xp.hook.google.GoogleMessagesHook
import com.github.magisk317.smscode.xp.hook.me.ModuleUtilsHook
import com.github.magisk317.smscode.xp.hook.permission.PermissionGranterHook
import com.github.magisk317.smscode.xp.hook.system.SystemInputInjectorHook
import com.github.magisk317.smscode.xp.hook.telephony.SmsProviderHook
import com.github.magisk317.smscode.xp.hookapi.HookEnv
import com.github.magisk317.smscode.xp.hookapi.LibXposedHookApi
import com.github.magisk317.smscode.xp.hookapi.LoadParam
import com.github.magisk317.smscode.xp.hookapi.ZygoteParam
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
}

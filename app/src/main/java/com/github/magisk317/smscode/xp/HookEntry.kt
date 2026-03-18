package com.github.magisk317.smscode.xp

import com.github.tianma8023.xposed.smscode.BuildConfig
import com.github.magisk317.smscode.xp.hook.google.GoogleMessagesHook
import io.github.magisk317.smscode.core.utils.XLog
import io.github.magisk317.smscode.core.hook.BaseHook
import com.github.magisk317.smscode.xp.hook.code.SmsHandlerHook
import com.github.magisk317.smscode.xp.hook.me.ModuleUtilsHook
import io.github.magisk317.smscode.core.hook.permission.PermissionGranterHook
import io.github.magisk317.smscode.core.hook.system.SystemInputInjectorHook
import com.github.magisk317.smscode.xp.hook.telephony.SmsProviderHook
import io.github.magisk317.smscode.core.hookapi.HookEnv
import io.github.magisk317.smscode.core.hookapi.LegacyHookApi
import io.github.magisk317.smscode.core.hookapi.LoadParam
import io.github.magisk317.smscode.core.hookapi.ZygoteParam
import io.github.magisk317.smscode.core.runtime.CoreRuntime
import io.github.magisk317.smscode.core.runtime.CoreRuntimeAccess
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.callbacks.XC_LoadPackage

class HookEntry :
    IXposedHookLoadPackage,
    IXposedHookZygoteInit {

    private val mHookList: List<BaseHook> = listOf(
        SmsHandlerHook(), // InBoundsSmsHandler Hook
        GoogleMessagesHook(), // Google Messages read sync hook
        ModuleUtilsHook(), // ModuleUtils Hook
        PermissionGranterHook(), // PackageManagerService Hook
        SystemInputInjectorHook(), // System Server Input Injection Hook
        SmsProviderHook(), // Telephony provider write logging
    )

    @Throws(Throwable::class)
    override fun initZygote(startupParam: IXposedHookZygoteInit.StartupParam) {
        installCoreRuntime()
        HookEnv.init(LegacyHookApi())
        for (hook in mHookList) {
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

    @Throws(Throwable::class)
    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        installCoreRuntime()
        HookEnv.init(LegacyHookApi())
        val loadParam = LoadParam(lpparam.packageName, lpparam.processName, lpparam.classLoader)
        XLog.d("HookEntry: Loaded package: ${loadParam.packageName} process: ${loadParam.processName}")
        if ("android" == loadParam.packageName || "system" == loadParam.packageName) {
            XLog.w(
                "HookEntry: Android/system package loaded: pkg=%s process=%s",
                loadParam.packageName,
                loadParam.processName,
            )
        }
        for (hook in mHookList) {
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
            override val actionNamespace: String = "com.github.magisk317.smscode"
        })
    }
}

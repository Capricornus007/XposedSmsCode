package com.github.magisk317.smscode.xp

import com.github.tianma8023.xposed.smscode.BuildConfig
import com.github.magisk317.smscode.xp.hook.google.GoogleMessagesHook
import com.github.magisk317.smscode.common.utils.XLog
import com.github.magisk317.smscode.xp.hook.BaseHook
import com.github.magisk317.smscode.xp.hook.code.SmsHandlerHook
import com.github.magisk317.smscode.xp.hook.me.ModuleUtilsHook
import com.github.magisk317.smscode.xp.hook.permission.PermissionGranterHook
import com.github.magisk317.smscode.xp.hook.system.SystemInputInjectorHook
import com.github.magisk317.smscode.xp.hook.telephony.SmsProviderHook
import com.github.magisk317.smscode.xp.hookapi.HookEnv
import com.github.magisk317.smscode.xp.hookapi.LegacyHookApi
import com.github.magisk317.smscode.xp.hookapi.LoadParam
import com.github.magisk317.smscode.xp.hookapi.ZygoteParam
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
}

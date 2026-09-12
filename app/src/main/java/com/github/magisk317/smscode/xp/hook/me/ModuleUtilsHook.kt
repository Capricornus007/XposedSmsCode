package com.github.magisk317.smscode.xp.hook.me

import com.github.tianma8023.xposed.smscode.BuildConfig
import com.github.magisk317.smscode.common.utils.ModuleUtils
import com.github.magisk317.smscode.common.utils.XLog
import com.github.magisk317.smscode.xp.helper.XposedWrapper
import com.github.magisk317.smscode.xp.hook.BaseHook
import com.github.magisk317.smscode.xp.hookapi.LoadParam
import com.github.magisk317.smscode.xp.hookapi.MethodHook
import com.github.magisk317.smscode.xp.hookapi.MethodHookParam

/**
 * Hook class ModuleUtils
 */
class ModuleUtilsHook : BaseHook() {
    @Throws(Throwable::class)
    override fun onLoadPackage(lpparam: LoadParam) {
        if (SMSCODE_PACKAGE == lpparam.packageName) {
            try {
                XLog.i("Hooking current Xposed module status...")
                hookModuleUtils(lpparam)
            } catch (e: Throwable) {
                XLog.e("Failed to hook current Xposed module status.")
            }
        }
    }

    @Throws(Throwable::class)
    private fun hookModuleUtils(lpparam: LoadParam) {
        val className = ModuleUtils::class.java.name
        XposedWrapper.findAndHookMethod(
            className,
            lpparam.classLoader,
            "getModuleVersion",
            object : MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    param.result = MODULE_VERSION
                }
            },
        )
    }

    companion object {
        private const val SMSCODE_PACKAGE = BuildConfig.APPLICATION_ID
        private const val MODULE_VERSION = BuildConfig.MODULE_VERSION
    }
}

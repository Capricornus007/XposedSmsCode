package io.github.capricornus007.smscode.xp.hook.me

import io.github.capricornus007.smscode.BuildConfig
import io.github.capricornus007.smscode.common.utils.ModuleUtils
import io.github.capricornus007.smscode.common.utils.XLog
import io.github.capricornus007.smscode.xp.helper.XposedWrapper
import io.github.capricornus007.smscode.xp.hook.BaseHook
import io.github.capricornus007.smscode.xp.hookapi.LoadParam
import io.github.capricornus007.smscode.xp.hookapi.MethodHook
import io.github.capricornus007.smscode.xp.hookapi.MethodHookParam

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

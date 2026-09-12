package com.github.magisk317.smscode.common.utils

/**
 * 当前Xposed模块相关工具类
 */
object ModuleUtils {
    @Volatile
    private var runtimeActivated = false

    /**
     * 标记当前运行时是否已连接到 Xposed Service。
     */
    @JvmStatic
    fun setRuntimeActivated(activated: Boolean) {
        runtimeActivated = activated
    }

    @JvmStatic
    fun isRuntimeActivated(): Boolean = runtimeActivated

    /**
     * 返回模块版本 <br/>
     * 注意：该方法被本模块Hook住，返回的值是 BuildConfig.MODULE_VERSION，如果没被Hook则返回-1
     */
    @JvmStatic
    fun getModuleVersion(): Int {
        XLog.d("getModuleVersion()")
        return -1
    }

    /**
     * 当前模块是否在XposedInstaller中被启用
     */
    @JvmStatic
    fun isModuleEnabled(): Boolean = getModuleVersion() > 0

    /**
     * 模块是否已激活（兼容新 API 静态作用域场景）
     */
    @JvmStatic
    fun isModuleActivated(context: android.content.Context): Boolean {
        return runtimeActivated || ModuleActivationStore.isActivatedRecently(context)
    }
}

package com.github.magisk317.smscode.xp.hookapi

import com.github.tianma8023.xposed.smscode.BuildConfig
import android.util.Log
import java.lang.reflect.Member

object HookBridge {
    fun hookMethod(member: Member?, callback: MethodHook): HookHandle? {
        if (member == null) return null
        return HookEnv.api.hookMethod(member, callback)
    }

    fun hookAllConstructors(clazz: Class<*>, callback: MethodHook): Set<HookHandle> =
        HookEnv.api.hookAllConstructors(clazz, callback)

    fun hookAllMethods(clazz: Class<*>, methodName: String, callback: MethodHook): Set<HookHandle> =
        HookEnv.api.hookAllMethods(clazz, methodName, callback)

    fun log(message: String, tr: Throwable? = null) {
        if (!BuildConfig.LOG_TO_XPOSED) return
        HookEnv.api.log(Log.INFO, BuildConfig.LOG_TAG, message, tr)
    }
}

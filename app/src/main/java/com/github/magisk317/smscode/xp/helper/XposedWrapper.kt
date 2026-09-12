package com.github.magisk317.smscode.xp.helper

import com.github.magisk317.smscode.common.utils.XLog
import com.github.magisk317.smscode.xp.hookapi.HookBridge
import com.github.magisk317.smscode.xp.hookapi.HookHandle
import com.github.magisk317.smscode.xp.hookapi.HookHelpers
import com.github.magisk317.smscode.xp.hookapi.MethodHook
import java.lang.reflect.Member
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/**
 * Xposed Wrapper Utils
 */
object XposedWrapper {
    fun findClass(className: String, classLoader: ClassLoader?): Class<*>? = try {
        HookHelpers.findClass(className, classLoader)
    } catch (ignored: Throwable) {
        XLog.e("Class not found: %s", className)
        null
    }

    fun findAndHookMethod(
        className: String,
        classLoader: ClassLoader?,
        methodName: String,
        vararg parameterTypesAndCallback: Any,
    ): HookHandle? = try {
        val clazz = HookHelpers.findClass(className, classLoader)
        findAndHookMethod(clazz, methodName, *parameterTypesAndCallback)
    } catch (t: Throwable) {
        XLog.e("Error in hook %s#%s", className, methodName, t)
        null
    }

    fun findAndHookMethod(clazz: Class<*>, methodName: String, vararg parameterTypesAndCallback: Any): HookHandle? = try {
        if (parameterTypesAndCallback.isEmpty()) return null
        val callback = parameterTypesAndCallback.lastOrNull() as? MethodHook ?: return null
        val paramTypes = parameterTypesAndCallback.dropLast(1).toTypedArray()
        val method = HookHelpers.findMethodExactIfExists(clazz, methodName, *paramTypes) ?: return null
        hookMethod(method, callback)
    } catch (t: Throwable) {
        XLog.e("Error in hook %s#%s", clazz.name, methodName, t)
        null
    }

    fun hookMethod(hookMethod: Member, callback: MethodHook): HookHandle? = try {
        if (hookMethod is Method && (Modifier.isAbstract(hookMethod.modifiers) || Modifier.isNative(hookMethod.modifiers))) {
            XLog.d("Skip hook unsupported method: %s#%s", hookMethod.declaringClass.name, hookMethod.name)
            return null
        }
        HookBridge.hookMethod(hookMethod, callback)
    } catch (t: Throwable) {
        XLog.e("Error in hookMethod: %s", hookMethod.name, t)
        null
    }

    fun hookAllConstructors(hookClass: Class<*>, callback: MethodHook): Set<HookHandle>? = try {
        HookBridge.hookAllConstructors(hookClass, callback)
    } catch (t: Throwable) {
        XLog.e("Error in hookAllConstructors: %s", hookClass.name, t)
        null
    }

    fun hookAllMethods(hookClass: Class<*>, methodName: String, callback: MethodHook): Set<HookHandle>? = try {
        HookBridge.hookAllMethods(hookClass, methodName, callback)
    } catch (t: Throwable) {
        XLog.e("Error in hookAllMethods: %s", hookClass.name, t)
        null
    }
}

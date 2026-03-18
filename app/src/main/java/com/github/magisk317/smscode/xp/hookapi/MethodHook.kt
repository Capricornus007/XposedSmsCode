package com.github.magisk317.smscode.xp.hookapi

import java.lang.reflect.Member
import com.github.magisk317.smscode.xp.hookapi.MethodHookParam

open class MethodHook {
    @Throws(Throwable::class)
    open fun beforeHookedMethod(param: MethodHookParam) {
    }

    @Throws(Throwable::class)
    open fun afterHookedMethod(param: MethodHookParam) {
    }
}

abstract class MethodHookParam {
    abstract val method: Member
    abstract var thisObject: Any?
    abstract var args: Array<Any?>

    var returnEarly: Boolean = false

    open var result: Any? = null
        set(value) {
            field = value
            returnEarly = true
            throwable = null
        }

    open var throwable: Throwable? = null
        set(value) {
            field = value
            returnEarly = true
        }

    fun hasThrowable(): Boolean = throwable != null
}

class SimpleMethodHookParam(
    override val method: Member,
    override var thisObject: Any?,
    override var args: Array<Any?>,
) : MethodHookParam()

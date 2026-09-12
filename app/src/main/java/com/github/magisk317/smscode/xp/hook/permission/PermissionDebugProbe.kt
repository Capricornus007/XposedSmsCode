package com.github.magisk317.smscode.xp.hook.permission

import com.github.magisk317.smscode.common.utils.XLog

/**
 * Runtime probe helpers for permission hooks.
 */
object PermissionDebugProbe {
    private const val MAX_ITEMS = 80
    private val loggedTags = mutableSetOf<String>()

    fun logFailure(tag: String, throwable: Throwable, obj: Any? = null, args: Array<Any?>? = null) {
        XLog.w("$tag failed: ${throwable.message ?: throwable.javaClass.simpleName}", throwable)
        if (obj != null) {
            dumpObject("$tag target", obj)
        }
        if (args != null) {
            dumpArgs("$tag args", args)
        }
    }

    fun dumpObject(tag: String, obj: Any?) {
        if (obj == null) {
            XLog.w("$tag: <null>")
            return
        }
        dumpClass(tag, obj.javaClass)
    }

    fun dumpClass(tag: String, clazz: Class<*>?) {
        if (clazz == null) {
            XLog.w("$tag: <null class>")
            return
        }
        val chain = generateSequence(clazz) { it.superclass }
            .joinToString(" -> ") { it.name }
        val fields = clazz.declaredFields
            .map { "${it.name}:${it.type.name}" }
            .sorted()
            .joinToString(limit = MAX_ITEMS, truncated = "...")
        val methods = clazz.declaredMethods
            .map { it.name }
            .distinct()
            .sorted()
            .joinToString(limit = MAX_ITEMS, truncated = "...")
        XLog.w("$tag: class=$chain fields=[$fields] methods=[$methods]")
    }

    fun dumpArgs(tag: String, args: Array<Any?>) {
        val summary = args.mapIndexed { index, arg ->
            val name = arg?.javaClass?.name ?: "null"
            "#$index:$name"
        }.joinToString(limit = MAX_ITEMS, truncated = "...")
        XLog.w("$tag: args=[$summary]")
    }

    fun dumpMethodSignaturesOnce(tag: String, clazz: Class<*>, methodName: String) {
        val key = "$tag|${clazz.name}|$methodName"
        synchronized(loggedTags) {
            if (!loggedTags.add(key)) return
        }
        dumpMethodSignatures(tag, clazz, methodName)
    }

    fun dumpMethodSignatures(tag: String, clazz: Class<*>, methodName: String) {
        val methods = clazz.declaredMethods.filter { it.name == methodName }
        if (methods.isEmpty()) {
            XLog.w("$tag: no method $methodName in ${clazz.name}")
            return
        }
        val signatures = methods.joinToString(limit = MAX_ITEMS, truncated = "...") { method ->
            val params = method.parameterTypes.joinToString(",") { it.name }
            "${method.name}($params):${method.returnType.name}"
        }
        XLog.w("$tag: ${clazz.name} methods=[$signatures]")
    }
}

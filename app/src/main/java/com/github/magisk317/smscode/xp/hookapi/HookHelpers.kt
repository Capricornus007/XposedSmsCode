package com.github.magisk317.smscode.xp.hookapi

import java.lang.reflect.Field
import java.lang.reflect.Method

object HookHelpers {
    private val primitiveToWrapper = mapOf(
        Boolean::class.javaPrimitiveType to Boolean::class.java,
        Byte::class.javaPrimitiveType to Byte::class.java,
        Char::class.javaPrimitiveType to Char::class.java,
        Short::class.javaPrimitiveType to Short::class.java,
        Int::class.javaPrimitiveType to Int::class.java,
        Long::class.javaPrimitiveType to Long::class.java,
        Float::class.javaPrimitiveType to Float::class.java,
        Double::class.javaPrimitiveType to Double::class.java,
        Void.TYPE to Void::class.java,
    )

    fun findClass(className: String, classLoader: ClassLoader?): Class<*> {
        return if (classLoader != null) {
            Class.forName(className, false, classLoader)
        } else {
            Class.forName(className)
        }
    }

    fun findClassIfExists(className: String, classLoader: ClassLoader?): Class<*>? {
        return runCatching { findClass(className, classLoader) }.getOrNull()
    }

    fun findMethodExact(clazz: Class<*>?, methodName: String, vararg parameterTypes: Any?): Method {
        val target = clazz ?: throw NoSuchMethodException("Class is null for method $methodName")
        val resolved = resolveParamTypes(target.classLoader, parameterTypes) ?: throw NoSuchMethodException(
            "Param types unresolved for ${target.name}#$methodName",
        )
        return findMethodExactInternal(target, methodName, resolved)
            ?: throw NoSuchMethodException("Method not found: ${target.name}#$methodName")
    }

    fun findMethodExactIfExists(clazz: Class<*>?, methodName: String, vararg parameterTypes: Any?): Method? {
        val target = clazz ?: return null
        val resolved = resolveParamTypes(target.classLoader, parameterTypes) ?: return null
        return findMethodExactInternal(target, methodName, resolved)
    }

    fun findMethodsByExactParameters(
        clazz: Class<*>?,
        returnType: Class<*>?,
        vararg parameterTypes: Class<*>?,
    ): Array<Method> {
        val target = clazz ?: return emptyArray()
        if (parameterTypes.any { it == null }) return emptyArray()
        val resolved = parameterTypes.filterNotNull().toTypedArray()
        val methods = mutableListOf<Method>()
        var current: Class<*>? = target
        while (current != null) {
            current.declaredMethods
                .filter { method ->
                    if (method.parameterTypes.size != resolved.size) return@filter false
                    if (returnType != null && method.returnType != returnType) return@filter false
                    method.parameterTypes.contentEquals(resolved)
                }
                .forEach { method ->
                    method.isAccessible = true
                    methods += method
                }
            current = current.superclass
        }
        return methods.toTypedArray()
    }

    fun findMethodBestMatch(clazz: Class<*>?, methodName: String, vararg args: Any?): Method {
        val target = clazz ?: throw NoSuchMethodException("Class is null for method $methodName")
        val candidates = collectMethods(target, methodName)
        val argTypes = args.map { it?.javaClass }.toTypedArray()
        var best: Method? = null
        var bestScore = -1
        for (method in candidates) {
            val params = method.parameterTypes
            if (params.size != argTypes.size) continue
            var score = 0
            var compatible = true
            for (i in params.indices) {
                val argType = argTypes[i]
                val paramType = params[i]
                if (argType == null) {
                    if (paramType.isPrimitive) {
                        compatible = false
                        break
                    }
                    continue
                }
                val wrappedParam = wrapPrimitive(paramType)
                if (!wrappedParam.isAssignableFrom(argType)) {
                    compatible = false
                    break
                }
                if (wrappedParam == argType) score++
            }
            if (!compatible) continue
            if (score > bestScore) {
                bestScore = score
                best = method
            }
        }
        return best?.also { it.isAccessible = true }
            ?: throw NoSuchMethodException("No suitable method for ${target.name}#$methodName")
    }

    fun callMethod(obj: Any?, methodName: String, vararg args: Any?): Any? {
        val target = obj ?: throw NullPointerException("callMethod target is null for $methodName")
        val method = findMethodBestMatch(target.javaClass, methodName, *args)
        return method.invoke(target, *args)
    }

    fun callStaticMethod(clazz: Class<*>?, methodName: String, vararg args: Any?): Any? {
        val target = clazz ?: throw NullPointerException("callStaticMethod class is null for $methodName")
        val method = findMethodBestMatch(target, methodName, *args)
        return method.invoke(null, *args)
    }

    fun getObjectField(obj: Any?, fieldName: String): Any? {
        val target = obj ?: throw NullPointerException("getObjectField target is null for $fieldName")
        return findField(target.javaClass, fieldName).get(target)
    }

    fun getIntField(obj: Any?, fieldName: String): Int {
        val target = obj ?: throw NullPointerException("getIntField target is null for $fieldName")
        return findField(target.javaClass, fieldName).getInt(target)
    }

    fun getStaticIntField(clazz: Class<*>?, fieldName: String): Int {
        val target = clazz ?: throw NullPointerException("getStaticIntField class is null for $fieldName")
        return findField(target, fieldName).getInt(null)
    }

    private fun resolveParamTypes(classLoader: ClassLoader?, parameterTypes: Array<out Any?>): Array<Class<*>>? {
        val resolved = ArrayList<Class<*>>()
        for (param in parameterTypes) {
            val clazz = when (param) {
                null -> return null
                is Class<*> -> param
                is String -> findClass(param, classLoader)
                else -> return null
            }
            resolved += clazz
        }
        return resolved.toTypedArray()
    }

    private fun findMethodExactInternal(clazz: Class<*>, methodName: String, parameterTypes: Array<Class<*>>): Method? {
        var current: Class<*>? = clazz
        while (current != null) {
            runCatching {
                val method = current.getDeclaredMethod(methodName, *parameterTypes)
                method.isAccessible = true
                return method
            }
            current = current.superclass
        }
        return null
    }

    private fun collectMethods(clazz: Class<*>, methodName: String): List<Method> {
        val methods = mutableListOf<Method>()
        var current: Class<*>? = clazz
        while (current != null) {
            current.declaredMethods
                .filter { it.name == methodName }
                .forEach { method ->
                    method.isAccessible = true
                    methods += method
                }
            current = current.superclass
        }
        return methods
    }

    private fun findField(clazz: Class<*>, fieldName: String): Field {
        var current: Class<*>? = clazz
        while (current != null) {
            try {
                val field = current.getDeclaredField(fieldName)
                field.isAccessible = true
                return field
            } catch (_: NoSuchFieldException) {
                current = current.superclass
            }
        }
        throw NoSuchFieldException("Field not found: ${clazz.name}#$fieldName")
    }

    private fun wrapPrimitive(clazz: Class<*>): Class<*> {
        return if (clazz.isPrimitive) {
            primitiveToWrapper[clazz] ?: clazz
        } else {
            clazz
        }
    }
}

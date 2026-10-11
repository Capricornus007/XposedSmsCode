package io.github.capricornus007.smscode.xp.helper

import android.content.Context
import io.github.capricornus007.smscode.xp.hookapi.HookHelpers

/**
 * Shim for the private module API
 * `io.github.magisk317.smscode.xposed.helper.SmsHookContextRecovery`, reconstructed by reverse
 * engineering the released APKs (arm64-v8a v3.3.6 and v3.3.8).
 *
 * Surface taken from v3.3.8 `classes.dex`:
 *   `SmsHookContextRecovery.INSTANCE.resolve(Object, String) -> RecoveredContext`
 *   `SmsHookContextRecovery.INSTANCE.resolveFirst(Any...) -> RecoveredContext`
 *   `RecoveredContext(Context context, String source)`
 *   `CONTEXT_FIELD_NAMES = ["mContext", "context", "mPhoneContext"]`
 *   `PHONE_FIELD_NAMES   = ["mPhone", "phone"]`
 *
 * Resolution order (v3.3.8):
 *   1. the candidate itself is a [Context];
 *   2. superclass walk for a declared field named `mContext`/`context` whose *type* is
 *      Context-assignable — this step is NEW in v3.3.8 (ROM subclasses keep the real phone
 *      context on a base class);
 *   3. `mContext` / `context` / `mPhoneContext` fields on the candidate;
 *   4. zero-arg `getContext()`;
 *   5. one hop through `mPhone` / `phone` then `getContext()`, else its `mContext` field.
 *
 * v3.3.6 did the same job with an app-side `SmsHandlerHook.DispatchContextRecovery` whose order was
 * 1 -> 3 -> 4 -> 5 (no superclass walk). Steps 3-5 are byte-for-byte the v3.3.6 chain, so this
 * single implementation stays compatible with both releases (superset, v3.3.6 behaviour intact).
 */
object SmsHookContextRecovery {

    private val CONTEXT_FIELD_NAMES = listOf("mContext", "context", "mPhoneContext")
    private val HIERARCHY_FIELD_NAMES = listOf("mContext", "context")
    private val PHONE_FIELD_NAMES = listOf("mPhone", "phone")
    private const val GET_CONTEXT_METHOD = "getContext"

    /** Recovered phone context plus the reflection path it was found on (used for diagnostics). */
    data class RecoveredContext(val context: Context, val source: String)

    fun resolve(obj: Any?, source: String): RecoveredContext? =
        resolveValue(obj, source, Context::class.java)
            ?.let { (value, path) -> RecoveredContext(value as Context, path) }

    /** First candidate that yields a context; mirrors v3.3.8 `resolveFirst(vararg Pair)`. */
    fun resolveFirst(vararg candidates: Pair<Any?, String>): RecoveredContext? {
        for ((value, source) in candidates) {
            resolve(value, source)?.let { return it }
        }
        return null
    }

    /**
     * Type-agnostic core so the whole chain can be unit tested on the JVM without an Android
     * [Context]. Returns the recovered object and the reflection path label.
     */
    fun resolveValue(obj: Any?, source: String, target: Class<*>): Pair<Any, String>? {
        if (obj == null) return null
        if (target.isInstance(obj)) return obj to source
        hierarchyField(obj, source, target)?.let { return it }
        for (name in CONTEXT_FIELD_NAMES) {
            readField(obj, name, target)?.let { return it to "$source.$name" }
        }
        readMethod(obj, GET_CONTEXT_METHOD, target)?.let { return it to "$source.$GET_CONTEXT_METHOD" }
        return recoverThroughPhone(obj, source, target)
    }

    /** v3.3.8-only step: nearest declared Context-assignable field named mContext/context. */
    private fun hierarchyField(obj: Any, source: String, target: Class<*>): Pair<Any, String>? {
        var clazz: Class<*>? = obj.javaClass
        while (clazz != null) {
            val field = clazz.declaredFields.firstOrNull { candidate ->
                target.isAssignableFrom(candidate.type) && candidate.name in HIERARCHY_FIELD_NAMES
            }
            if (field != null) {
                val value = runCatching {
                    field.isAccessible = true
                    field.get(obj)
                }.getOrNull()
                if (value != null && target.isInstance(value)) {
                    return value to "$source.${field.name}"
                }
            }
            clazz = clazz.superclass
        }
        return null
    }

    private fun recoverThroughPhone(obj: Any, source: String, target: Class<*>): Pair<Any, String>? {
        val phone = readRawField(obj, PHONE_FIELD_NAMES[0]) ?: readRawField(obj, PHONE_FIELD_NAMES[1])
            ?: return null
        readMethod(phone, GET_CONTEXT_METHOD, target)?.let {
            return it to "$source.phone.$GET_CONTEXT_METHOD"
        }
        return readField(phone, "mContext", target)?.let { it to "$source.phone.mContext" }
    }

    private fun readField(obj: Any, name: String, target: Class<*>): Any? =
        readRawField(obj, name)?.takeIf { target.isInstance(it) }

    private fun readRawField(obj: Any, name: String): Any? =
        runCatching { HookHelpers.getObjectField(obj, name) }.getOrNull()

    private fun readMethod(obj: Any, name: String, target: Class<*>): Any? =
        runCatching { HookHelpers.callMethod(obj, name) }.getOrNull()?.takeIf { target.isInstance(it) }
}

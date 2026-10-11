package io.github.capricornus007.smscode.xp.helper

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Exercises the reflection chain of [SmsHookContextRecovery.resolveValue] with plain JVM fixtures
 * (the Android [android.content.Context] binding is just one target class of the same walker).
 */
class SmsHookContextRecoveryTest {

    private class Leaf(val tag: String)

    private open class BaseHolder {
        @JvmField
        var mContext: Leaf? = Leaf("from-base")
    }

    private class SubHolder : BaseHolder()

    private class DirectContext {
        @JvmField
        var context: Leaf? = Leaf("direct")
    }

    private class PhoneContextField {
        @JvmField
        var mContext: Leaf? = Leaf("phone-field")
    }

    private class MethodContext {
        fun getContext(): Leaf = Leaf("method")
    }

    private class PhoneMethod {
        @JvmField
        var mPhone: Any = MethodContext()
    }

    private class PhoneField {
        @JvmField
        var phone: Any = PhoneContextField()
    }

    private class MismatchedField {
        @JvmField
        var mContext: String = "not a leaf"
    }

    private class Empty

    @Test
    fun resolveValue_returnsCandidateWhenAlreadyTarget() {
        val leaf = Leaf("self")
        val recovered = SmsHookContextRecovery.resolveValue(leaf, "handler", Leaf::class.java)
        assertNotNull(recovered)
        assertEquals(leaf, recovered!!.first)
        assertEquals("handler", recovered.second)
    }

    @Test
    fun resolveValue_walksSuperclassHierarchy() {
        // v3.3.8-only step: the field lives on a base class of the hooked instance.
        val recovered = SmsHookContextRecovery.resolveValue(SubHolder(), "handler", Leaf::class.java)
        assertNotNull(recovered)
        assertEquals("handler.mContext", recovered!!.second)
        assertEquals("from-base", (recovered.first as Leaf).tag)
    }

    @Test
    fun resolveValue_readsContextFieldNamesFromV336Chain() {
        val recovered = SmsHookContextRecovery.resolveValue(DirectContext(), "receiver", Leaf::class.java)
        assertNotNull(recovered)
        assertEquals("receiver.context", recovered!!.second)
        assertEquals("direct", (recovered.first as Leaf).tag)
    }

    @Test
    fun resolveValue_fallsBackToGetContextMethod() {
        val recovered = SmsHookContextRecovery.resolveValue(MethodContext(), "handler", Leaf::class.java)
        assertNotNull(recovered)
        assertEquals("handler.getContext", recovered!!.second)
        assertEquals("method", (recovered.first as Leaf).tag)
    }

    @Test
    fun resolveValue_hopsThroughMPhoneFieldToGetContext() {
        val recovered = SmsHookContextRecovery.resolveValue(PhoneMethod(), "handler", Leaf::class.java)
        assertNotNull(recovered)
        assertEquals("handler.phone.getContext", recovered!!.second)
        assertEquals("method", (recovered.first as Leaf).tag)
    }

    @Test
    fun resolveValue_hopsThroughPhoneFieldToMContext() {
        val recovered = SmsHookContextRecovery.resolveValue(PhoneField(), "arg[2]", Leaf::class.java)
        assertNotNull(recovered)
        assertEquals("arg[2].phone.mContext", recovered!!.second)
        assertEquals("phone-field", (recovered.first as Leaf).tag)
    }

    @Test
    fun resolveValue_ignoresFieldsOfTheWrongType() {
        assertNull(SmsHookContextRecovery.resolveValue(MismatchedField(), "handler", Leaf::class.java))
    }

    @Test
    fun resolveValue_isNullSafe() {
        assertNull(SmsHookContextRecovery.resolveValue(null, "receiver", Leaf::class.java))
        assertNull(SmsHookContextRecovery.resolveValue(Empty(), "receiver", Leaf::class.java))
    }

    @Test
    fun resolveFirst_isBoundToAndroidContextAndIgnoresOtherTypes() {
        // resolve() / resolveFirst() are the Context-bound wrappers used by the hook. The fixtures
        // above are not Contexts, so the chain must come back empty instead of throwing.
        assertNull(
            SmsHookContextRecovery.resolveFirst(
                SubHolder() to "handler",
                DirectContext() to "receiver",
            ),
        )
        assertNull(SmsHookContextRecovery.resolve(null, "handler"))
        assertNull(SmsHookContextRecovery.resolve(Empty(), "arg[0]"))
    }
}

package io.github.capricornus007.smscode.xp.hook.code.action

import android.content.Context
import android.os.Bundle
import io.github.capricornus007.smscode.common.utils.XLog
import io.github.capricornus007.smscode.data.db.entity.SmsMsg
import java.util.concurrent.Callable

/**
 * Action + Callable
 */
abstract class CallableAction(
    @JvmField protected val mPluginContext: Context,
    @JvmField protected val mPhoneContext: Context,
    @JvmField protected var mSmsMsg: SmsMsg,
) : Action<Bundle?>,
    Callable<Bundle?> {

    override fun call(): Bundle? = try {
        action()
    } catch (t: Throwable) {
        XLog.e("Error in CallableAction#call()", t)
        null
    }
}

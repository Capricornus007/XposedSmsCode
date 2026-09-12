package io.github.capricornus007.smscode.xp.hook.code.action

import android.content.Context
import io.github.capricornus007.smscode.data.db.entity.SmsMsg

/**
 * Runnable + Action + Callable
 */
abstract class RunnableAction(pluginContext: Context, phoneContext: Context, smsMsg: SmsMsg) :
    CallableAction(pluginContext, phoneContext, smsMsg),
    Runnable {

    override fun run() {
        call()
    }
}

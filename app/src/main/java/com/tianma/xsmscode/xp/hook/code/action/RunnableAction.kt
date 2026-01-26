package com.tianma.xsmscode.xp.hook.code.action

import android.content.Context
import com.tianma.xsmscode.data.db.entity.SmsMsg

/**
 * Runnable + Action + Callable
 */
abstract class RunnableAction(
    pluginContext: Context,
    phoneContext: Context,
    smsMsg: SmsMsg
) : CallableAction(pluginContext, phoneContext, smsMsg), Runnable {

    override fun run() {
        call()
    }
}

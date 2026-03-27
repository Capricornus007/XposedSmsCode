package com.github.magisk317.smscode.xp.hook.code.helper

import io.github.magisk317.smscode.verification.AutoInputBroadcastHelper
import io.github.magisk317.smscode.xposed.utils.XLog

object InputHelper {

    @JvmStatic
    fun sendText(
        context: android.content.Context,
        text: String?,
        autoEnter: Boolean = false,
        inputIntervalMs: Long = 0L,
        attemptId: Long? = null,
    ) {
        AutoInputBroadcastHelper.sendText(
            context = context,
            text = text,
            autoEnter = autoEnter,
            inputIntervalMs = inputIntervalMs,
            attemptId = attemptId,
            actionResolver = {
                io.github.magisk317.smscode.xposed.hook.system.SystemInputInjectorHook.resolveActionAutoInput()
            },
        )
    }

    @JvmStatic
    fun sendToast(
        context: android.content.Context,
        text: String?,
        duration: Int = android.widget.Toast.LENGTH_LONG,
    ) {
        if (text.isNullOrEmpty()) return
        val intent = android.content.Intent(
            io.github.magisk317.smscode.xposed.hook.system.SystemInputInjectorHook.resolveActionShowToast(),
        )
        intent.putExtra("text", text)
        intent.putExtra("duration", duration)
        context.sendBroadcast(intent)
        XLog.i(
            "Sent Broadcast ACTION_SHOW_TOAST with textLength: %d, duration: %d",
            text.length,
            duration,
        )
    }
}

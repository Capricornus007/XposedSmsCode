package com.github.magisk317.smscode.xp.hook.code.helper

import io.github.magisk317.smscode.xposed.utils.XLog

object InputHelper {

    @JvmStatic
    fun sendText(
        context: android.content.Context,
        text: String?,
        autoEnter: Boolean = false,
        inputIntervalMs: Long = 0L,
    ) {
        if (text == null) return
        val intent = android.content.Intent(
            io.github.magisk317.smscode.xposed.hook.system.SystemInputInjectorHook.resolveActionAutoInput(),
        )
        intent.putExtra("code", text)
        intent.putExtra("autoEnter", autoEnter)
        intent.putExtra("inputIntervalMs", inputIntervalMs)
        // Broadcast without explicit package to avoid dropping delivery when
        // system_server receiver isn't bound to package.
        context.sendBroadcast(intent)
        XLog.i(
            "Sent Broadcast ACTION_AUTO_INPUT with code: %s, autoEnter: %s, inputIntervalMs: %d",
            text,
            autoEnter,
            inputIntervalMs,
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

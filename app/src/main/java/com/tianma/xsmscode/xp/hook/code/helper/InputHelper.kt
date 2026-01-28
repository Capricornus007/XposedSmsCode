package com.tianma.xsmscode.xp.hook.code.helper

import com.tianma.xsmscode.common.utils.XLog
import java.io.DataOutputStream
import java.io.IOException

object InputHelper {

    @JvmStatic
    fun sendText(context: android.content.Context, text: String?) {
        if (text == null) return
        val intent = android.content.Intent("com.tianma.xsmscode.ACTION_AUTO_INPUT")
        intent.putExtra("code", text)
        intent.setPackage("android") // Send explicit broadcast to system package (hooked) or general?
        // Wait, SystemInputInjectorHook registers receiver dynamically on the system context.
        // It's a dynamic receiver.
        // If we set package "android", it targets the package.
        // Dynamic receivers in "android" package should receive it.
        context.sendBroadcast(intent)
        XLog.i("Sent Broadcast ACTION_AUTO_INPUT with code: $text")
    }
}

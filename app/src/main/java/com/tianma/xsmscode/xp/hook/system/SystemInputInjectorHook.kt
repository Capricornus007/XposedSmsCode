package com.tianma.xsmscode.xp.hook.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import com.tianma.xsmscode.common.utils.XLog
import com.tianma.xsmscode.xp.hook.BaseHook
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

class SystemInputInjectorHook : BaseHook() {

    override fun onLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if ("android" == lpparam.packageName && "android" == lpparam.processName) {
            XLog.i("Hooking System Server for Input Injection...")
            
            // Hook Application.onCreate to get Context
            try {
                XposedHelpers.findAndHookMethod(
                    "android.app.LoadedApk",
                    lpparam.classLoader,
                    "makeApplication",
                    Boolean::class.javaPrimitiveType,
                    android.app.Instrumentation::class.java,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            val application = param.result as? android.app.Application ?: return
                            registerReceiver(application)
                        }
                    }
                )
            } catch (t: Throwable) {
                XLog.e("Failed to hook makeApplication", t)
            }
        }
    }

    private fun registerReceiver(context: Context) {
        try {
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    val code = intent.getStringExtra("code")
                    if (!code.isNullOrEmpty()) {
                        XLog.i("SystemServer received input request: $code")
                        injectText(code)
                    }
                }
            }
            val filter = IntentFilter("com.tianma.xsmscode.ACTION_AUTO_INPUT")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                context.registerReceiver(receiver, filter)
            }
            XLog.i("SystemInputInjectorReceiver registered")
        } catch (t: Throwable) {
            XLog.e("Failed to register receiver", t)
        }
    }

    private fun injectText(text: String) {
        Thread {
            try {
                // Simplified injection logic using instrumentation or direct InputManager
                // Since we are in system process, we can use Instrumentation? 
                // Creating new Instrumentation might be risky.
                // Let's use InputManager.getInstance().injectInputEvent
                
                val inputManager = XposedHelpers.callStaticMethod(
                    android.hardware.input.InputManager::class.java, 
                    "getInstance"
                )
                
                // We need to inject key events for each char
                // This logic is complex to reimplement here.
                // Alternatively, can we just use Runtime.exec("input text") from HERE?
                // System Server runs as 'system' user. 'system' user MIGHT have permission to run 'input'
                // unlike the 'radio' user (Phone process).
                // Let's try Runtime.exec first as it is simplest.
                
                Runtime.getRuntime().exec("input text $text")
                XLog.i("Executed 'input text' from System Server")
                
            } catch (t: Throwable) {
                XLog.e("Failed to inject text from System Server", t)
            }
        }.start()
    }
}

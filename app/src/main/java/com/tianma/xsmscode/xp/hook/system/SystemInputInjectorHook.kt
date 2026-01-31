package com.tianma.xsmscode.xp.hook.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Process
import android.view.KeyCharacterMap
import android.view.KeyEvent
import com.tianma.xsmscode.common.utils.XLog
import com.tianma.xsmscode.xp.hook.BaseHook
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import java.lang.reflect.Method

class SystemInputInjectorHook : BaseHook() {
    @Volatile
    private var receiverRegistered = false
    @Volatile
    private var registerAttempts = 0
    @Volatile
    private var inputHandler: Handler? = null
    @Volatile
    private var inputManagerGlobal: Any? = null
    @Volatile
    private var injectMethod: Method? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun hookInitZygote(): Boolean {
        return true
    }

    override fun initZygote(startupParam: de.robv.android.xposed.IXposedHookZygoteInit.StartupParam) {
        try {
            XposedHelpers.findAndHookMethod(
                "android.app.ActivityThread",
                null,
                "systemMain",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val activityThreadClass = XposedHelpers.findClass("android.app.ActivityThread", null)
                        val activityThread = XposedHelpers.callStaticMethod(
                            activityThreadClass,
                            "currentActivityThread"
                        )
                        val systemContext = XposedHelpers.callMethod(activityThread, "getSystemContext") as? Context
                        if (systemContext != null) {
                            scheduleRegister(systemContext)
                        } else {
                            XposedBridge.log("XSmsCode: systemContext is null in ActivityThread.systemMain hook")
                        }
                    }
                }
            )
            XLog.w("SystemInputInjectorHook: hooked ActivityThread.systemMain in zygote")
            XposedBridge.log("XSmsCode: hooked ActivityThread.systemMain in zygote")
        } catch (t: Throwable) {
            XLog.e("SystemInputInjectorHook: failed to hook ActivityThread.systemMain in zygote", t)
            XposedBridge.log("XSmsCode: failed to hook ActivityThread.systemMain in zygote: ${t.message}")
        }
    }

    // onLoadPackage hook removed; zygote hook handles system_server registration.

    private fun scheduleRegister(context: Context) {
        if (receiverRegistered) return
        mainHandler.postDelayed(
            { registerReceiver(context) },
            500L
        )
    }

    private fun registerReceiver(context: Context) {
        try {
            if (receiverRegistered) return
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    val sendingUid = try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                            XposedHelpers.callMethod(this, "getSendingUid") as Int
                        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                            // On older versions, it's stored in mPendingResult
                            val pendingResult = XposedHelpers.getObjectField(this, "mPendingResult")
                            XposedHelpers.getIntField(pendingResult, "mSendingUid")
                        } else {
                            -1
                        }
                    } catch (t: Throwable) {
                        -1
                    }
                    val appUid = context.applicationInfo.uid
                    if (sendingUid != -1 && sendingUid != Process.SYSTEM_UID && sendingUid != Process.PHONE_UID && sendingUid != appUid) {
                        XLog.w("SystemServer input request rejected from uid=%d", sendingUid)
                        return
                    }
                    val code = intent.getStringExtra("code")
                    if (!code.isNullOrEmpty()) {
                        XLog.i("SystemServer received input request: $code")
                        injectText(code)
                    } else {
                        XLog.w("SystemServer received input request with empty code")
                    }
                }
            }
            val filter = IntentFilter("com.tianma.xsmscode.ACTION_AUTO_INPUT")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                context.registerReceiver(receiver, filter)
            }
            receiverRegistered = true
            XLog.w("SystemInputInjectorReceiver registered")
            XposedBridge.log("XSmsCode: SystemInputInjectorReceiver registered")
        } catch (t: Throwable) {
            registerAttempts += 1
            XLog.e("Failed to register receiver", t)
            XposedBridge.log("XSmsCode: Failed to register receiver: ${t.message}")
            if (registerAttempts < 10) {
                scheduleRegister(context)
            } else {
                XposedBridge.log("XSmsCode: registerReceiver give up after $registerAttempts attempts")
            }
        }
    }

    private fun getInputHandler(): Handler {
        val cached = inputHandler
        if (cached != null) return cached
        return synchronized(this) {
            val existing = inputHandler
            if (existing != null) {
                existing
            } else {
                val thread = HandlerThread("xsmscode-input")
                thread.start()
                Handler(thread.looper).also { inputHandler = it }
            }
        }
    }

    private fun getInputManagerGlobal(): Pair<Any, Method>? {
        val cachedManager = inputManagerGlobal
        val cachedMethod = injectMethod
        if (cachedManager != null && cachedMethod != null) return cachedManager to cachedMethod
        return synchronized(this) {
            val manager = inputManagerGlobal
            val method = injectMethod
            if (manager != null && method != null) {
                manager to method
            } else {
                try {
                    val inputManagerGlobalClass = XposedHelpers.findClass(
                        "android.hardware.input.InputManagerGlobal",
                        null
                    )
                    val instance = XposedHelpers.callStaticMethod(
                        inputManagerGlobalClass,
                        "getInstance"
                    )
                    val inject = XposedHelpers.findMethodBestMatch(
                        inputManagerGlobalClass,
                        "injectInputEvent",
                        android.view.InputEvent::class.java,
                        Int::class.javaPrimitiveType
                    )
                    inputManagerGlobal = instance
                    injectMethod = inject
                    instance to inject
                } catch (t: Throwable) {
                    XLog.e("Failed to resolve InputManagerGlobal", t)
                    null
                }
            }
        }
    }

    private fun injectText(text: String) {
        getInputHandler().post {
            try {
                val events = KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD)
                    .getEvents(text.toCharArray())
                if (events == null) {
                    XLog.e("Failed to create key events for text")
                    return@post
                }

                val managerPair = getInputManagerGlobal() ?: return@post
                val (manager, method) = managerPair
                val mode = 0 // InputManager.INJECT_INPUT_EVENT_MODE_ASYNC
                var injectedCount = 0
                for (event in events) {
                    val result = method.invoke(manager, event, mode) as? Boolean ?: false
                    if (result) injectedCount += 1
                }
                XLog.w("Injected key events from System Server, count=%d", injectedCount)
            } catch (t: Throwable) {
                XLog.e("Failed to inject text from System Server", t)
            }
        }
    }
}

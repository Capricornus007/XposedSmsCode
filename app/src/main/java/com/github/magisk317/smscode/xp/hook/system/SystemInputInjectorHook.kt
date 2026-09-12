package com.github.magisk317.smscode.xp.hook.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Process
import android.view.InputEvent
import android.view.KeyCharacterMap
import com.github.magisk317.smscode.common.utils.XLog
import com.github.magisk317.smscode.xp.helper.ModuleConflictArbiter
import com.github.magisk317.smscode.xp.helper.XposedWrapper
import com.github.magisk317.smscode.xp.hook.BaseHook
import com.github.magisk317.smscode.xp.hookapi.HookBridge
import com.github.magisk317.smscode.xp.hookapi.HookHelpers
import com.github.magisk317.smscode.xp.hookapi.LoadParam
import com.github.magisk317.smscode.xp.hookapi.MethodHook
import com.github.magisk317.smscode.xp.hookapi.MethodHookParam
import com.github.magisk317.smscode.xp.hookapi.ZygoteParam
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

    @Volatile
    private var injectMethodParamCount: Int = 0

    @Volatile
    private var mainHandler: Handler? = null

    @Volatile
    private var amsSystemReadyHooked = false
    @Volatile
    private var suppressionLogged = false
    @Volatile
    private var sendingUidFieldsLogged = false

    override fun hookInitZygote(): Boolean = true

    override fun initZygote(startupParam: ZygoteParam) {
        try {
            // Redmi K60 Ultra (Redmi 23078RKD5C) Android 16 feedback:
            // system_server starts very early, ActivityThread.systemMain might be missed.
            XposedWrapper.findAndHookMethod(
                "android.app.ActivityThread",
                null,
                "systemMain",
                object : MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        XLog.i("XSmsCode: ActivityThread.systemMain hook triggered")
                        val activityThreadClass = HookHelpers.findClass("android.app.ActivityThread", null)
                        val activityThread = HookHelpers.callStaticMethod(
                            activityThreadClass,
                            "currentActivityThread",
                        )
                        val systemContext = HookHelpers.callMethod(activityThread, "getSystemContext") as? Context
                        if (systemContext != null) {
                            scheduleRegister(systemContext)
                        } else {
                            HookBridge.log("XSmsCode: systemContext is null in ActivityThread.systemMain hook")
                        }
                    }
                },
            )
            XLog.w("SystemInputInjectorHook: hooked ActivityThread.systemMain in zygote")
            HookBridge.log("XSmsCode: hooked ActivityThread.systemMain in zygote")
        } catch (t: Throwable) {
            XLog.e("SystemInputInjectorHook: failed to hook ActivityThread.systemMain in zygote", t)
            HookBridge.log("XSmsCode: failed to hook ActivityThread.systemMain in zygote: ${t.message}")
        }
    }

    override fun hookOnLoadPackage(): Boolean = true

    override fun onLoadPackage(lpparam: LoadParam) {
        if (lpparam.packageName != "android") return

        // Fallback for Redmi K60 Ultra (Android 16): 
        // If systemMain was already executed, try immediate initialization or hook systemReady.
        XLog.i("XSmsCode: SystemInputInjectorHook loading for android package")
        
        try {
            // Attempt 1: Check if already ready
            val activityThreadClass = HookHelpers.findClass("android.app.ActivityThread", lpparam.classLoader)
            val activityThread = HookHelpers.callStaticMethod(activityThreadClass, "currentActivityThread")
            if (activityThread != null) {
                val systemContext = HookHelpers.callMethod(activityThread, "getSystemContext") as? Context
                if (systemContext != null) {
                    if (ModuleConflictArbiter.shouldSuppressByRelay(systemContext, "SystemInputInjectorHook#onLoadPackage")) {
                        logSuppressedOnce("onLoadPackage")
                        receiverRegistered = true
                        return
                    }
                    XLog.w("XSmsCode: System context available in onLoadPackage, registering receiver")
                    HookBridge.log("XSmsCode: System context available in onLoadPackage, registering receiver")
                    scheduleRegister(systemContext)
                    if (receiverRegistered) return
                }
            }
        } catch (t: Throwable) {
            XLog.w("Failed to get system context in onLoadPackage: ${t.message}")
        }

        hookAmsSystemReadyFallback(lpparam.classLoader)
    }

    private fun hookAmsSystemReadyFallback(classLoader: ClassLoader?) {
        if (amsSystemReadyHooked) return
        try {
            val amsClass = HookHelpers.findClass("com.android.server.am.ActivityManagerService", classLoader)
            val methods = amsClass.declaredMethods.filter { it.name == "systemReady" }
            if (methods.isEmpty()) {
                XLog.w("SystemInputInjectorHook: no ActivityManagerService.systemReady method found, skip fallback hook")
                return
            }
            methods.forEach { method ->
                HookBridge.hookMethod(
                    method,
                    object : MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            if (receiverRegistered) return
                            XLog.i("XSmsCode: ActivityManagerService.systemReady hook triggered")
                            val context = resolveSystemContext(param.thisObject)
                            if (context != null) {
                                if (ModuleConflictArbiter.shouldSuppressByRelay(context, "SystemInputInjectorHook#systemReady")) {
                                    logSuppressedOnce("systemReady")
                                    receiverRegistered = true
                                    return
                                }
                                scheduleRegister(context)
                            }
                        }
                    },
                )
            }
            amsSystemReadyHooked = true
            XLog.w("SystemInputInjectorHook: hooked ActivityManagerService.systemReady overloads as fallback")
        } catch (t: Throwable) {
            XLog.e("SystemInputInjectorHook: failed to hook AMS.systemReady overloads", t)
        }
    }

    private fun resolveSystemContext(systemService: Any?): Context? {
        if (systemService == null) return null
        return try {
            HookHelpers.getObjectField(systemService, "mContext") as? Context
        } catch (_: Throwable) {
            try {
                HookHelpers.getObjectField(systemService, "mSystemContext") as? Context
            } catch (_: Throwable) {
                null
            }
        }
    }

    private fun scheduleRegister(context: Context) {
        if (receiverRegistered) return
        getMainHandler().postDelayed(
            { registerReceiver(context) },
            DELAY_REGISTER,
        )
    }

    @Suppress("TooGenericExceptionCaught")
    private fun registerReceiver(context: Context) {
        try {
            if (receiverRegistered) return
            if (ModuleConflictArbiter.shouldSuppressByRelay(context, "SystemInputInjectorHook#registerReceiver")) {
                logSuppressedOnce("registerReceiver")
                receiverRegistered = true
                return
            }
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    val sendingUid = resolveSendingUid(this)
                    val appUid = context.applicationInfo.uid
                    if (sendingUid != -1 && sendingUid != Process.SYSTEM_UID && sendingUid != Process.PHONE_UID &&
                        sendingUid != appUid
                    ) {
                        XLog.w("SystemServer input request rejected from uid=%d", sendingUid)
                        return
                    }
                    val code = intent.getStringExtra("code")
                    val autoEnter = intent.getBooleanExtra("autoEnter", false)
                    val inputIntervalMs = intent.getLongExtra("inputIntervalMs", 0L).coerceAtLeast(0L)
                    XLog.w(
                        "Diag system receiver onReceive: uid=%d code_len=%d autoEnter=%s inputIntervalMs=%d",
                        sendingUid,
                        code?.length ?: 0,
                        autoEnter,
                        inputIntervalMs,
                    )
                    if (!code.isNullOrEmpty()) {
                        XLog.i(
                            "SystemServer received input request: %s, autoEnter: %s, inputIntervalMs: %d",
                            code,
                            autoEnter,
                            inputIntervalMs,
                        )
                        injectText(code, autoEnter, inputIntervalMs)
                    } else {
                        XLog.w("SystemServer received input request with empty code")
                    }
                }
            }
            val filter = IntentFilter("com.github.magisk317.smscode.ACTION_AUTO_INPUT")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                context.registerReceiver(receiver, filter)
            }
            receiverRegistered = true
            XLog.w("SystemInputInjectorReceiver registered")
            HookBridge.log("XSmsCode: SystemInputInjectorReceiver registered")
        } catch (t: Throwable) {
            registerAttempts += 1
            XLog.e("Failed to register receiver", t)
            HookBridge.log("XSmsCode: Failed to register receiver: ${t.message}")
            if (registerAttempts < MAX_REGISTER_ATTEMPTS) {
                scheduleRegister(context)
            } else {
                HookBridge.log("XSmsCode: registerReceiver give up after $registerAttempts attempts")
            }
        }
    }

    private fun logSuppressedOnce(stage: String) {
        if (suppressionLogged) return
        synchronized(this) {
            if (suppressionLogged) return
            XLog.w(
                "SystemInputInjectorHook suppressed: reason=%s stage=%s package=%s",
                ModuleConflictArbiter.SUPPRESSION_REASON,
                stage,
                ModuleConflictArbiter.TARGET_RELAY_PACKAGE,
            )
            suppressionLogged = true
        }
    }

    private fun resolveSendingUid(receiver: BroadcastReceiver): Int {
        val direct = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                HookHelpers.callMethod(receiver, "getSendingUid") as Int
            } else {
                null
            }
        } catch (t: Throwable) {
            XLog.w("Failed to get sendingUid: ${t.message}")
            null
        }
        if (direct != null) return direct

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            val pendingResult = runCatching { HookHelpers.getObjectField(receiver, "mPendingResult") }.getOrNull()
            if (pendingResult != null) {
                val candidates = listOf("mSendingUid", "mSenderUid", "mCallingUid")
                for (field in candidates) {
                    runCatching { HookHelpers.getIntField(pendingResult, field) }.getOrNull()?.let { return it }
                }
                logPendingResultFieldsOnce(pendingResult)
            }
        }
        return -1
    }

    private fun logPendingResultFieldsOnce(pendingResult: Any) {
        if (sendingUidFieldsLogged) return
        synchronized(this) {
            if (sendingUidFieldsLogged) return
            sendingUidFieldsLogged = true
        }
        val fields = generateSequence(pendingResult.javaClass) { it.superclass }
            .flatMap { it.declaredFields.asSequence() }
            .map { "${it.name}:${it.type.name}" }
            .distinct()
            .sorted()
            .joinToString(limit = 80, truncated = "...")
        XLog.w("Diag pendingResult fields: %s", fields)
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

    private fun getMainHandler(): Handler {
        val cached = mainHandler
        if (cached != null) return cached
        return synchronized(this) {
            val existing = mainHandler
            if (existing != null) {
                existing
            } else {
                val mainLooper = Looper.getMainLooper()
                val handler = if (mainLooper != null) {
                    Handler(mainLooper)
                } else {
                    // Fallback for early zygote stage when main looper isn't ready yet.
                    getInputHandler()
                }
                mainHandler = handler
                handler
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
                    val classCandidates = buildList {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                            add("android.hardware.input.InputManagerGlobal")
                            add("android.hardware.input.InputManager")
                        } else {
                            add("android.hardware.input.InputManager")
                            add("android.hardware.input.InputManagerGlobal")
                        }
                    }
                    val errors = mutableListOf<String>()
                    for (className in classCandidates) {
                        try {
                            val inputManagerClass = HookHelpers.findClass(className, null)
                            val instance = HookHelpers.callStaticMethod(inputManagerClass, "getInstance") ?: continue
                            val inject = findInjectMethod(inputManagerClass)
                            inputManagerGlobal = instance
                            injectMethod = inject
                            injectMethodParamCount = inject.parameterTypes.size
                            XLog.i(
                                "Resolved input injector: class=%s, method=%s",
                                className,
                                inject.toGenericString(),
                            )
                            return@synchronized instance to inject
                        } catch (t: Throwable) {
                            errors += "$className -> ${t::class.java.simpleName}: ${t.message}"
                        }
                    }
                    throw NoSuchMethodError(
                        "No compatible injectInputEvent found. Details: ${errors.joinToString(" | ")}",
                    )
                } catch (t: Throwable) {
                    XLog.e("Failed to resolve InputManagerGlobal", t)
                    null
                }
            }
        }
    }

    private fun findInjectMethod(inputManagerClass: Class<*>): Method {
        val candidates = (inputManagerClass.declaredMethods + inputManagerClass.methods).distinctBy {
            "${it.name}#${it.parameterTypes.joinToString(",") { p -> p.name }}"
        }
        val preferred = candidates.firstOrNull { method ->
            if (method.name != "injectInputEvent") return@firstOrNull false
            val params = method.parameterTypes
            params.size == 2 && params[0] == InputEvent::class.java && params[1] == Int::class.javaPrimitiveType
        }
        if (preferred != null) {
            preferred.isAccessible = true
            return preferred
        }

        val fallback = candidates.firstOrNull { method ->
            if (method.name != "injectInputEvent") return@firstOrNull false
            val params = method.parameterTypes
            when (params.size) {
                2 -> InputEvent::class.java.isAssignableFrom(params[0]) &&
                    (params[1] == Int::class.javaPrimitiveType || params[1] == Int::class.java)
                3 -> InputEvent::class.java.isAssignableFrom(params[0]) &&
                    (params[1] == Int::class.javaPrimitiveType || params[1] == Int::class.java) &&
                    (params[2] == Int::class.javaPrimitiveType || params[2] == Int::class.java)
                else -> false
            }
        }
        if (fallback != null) {
            fallback.isAccessible = true
            return fallback
        }

        val signatureDump = candidates
            .filter { it.name == "injectInputEvent" }
            .joinToString("; ") { it.toGenericString() }
            .ifBlank { "none" }
        throw NoSuchMethodException("injectInputEvent signatures: $signatureDump")
    }

    private fun invokeInject(manager: Any, method: Method, event: InputEvent, mode: Int): Boolean {
        val result = when (injectMethodParamCount) {
            2 -> method.invoke(manager, event, mode)
            3 -> method.invoke(manager, event, mode, 0)
            else -> return false
        }
        return when (result) {
            is Boolean -> result
            is Number -> result.toInt() != 0
            else -> false
        }
    }

    private fun injectText(text: String, autoEnter: Boolean = false, inputIntervalMs: Long = 0L) {
        getInputHandler().post {
            try {
                val managerPair = getInputManagerGlobal() ?: return@post
                val (manager, method) = managerPair
                val mode = 0 // InputManager.INJECT_INPUT_EVENT_MODE_ASYNC
                var injectedCount = 0
                val keyCharacterMap = KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD)
                text.forEachIndexed { index, char ->
                    val events = keyCharacterMap.getEvents(charArrayOf(char))
                    if (events == null) {
                        XLog.w("Failed to create key events for char: %s", char.toString())
                        return@forEachIndexed
                    }
                    for (event in events) {
                        val result = invokeInject(manager, method, event, mode)
                        if (result) injectedCount += 1
                    }
                    if (inputIntervalMs > 0L && index < text.lastIndex) {
                        Thread.sleep(inputIntervalMs)
                    }
                }
                XLog.w("Injected key characters from System Server, count=%d", injectedCount)

                if (autoEnter) {
                    val now = android.os.SystemClock.uptimeMillis()
                    val downEvent = android.view.KeyEvent(now, now, android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_ENTER, 0)
                    val upEvent = android.view.KeyEvent(now, now, android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_ENTER, 0)

                    val downResult = invokeInject(manager, method, downEvent, mode)
                    val upResult = invokeInject(manager, method, upEvent, mode)

                    if (downResult && upResult) {
                        XLog.w("Injected KEYCODE_ENTER from System Server")
                    } else {
                        XLog.e("Failed to inject KEYCODE_ENTER: down=$downResult, up=$upResult")
                    }
                }
            } catch (t: Throwable) {
                XLog.e("Failed to inject text/enter from System Server", t)
            }
        }
    }

    companion object {
        private const val DELAY_REGISTER = 500L
        private const val MAX_REGISTER_ATTEMPTS = 10

        @Suppress("unused")
        const val ACTION_AUTO_INPUT = "com.github.magisk317.smscode.ACTION_AUTO_INPUT"
    }
}

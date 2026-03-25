package com.github.magisk317.smscode.xp.hook.code

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.Message
import android.provider.Telephony
import com.github.tianma8023.xposed.smscode.BuildConfig
import com.github.magisk317.smscode.core.R
import com.github.magisk317.smscode.common.constant.NotificationConst
import com.github.magisk317.smscode.common.utils.ActivationDiagnosticsStore
import io.github.magisk317.smscode.xposed.utils.ModuleActivationStore
import com.github.magisk317.smscode.common.utils.NotificationUtils
import com.github.magisk317.smscode.common.utils.PrefsReader
import com.github.magisk317.smscode.common.utils.SmsBlacklistUtils
import io.github.magisk317.smscode.xposed.utils.XLog
import com.github.magisk317.smscode.data.db.entity.SmsMsg
import com.github.magisk317.smscode.xp.helper.ModuleConflictArbiter
import com.github.magisk317.smscode.xp.helper.RelayConflictNoticeHelper
import io.github.magisk317.smscode.xposed.helper.XposedWrapper
import io.github.magisk317.smscode.xposed.hook.BaseHook
import com.github.magisk317.smscode.xp.hook.code.action.impl.OperateSmsAction
import io.github.magisk317.smscode.xposed.hookapi.HookEnv
import io.github.magisk317.smscode.xposed.hookapi.MethodHook
import io.github.magisk317.smscode.xposed.hookapi.HookBridge
import io.github.magisk317.smscode.xposed.hookapi.HookHelpers
import io.github.magisk317.smscode.xposed.hookapi.LoadParam
import io.github.magisk317.smscode.xposed.hookapi.MethodHookParam
import com.github.magisk317.smscode.common.utils.StorageUtils
import java.io.File
import java.io.RandomAccessFile
import java.lang.reflect.Method
import java.util.Collections
import java.util.LinkedHashMap
import java.util.concurrent.Executors

/**
 * Hook class com.android.internal.telephony.InboundSmsHandler
 */
class SmsHandlerHook : BaseHook() {

    private var mPhoneContext: Context? = null
    private var mPluginContext: Context? = null
    private var smsInboxObserver: SmsInboxObserver? = null
    @Volatile
    private var suppressionLogged = false

    override fun onLoadPackage(lpparam: LoadParam) {
        if (ANDROID_PHONE_PACKAGE == lpparam.packageName) {
            val sharedHookKey = buildSharedProcessKey(
                prefix = "hook_init",
                packageName = lpparam.packageName,
                processName = lpparam.processName,
            )
            val sharedHookAge = claimProcessPropertyWithinWindow(
                key = sharedHookKey,
                windowMs = SHARED_HOOK_INIT_WINDOW_MS,
            )
            if (sharedHookAge != null) {
                XLog.w(
                    "SmsHandlerHook shared init skip: pkg=%s process=%s pid=%d ageMs=%d",
                    lpparam.packageName,
                    lpparam.processName,
                    android.os.Process.myPid(),
                    sharedHookAge,
                )
                return
            }
            val hookKey = buildHookInstallKey(lpparam)
            if (!markHookInstalled(hookKey)) {
                XLog.w(
                    "SmsHandlerHook already initialized, skip duplicate load: pkg=%s process=%s loader=%s",
                    lpparam.packageName,
                    lpparam.processName,
                    Integer.toHexString(System.identityHashCode(lpparam.classLoader)),
                )
                return
            }
            XLog.i("SmsCode initializing")
            printDeviceInfo()
            try {
                hookSmsHandler(lpparam.classLoader)
            } catch (e: Throwable) {
                XLog.e("Failed to hook SmsHandler", e)
            }
            XLog.i("SmsCode initialize completely")
        }
    }

    private fun printDeviceInfo() {
        XLog.i("Phone manufacturer: %s", Build.MANUFACTURER)
        XLog.i("Phone model: %s", Build.MODEL)
        XLog.i("Android version: %s", Build.VERSION.RELEASE)
        val xposedVersion = resolveXposedVersion()
        if (xposedVersion != null) {
            XLog.i("Xposed bridge version: %d", xposedVersion)
        } else {
            XLog.i("Xposed bridge version: unknown")
        }
        XLog.i("SmsCode version: %s (%d)", BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)
    }

    private fun resolveXposedVersion(): Int? {
        return HookEnv.api.getXposedBridgeVersion() ?: HookEnv.api.getApiVersion()
    }

    private fun hookSmsHandler(classloader: ClassLoader) {
        hookConstructor(classloader)
        hookDispatchIntent(classloader)
        hookSmsDispatcherChain(classloader)
    }

    private fun hookConstructor(classloader: ClassLoader) {
        // minSdkVersion 35: Only hook for Android 14+ / 15+
        hookConstructor34(classloader)
    }

    // Android 14+
    private fun hookConstructor34(classLoader: ClassLoader) {
        XLog.i("Hooking InboundSmsHandler constructor for android v34+")
        val smsHandlerClazz = XposedWrapper.findClass(SMS_HANDLER_CLASS, classLoader)
        if (smsHandlerClazz != null) {
            HookBridge.hookAllConstructors(smsHandlerClazz, ConstructorHook())
        }
    }

    private fun hookDispatchIntent(classloader: ClassLoader) {
        // minSdkVersion 35: Only hook for Android 10+ / 15+
        hookDispatchIntent29(classloader)
    }

    private fun hookSmsDispatcherChain(classLoader: ClassLoader) {
        // Some ROMs/Android versions may dispatch SMS via alternative paths.
        hookDispatcherMethods(
            classLoader,
            SMS_HANDLER_CLASS,
            listOf(
                "dispatchSmsDeliveryIntent",
                "dispatchSmsDeliveryIntentToApp",
                "dispatchSmsDeliveryIntentToRegisteredReceivers",
            ),
        )
        hookDispatcherMethods(
            classLoader,
            "com.android.internal.telephony.SmsDispatchersController",
            listOf(
                "dispatchSmsDeliveryIntent",
                "dispatchSmsDeliveryIntentToApp",
                "dispatchSmsDeliveryIntentToRegisteredReceivers",
                "dispatchSmsDeliveryIntentToAppWithPermission",
            ),
        )
    }

    private fun hookDispatcherMethods(
        classLoader: ClassLoader,
        className: String,
        methodNames: List<String>,
    ) {
        val clazz = XposedWrapper.findClass(className, classLoader) ?: return
        methodNames.forEach { name ->
            val methods = clazz.declaredMethods.filter { it.name == name }
            if (methods.isEmpty()) return@forEach
            methods.forEach { method ->
                XposedWrapper.hookMethod(
                    method,
                    object : MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            val intent = extractOrBuildSmsIntent(
                                param.args,
                                fallbackAction = Telephony.Sms.Intents.SMS_DELIVER_ACTION,
                            )
                            val action = intent?.action ?: extractIntentAction(param.args)
                            val pluginContext = getPluginContext()
                            if (isVerboseDiagEnabled(pluginContext)) {
                                XLog.w(
                                    "Diag SMS dispatch chain: class=%s owner=%s method=%s action=%s args=%d",
                                    className,
                                    param.thisObject?.javaClass?.name ?: className,
                                    name,
                                    action ?: "<none>",
                                    param.args.size,
                                )
                            }
                            if (className == SMS_HANDLER_CLASS) {
                                maybeBlockFromDispatchChain(name, param, intent)
                            }
                        }
                    },
                )
            }
        }
    }

    private fun extractIntentAction(args: Array<Any?>?): String? {
        if (args == null) return null
        for (arg in args) {
            if (arg is Intent) {
                return arg.action
            }
        }
        return null
    }

    // Android 10+
    private fun hookDispatchIntent29(classLoader: ClassLoader) {
        XLog.d("Hooking dispatchIntent() for Android v29+")
        val inboundSmsHandlerClass = XposedWrapper.findClass(SMS_HANDLER_CLASS, classLoader) ?: run {
            XLog.e("Class: %s cannot found", SMS_HANDLER_CLASS)
            return
        }

        val dispatchIntentMethodName = "dispatchIntent"
        val methods = inboundSmsHandlerClass.declaredMethods.filter { it.name == dispatchIntentMethodName }
        if (methods.isEmpty()) {
            XLog.e("Method %s for Class %s cannot found", dispatchIntentMethodName, SMS_HANDLER_CLASS)
            return
        }
        methods.forEach { method ->
            var receiverIndex = -1
            method.parameterTypes.forEachIndexed { index, clazz ->
                if (receiverIndex < 0 && BroadcastReceiver::class.java.isAssignableFrom(clazz)) {
                    receiverIndex = index
                }
            }
            XposedWrapper.hookMethod(method, DispatchIntentHook(receiverIndex))
        }
    }

    private inner class ConstructorHook : MethodHook() {
        @Throws(Throwable::class)
        override fun afterHookedMethod(param: MethodHookParam) {
            try {
                afterConstructorHandler(param)
            } catch (e: Throwable) {
                XLog.e("Error occurred in constructor hook", e)
                throw e
            }
        }
    }

    private fun afterConstructorHandler(param: MethodHookParam) {
        val context = param.args.getOrNull(1) as? Context ?: return
        if (mPhoneContext == null) {
            mPhoneContext = context
            try {
                mPluginContext = mPhoneContext?.createPackageContext(
                    SMSCODE_PACKAGE,
                    Context.CONTEXT_IGNORE_SECURITY,
                )
                if (mPluginContext != null) {
                    val pluginContext = mPluginContext ?: return
                    RelayConflictNoticeHelper.initNotificationChannel(pluginContext, context)
                    val suppressByRelay = ModuleConflictArbiter.shouldSuppressByRelay(
                        mPhoneContext,
                        "SmsHandlerHook#constructor",
                    )
                    if (PrefsReader.showCodeNotification(pluginContext)) {
                        initNotificationChannel()
                        if (!suppressByRelay) {
                            registerCopyCodeReceiver()
                        }
                    }
                    ModuleActivationStore.markActivated(pluginContext)
                    ActivationDiagnosticsStore.recordHookHeartbeat(
                        context = pluginContext,
                        packageName = ANDROID_PHONE_PACKAGE,
                        processName = context.applicationInfo?.processName ?: ANDROID_PHONE_PACKAGE,
                        source = "sms_handler_constructor",
                        verboseLogging = PrefsReader.isVerboseLogMode(pluginContext),
                    )
                    if (suppressByRelay) {
                        logSuppressedOnce("constructor")
                    } else {
                        registerSmsInboxObserver()
                    }
                } else {
                    XLog.e("Plugin context is null after creation attempt")
                }
            } catch (e: Exception) {
                XLog.e("Create plugin context failed: %s", e)
            }
        }
    }

    private fun initNotificationChannel() {
        val channelId = NotificationConst.CHANNEL_ID_SMSCODE_NOTIFICATION
        val channelName = getPluginContext()?.getString(R.string.channel_name_smscode_notification) ?: ""
        mPhoneContext?.let {
            NotificationUtils.createNotificationChannel(
                it,
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_HIGH,
            )
            XLog.d("Init notification channel succeed")
        }
    }

    private fun registerCopyCodeReceiver() {
        val pluginContext = mPluginContext ?: return
        if (!PrefsReader.showCodeNotification(pluginContext)) return
        mPhoneContext?.let {
            CopyCodeReceiver.registerMe(it)
            XLog.d("Register copy code receiver")
        }
    }

    private fun registerSmsInboxObserver() {
        val pluginContext = mPluginContext ?: return
        val phoneContext = mPhoneContext ?: return
        if (smsInboxObserver != null) return
        val observerKey = buildSharedProcessKey(
            prefix = "sms_observer",
            packageName = phoneContext.packageName,
            processName = phoneContext.applicationInfo?.processName ?: phoneContext.packageName,
        )
        val observerAge = claimProcessPropertyWithinWindow(
            key = observerKey,
            windowMs = SHARED_OBSERVER_WINDOW_MS,
        )
        if (observerAge != null) {
            XLog.w(
                "SmsInboxObserver shared register skip: key=%s pid=%d ageMs=%d",
                observerKey,
                android.os.Process.myPid(),
                observerAge,
            )
            return
        }
        smsInboxObserver = SmsInboxObserver(pluginContext, phoneContext).also { it.register() }
    }

    private inner class DispatchIntentHook(private val mReceiverIndex: Int) : MethodHook() {
        @Throws(Throwable::class)
        override fun beforeHookedMethod(param: MethodHookParam) {
            try {
                beforeDispatchIntentHandler(param, mReceiverIndex)
            } catch (e: Throwable) {
                XLog.e("Error occurred in dispatchIntent() hook, ", e)
            }
        }
    }

    private fun beforeDispatchIntentHandler(param: MethodHookParam, receiverIndex: Int) {
        val intent = param.args.getOrNull(0) as? Intent ?: return
        val action = intent.action

        if (BuildConfig.DEBUG) {
            XLog.d("SmsHandlerHook: Received intent action: $action")
            intent.extras?.let { bundle ->
                XLog.d("SmsHandlerHook: Extra keys = %s", bundle.keySet().joinToString(","))
            }
        }

        if (!SmsIntentHookSupport.isSmsAction(action)) {
            return
        }
        val eventId = SmsIntentHookSupport.ensureEventId(intent)
        val pluginContext = getPluginContext()
        val phoneContext = mPhoneContext
        if (pluginContext == null || phoneContext == null) {
            XLog.e("Context is null, skip parsing. pluginContext: %s, phoneContext: %s", pluginContext, phoneContext)
            return
        }
        if (SmsIntentHookSupport.markDispatchHandled(intent, action)) {
            XLog.w(
                "Diag SMS dispatch duplicate skip: event_id=%s action=%s source=intent_extra",
                eventId,
                action,
            )
            return
        }
        if (shouldSkipDispatchBySharedDedup(pluginContext, eventId, action)) {
            return
        }
        val pduCount = getPduCount(intent)
        XLog.w(
            "Diag SMS intent intercepted: event_id=%s action=%s, pduCount=%d, extras=%s",
            eventId,
            action,
            pduCount,
            intent.extras != null,
        )
        ActivationDiagnosticsStore.recordHookHeartbeat(
            context = pluginContext,
            packageName = ANDROID_PHONE_PACKAGE,
            processName = phoneContext.applicationInfo?.processName ?: ANDROID_PHONE_PACKAGE,
            source = "sms_handler_dispatch",
            verboseLogging = PrefsReader.isVerboseLogMode(pluginContext),
        )
        if (ModuleConflictArbiter.shouldSuppressByRelay(phoneContext, "SmsHandlerHook#dispatchIntent")) {
            logSuppressedOnce("dispatchIntent")
            RelayConflictNoticeHelper.notifyConflictOnSms(pluginContext, phoneContext, eventId)
            return
        }
        val smsMsg = runCatching { SmsMsg.fromIntent(intent) }.getOrNull()
        val blacklistResult = SmsBlacklistUtils.match(pluginContext, smsMsg?.sender, smsMsg?.body)
        if (blacklistResult.matched) {
            XLog.w(
                "Diag sms blacklist matched: event_id=%s type=%s, pattern=%s, delete=%s, block=%s",
                eventId,
                blacklistResult.matchType,
                blacklistResult.pattern,
                blacklistResult.actionDelete,
                blacklistResult.actionBlock,
            )
            if (blacklistResult.actionDelete && !blacklistResult.actionBlock && smsMsg != null) {
                scheduleBlacklistDelete(pluginContext, phoneContext, smsMsg)
            }
            if (blacklistResult.actionBlock) {
                XLog.w("Diag sms block reason=%s event_id=%s", SmsBlockEvaluator.BLOCK_REASON_BLACKLIST, eventId)
                param.args.getOrNull(receiverIndex)?.let { receiver ->
                    val inbound = param.thisObject ?: return
                    val blocked = deleteRawTableAndSendMessage(
                        inboundSmsHandler = inbound,
                        smsReceiver = receiver,
                        reason = SmsBlockEvaluator.BLOCK_REASON_BLACKLIST,
                        eventId = eventId,
                    )
                    if (blocked) {
                        param.result = null
                    }
                }
                return
            }
        }

        val parseResult = CodeWorker(pluginContext, phoneContext, intent, eventId).parse()
        if (parseResult == null) {
            XLog.w("Diag parse result is null: event_id=%s no code matched or parse failed", eventId)
        } else {
            XLog.w("Diag parse result: event_id=%s blockSms=%s", eventId, parseResult.isBlockSms)
        }
        if (parseResult != null) {
            if (parseResult.isBlockSms) {
                XLog.w("Diag sms block reason=%s event_id=%s", SmsBlockEvaluator.BLOCK_REASON_PREF_BLOCK, eventId)
                param.args.getOrNull(receiverIndex)?.let { receiver ->
                    val inbound = param.thisObject ?: return
                    val blocked = deleteRawTableAndSendMessage(
                        inboundSmsHandler = inbound,
                        smsReceiver = receiver,
                        reason = SmsBlockEvaluator.BLOCK_REASON_PREF_BLOCK,
                        eventId = eventId,
                    )
                    if (blocked) {
                        param.result = null
                    }
                }
            } else {
                XLog.w(
                    "Diag allow system inbox persist: event_id=%s sender_hash=%s body_len=%d",
                    eventId,
                    senderHash(smsMsg?.sender),
                    smsMsg?.body?.length ?: 0,
                )
            }
        }
    }

    private fun getPduCount(intent: Intent): Int {
        return try {
            Telephony.Sms.Intents.getMessagesFromIntent(intent)?.size ?: -1
        } catch (t: Throwable) {
            XLog.w("Diag getPduCount failed: %s", t.message ?: "unknown")
            -1
        }
    }

    private fun scheduleBlacklistDelete(pluginContext: Context, phoneContext: Context, smsMsg: SmsMsg) {
        SMS_OPERATION_EXECUTOR.execute {
            runCatching {
                OperateSmsAction(
                    pluginContext,
                    phoneContext,
                    smsMsg,
                    OperateSmsAction.FORCE_DELETE,
                ).call()
            }.onFailure {
                XLog.w("Diag sms blacklist delete task failed: %s", it.message ?: "unknown")
            }
        }
    }

    private fun deleteRawTableAndSendMessage(
        inboundSmsHandler: Any,
        smsReceiver: Any,
        reason: String,
        eventId: String,
    ): Boolean {
        XLog.w("Diag raw-table delete start: reason=%s event_id=%s", reason, eventId)
        val token = Binder.clearCallingIdentity()
        var deleteSucceeded = false
        try {
            deleteFromRawTable(inboundSmsHandler, smsReceiver, reason, eventId)
            deleteSucceeded = true
        } catch (e: Throwable) {
            XLog.e("Error occurs when delete SMS data from raw table", e)
        } finally {
            Binder.restoreCallingIdentity(token)
        }

        var sendSucceeded = false
        try {
            sendEventBroadcastComplete(inboundSmsHandler, reason, eventId)
            sendSucceeded = true
        } catch (e: Throwable) {
            XLog.e("Error occurs when sending broadcast complete", e)
        }
        return deleteSucceeded && sendSucceeded
    }

    private fun sendEventBroadcastComplete(inboundSmsHandler: Any, reason: String, eventId: String) {
        XLog.d("Send event(EVENT_BROADCAST_COMPLETE): reason=%s event_id=%s", reason, eventId)
        if (trySendMessage(inboundSmsHandler, EVENT_BROADCAST_COMPLETE)) {
            return
        }
        if (!loggedSendMessageSignatures) {
            loggedSendMessageSignatures = true
            logMethodSignatures(
                "Diag sendMessage signatures",
                inboundSmsHandler.javaClass,
                "sendMessage",
            )
        }
    }

    @Throws(ReflectiveOperationException::class)
    private fun deleteFromRawTable(inboundSmsHandler: Any, smsReceiver: Any, reason: String, eventId: String) {
        // minSdkVersion 35: Always use Android 24+ method
        deleteFromRawTable24(inboundSmsHandler, smsReceiver, reason, eventId)
    }

    @Throws(ReflectiveOperationException::class)
    private fun deleteFromRawTable24(inboundSmsHandler: Any, smsReceiver: Any, reason: String, eventId: String) {
        XLog.d("Delete raw SMS data from database on Android 24+: reason=%s event_id=%s", reason, eventId)
        val deleteWhere = HookHelpers.getObjectField(smsReceiver, "mDeleteWhere")
        val deleteWhereArgs = HookHelpers.getObjectField(smsReceiver, "mDeleteWhereArgs")
        val markDeleted = 2
        val handlerClass = HookHelpers.findClass(SMS_HANDLER_CLASS, inboundSmsHandler.javaClass.classLoader)
        val cached = cachedDeleteRawMethod
        if (cached != null) {
            val args = buildDeleteRawArgs(cached.parameterTypes, deleteWhere, deleteWhereArgs, markDeleted)
            if (args != null) {
                cached.invoke(inboundSmsHandler, *args)
                return
            } else {
                cachedDeleteRawMethod = null
            }
        }

        val methods = collectMethods(handlerClass, "deleteFromRawTable")
        var lastError: Throwable? = null
        for (method in methods) {
            val args = buildDeleteRawArgs(method.parameterTypes, deleteWhere, deleteWhereArgs, markDeleted) ?: continue
            try {
                method.invoke(inboundSmsHandler, *args)
                cachedDeleteRawMethod = method
                return
            } catch (e: Throwable) {
                lastError = e
            }
        }
        if (!loggedDeleteRawSignatures) {
            loggedDeleteRawSignatures = true
            logMethodSignatures(
                "Diag deleteFromRawTable signatures",
                handlerClass,
                "deleteFromRawTable",
            )
        }
        if (lastError != null) {
            throw lastError
        } else {
            throw NoSuchMethodException("No suitable method for ${handlerClass.name}#deleteFromRawTable")
        }
    }

    private fun trySendMessage(inboundSmsHandler: Any, what: Int): Boolean {
        val cached = cachedSendMessageMethod
        if (cached != null) {
            val args = buildSendMessageArgs(cached.parameterTypes, inboundSmsHandler, what)
            if (args != null) {
                return runCatching {
                    cached.invoke(inboundSmsHandler, *args)
                    true
                }.getOrElse {
                    cachedSendMessageMethod = null
                    false
                }
            } else {
                cachedSendMessageMethod = null
            }
        }

        val methods = collectMethods(inboundSmsHandler.javaClass, "sendMessage")
        for (method in methods) {
            val args = buildSendMessageArgs(method.parameterTypes, inboundSmsHandler, what) ?: continue
            val ok = runCatching {
                method.invoke(inboundSmsHandler, *args)
                cachedSendMessageMethod = method
                true
            }.getOrElse { false }
            if (ok) return true
        }
        return false
    }

    private fun buildSendMessageArgs(
        parameterTypes: Array<Class<*>>,
        inboundSmsHandler: Any,
        what: Int,
    ): Array<Any?>? {
        val message = obtainMessage(inboundSmsHandler, what)
        val intValues = ArrayDeque<Any?>(listOf(what, 0))
        val longValues = ArrayDeque<Any?>(listOf(0L))
        val boolValues = ArrayDeque<Any?>(listOf(false))
        val args = arrayOfNulls<Any?>(parameterTypes.size)
        for (i in parameterTypes.indices) {
            val type = parameterTypes[i]
            when {
                type == Int::class.javaPrimitiveType || type == Int::class.javaObjectType ->
                    args[i] = if (intValues.isNotEmpty()) intValues.removeFirst() else 0
                type == Long::class.javaPrimitiveType || type == Long::class.javaObjectType ->
                    args[i] = if (longValues.isNotEmpty()) longValues.removeFirst() else 0L
                type == Boolean::class.javaPrimitiveType || type == Boolean::class.javaObjectType ->
                    args[i] = if (boolValues.isNotEmpty()) boolValues.removeFirst() else false
                type == Message::class.java -> args[i] = message
                else -> args[i] = null
            }
        }
        return args
    }

    private fun obtainMessage(inboundSmsHandler: Any, what: Int): Message {
        return runCatching {
            HookHelpers.callMethod(inboundSmsHandler, "obtainMessage", what) as? Message
        }.getOrNull() ?: Message.obtain().apply { this.what = what }
    }

    private fun buildDeleteRawArgs(
        parameterTypes: Array<Class<*>>,
        deleteWhere: Any?,
        deleteWhereArgs: Any?,
        markDeleted: Int,
    ): Array<Any?>? {
        val stringValues = ArrayDeque<Any?>(listOf(deleteWhere, PERSISTENT_DEVICE_ID_DEFAULT, null))
        val intValues = ArrayDeque<Any?>(listOf(markDeleted, 0))
        val longValues = ArrayDeque<Any?>(listOf(0L))
        val boolValues = ArrayDeque<Any?>(listOf(false))
        val args = arrayOfNulls<Any?>(parameterTypes.size)
        for (i in parameterTypes.indices) {
            val type = parameterTypes[i]
            when {
                type == String::class.java -> args[i] = if (stringValues.isNotEmpty()) stringValues.removeFirst() else null
                type.isArray && type.componentType == String::class.java ->
                    args[i] = deleteWhereArgs
                type == Int::class.javaPrimitiveType || type == Int::class.javaObjectType ->
                    args[i] = if (intValues.isNotEmpty()) intValues.removeFirst() else 0
                type == Long::class.javaPrimitiveType || type == Long::class.javaObjectType ->
                    args[i] = if (longValues.isNotEmpty()) longValues.removeFirst() else 0L
                type == Boolean::class.javaPrimitiveType || type == Boolean::class.javaObjectType ->
                    args[i] = if (boolValues.isNotEmpty()) boolValues.removeFirst() else false
                else -> args[i] = null
            }
        }
        return args
    }

    private fun collectMethods(clazz: Class<*>, methodName: String): List<Method> {
        val methods = mutableListOf<Method>()
        var current: Class<*>? = clazz
        while (current != null) {
            current.declaredMethods
                .filter { it.name == methodName }
                .forEach { method ->
                    method.isAccessible = true
                    methods += method
                }
            current = current.superclass
        }
        return methods
    }

    private fun logMethodSignatures(tag: String, clazz: Class<*>, methodName: String) {
        val methods = collectMethods(clazz, methodName)
        if (methods.isEmpty()) {
            XLog.w("%s: no method %s in %s", tag, methodName, clazz.name)
            return
        }
        val signatures = methods.joinToString(limit = 80, truncated = "...") { method ->
            val params = method.parameterTypes.joinToString(",") { it.name }
            "${method.name}($params):${method.returnType.name}"
        }
        XLog.w("%s: %s methods=[%s]", tag, clazz.name, signatures)
    }

    private fun shouldSkipDispatchBySharedDedup(
        pluginContext: Context,
        eventId: String,
        action: String?,
    ): Boolean {
        if (eventId.isBlank() || action.isNullOrBlank()) return false
        val now = System.currentTimeMillis()
        val key = "$eventId|$action"
        return runCatching {
            val file = File(StorageUtils.getExternalFilesDir(pluginContext), DISPATCH_DEDUP_FILE_NAME)
            file.parentFile?.mkdirs()
            RandomAccessFile(file, "rw").use { raf ->
                raf.channel.use { channel ->
                    channel.lock().use {
                        val entries = readDispatchDedupEntries(raf)
                        val iterator = entries.entries.iterator()
                        while (iterator.hasNext()) {
                            val entry = iterator.next()
                            if (now - entry.value > DISPATCH_DEDUP_WINDOW_MS) {
                                iterator.remove()
                            }
                        }
                        val last = entries[key]
                        if (last != null && now - last <= DISPATCH_DEDUP_WINDOW_MS) {
                            XLog.w(
                                "Diag SMS dispatch duplicate skip: event_id=%s action=%s source=shared_store ageMs=%d",
                                eventId,
                                action,
                                now - last,
                            )
                            writeDispatchDedupEntries(raf, entries)
                            return true
                        }
                        entries[key] = now
                        while (entries.size > MAX_DISPATCH_DEDUP_ENTRIES) {
                            val firstKey = entries.entries.firstOrNull()?.key ?: break
                            entries.remove(firstKey)
                        }
                        writeDispatchDedupEntries(raf, entries)
                        false
                    }
                }
            }
        }.onFailure {
            XLog.w(
                "Diag SMS dispatch shared dedup failed: event_id=%s action=%s err=%s",
                eventId,
                action,
                it.message ?: it.javaClass.simpleName,
            )
        }.getOrDefault(false)
    }

    private fun readDispatchDedupEntries(raf: RandomAccessFile): LinkedHashMap<String, Long> {
        val entries = LinkedHashMap<String, Long>()
        raf.seek(0L)
        while (true) {
            val rawLine = raf.readLine() ?: break
            val line = rawLine.trim()
            if (line.isBlank()) continue
            val split = line.indexOf('=')
            if (split <= 0) continue
            val key = line.substring(0, split)
            val value = line.substring(split + 1).toLongOrNull() ?: continue
            entries[key] = value
        }
        return entries
    }

    private fun writeDispatchDedupEntries(
        raf: RandomAccessFile,
        entries: LinkedHashMap<String, Long>,
    ) {
        raf.setLength(0L)
        raf.seek(0L)
        val content = buildString {
            entries.forEach { (key, value) ->
                append(key)
                append('=')
                append(value)
                append('\n')
            }
        }
        raf.write(content.toByteArray())
    }

    private fun senderHash(sender: String?): String {
        val value = sender.orEmpty()
        if (value.isBlank()) return "none"
        return Integer.toHexString(value.hashCode())
    }

    private fun maybeBlockFromDispatchChain(
        methodName: String,
        param: MethodHookParam,
        smsIntent: Intent?,
    ) {
        val intent = smsIntent ?: return
        val action = intent.action
        if (!SmsIntentHookSupport.isSmsAction(action)) return
        val pluginContext = getPluginContext() ?: return
        val phoneContext = mPhoneContext ?: return
        if (ModuleConflictArbiter.shouldSuppressByRelay(phoneContext, "SmsHandlerHook#$methodName")) {
            logSuppressedOnce("dispatchChain:$methodName")
            return
        }
        val eventId = SmsIntentHookSupport.ensureEventId(intent)
        val evaluation = SmsBlockEvaluator.evaluate(pluginContext, intent, eventId, "dispatch_chain") ?: return
        if (evaluation.blacklistDeleteOnly && evaluation.smsMsg != null) {
            scheduleBlacklistDelete(pluginContext, phoneContext, evaluation.smsMsg)
        }
        val reason = evaluation.blockReason ?: return
        if (shouldSkipDispatchChainBlock(evaluation.smsMsg, action, reason)) {
            return
        }
        ActivationDiagnosticsStore.recordHookHeartbeat(
            context = pluginContext,
            packageName = ANDROID_PHONE_PACKAGE,
            processName = phoneContext.applicationInfo?.processName ?: ANDROID_PHONE_PACKAGE,
            source = "sms_handler_dispatch_chain",
            verboseLogging = PrefsReader.isVerboseLogMode(pluginContext),
        )
        XLog.w(
            "Diag SMS dispatch chain block: method=%s reason=%s event_id=%s",
            methodName,
            reason,
            eventId,
        )
        CodeWorker(pluginContext, phoneContext, intent, eventId).parse()
        val inbound = param.thisObject ?: return
        val smsReceiver = findRawTableReceiver(param.args)
        if (smsReceiver != null) {
            val blocked = deleteRawTableAndSendMessage(
                inboundSmsHandler = inbound,
                smsReceiver = smsReceiver,
                reason = reason,
                eventId = eventId,
            )
            if (!blocked) {
                XLog.w(
                    "Diag dispatch chain block aborted: cleanup incomplete method=%s event_id=%s",
                    methodName,
                    eventId,
                )
                return
            }
        } else {
            XLog.w(
                "Diag dispatch chain block fallback: receiver unavailable method=%s event_id=%s",
                methodName,
                eventId,
            )
            return
        }
        param.result = defaultResultForType((param.method as? Method)?.returnType)
    }

    private fun extractOrBuildSmsIntent(args: Array<Any?>?, fallbackAction: String): Intent? {
        if (args == null) return null
        args.forEach { arg ->
            if (arg is Intent) {
                return arg
            }
        }
        val pduList = args.firstNotNullOfOrNull { arg ->
            val array = arg as? Array<*> ?: return@firstNotNullOfOrNull null
            val pdus = array.mapNotNull { it as? ByteArray }
            if (pdus.isEmpty() || pdus.size != array.size) null else pdus
        } ?: return null
        val format = args.firstNotNullOfOrNull { arg ->
            val text = arg as? String ?: return@firstNotNullOfOrNull null
            if (text.equals("3gpp", ignoreCase = true) || text.equals("3gpp2", ignoreCase = true)) {
                text
            } else {
                null
            }
        }
        return Intent(fallbackAction).apply {
            putExtra("pdus", pduList.toTypedArray())
            if (!format.isNullOrBlank()) {
                putExtra("format", format)
            }
        }
    }

    private fun findRawTableReceiver(args: Array<Any?>?): Any? {
        if (args == null) return null
        return args.firstOrNull { candidate ->
            candidate != null &&
                hasField(candidate, "mDeleteWhere") &&
                hasField(candidate, "mDeleteWhereArgs")
        }
    }

    private fun hasField(instance: Any, fieldName: String): Boolean {
        var current: Class<*>? = instance.javaClass
        while (current != null) {
            if (current.declaredFields.any { it.name == fieldName }) {
                return true
            }
            current = current.superclass
        }
        return false
    }

    private fun shouldSkipDispatchChainBlock(
        smsMsg: SmsMsg?,
        action: String?,
        reason: String,
    ): Boolean {
        if (smsMsg == null || action.isNullOrBlank()) return false
        val now = System.currentTimeMillis()
        val key = buildString {
            append(senderHash(smsMsg.sender))
            append('|')
            append(smsMsg.body.orEmpty().hashCode())
            append('|')
            append(smsMsg.date)
            append('|')
            append(action)
            append('|')
            append(reason)
        }
        synchronized(DISPATCH_CHAIN_BLOCK_LOCK) {
            val iterator = dispatchChainBlockHistory.entries.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                if (now - entry.value > DISPATCH_CHAIN_BLOCK_WINDOW_MS) {
                    iterator.remove()
                }
            }
            val last = dispatchChainBlockHistory[key]
            if (last != null && now - last <= DISPATCH_CHAIN_BLOCK_WINDOW_MS) {
                XLog.w(
                    "Diag dispatch chain block duplicate skip: action=%s reason=%s ageMs=%d",
                    action,
                    reason,
                    now - last,
                )
                return true
            }
            dispatchChainBlockHistory[key] = now
            return false
        }
    }

    private fun defaultResultForType(type: Class<*>?): Any? {
        return when (type) {
            null, Void.TYPE, Void::class.java -> null
            Boolean::class.javaPrimitiveType, Boolean::class.javaObjectType -> false
            Int::class.javaPrimitiveType, Int::class.javaObjectType -> 0
            Long::class.javaPrimitiveType, Long::class.javaObjectType -> 0L
            Float::class.javaPrimitiveType, Float::class.javaObjectType -> 0f
            Double::class.javaPrimitiveType, Double::class.javaObjectType -> 0.0
            Short::class.javaPrimitiveType, Short::class.javaObjectType -> 0.toShort()
            Byte::class.javaPrimitiveType, Byte::class.javaObjectType -> 0.toByte()
            Char::class.javaPrimitiveType, Char::class.javaObjectType -> 0.toChar()
            else -> null
        }
    }

    private fun logSuppressedOnce(stage: String) {
        if (suppressionLogged) return
        synchronized(this) {
            if (suppressionLogged) return
            XLog.w(
                "SmsHandlerHook suppressed: reason=%s stage=%s package=%s",
                ModuleConflictArbiter.SUPPRESSION_REASON,
                stage,
                ModuleConflictArbiter.TARGET_RELAY_PACKAGE,
            )
            suppressionLogged = true
        }
    }

    private fun getPluginContext(): Context? {
        if (mPluginContext == null) {
            try {
                mPluginContext = mPhoneContext?.createPackageContext(
                    SMSCODE_PACKAGE,
                    Context.CONTEXT_IGNORE_SECURITY,
                )
            } catch (e: Exception) {
                XLog.e("Create plugin context failed: %s", e)
            }
        }
        return mPluginContext
    }

    private fun isVerboseDiagEnabled(context: Context?): Boolean {
        if (context == null) return false
        return runCatching { PrefsReader.isVerboseLogMode(context) }.getOrDefault(false)
    }

    companion object {
        const val ANDROID_PHONE_PACKAGE = "com.android.phone"
        private const val TELEPHONY_PACKAGE = "com.android.internal.telephony"
        private const val SMS_HANDLER_CLASS = "$TELEPHONY_PACKAGE.InboundSmsHandler"
        private val SMSCODE_PACKAGE = BuildConfig.APPLICATION_ID
        private const val EVENT_BROADCAST_COMPLETE = 3
        private const val DISPATCH_DEDUP_FILE_NAME = "dispatch_dedup"
        private const val DISPATCH_DEDUP_WINDOW_MS = 8_000L
        private const val MAX_DISPATCH_DEDUP_ENTRIES = 256
        private const val DISPATCH_CHAIN_BLOCK_WINDOW_MS = 8_000L
        private const val PERSISTENT_DEVICE_ID_DEFAULT = "default:0"
        private val SMS_OPERATION_EXECUTOR = Executors.newSingleThreadExecutor()
        private val installedHookKeys = Collections.synchronizedSet(mutableSetOf<String>())
        private val dispatchChainBlockHistory = LinkedHashMap<String, Long>()
        @Volatile
        private var cachedDeleteRawMethod: Method? = null
        @Volatile
        private var cachedSendMessageMethod: Method? = null
        @Volatile
        private var loggedDeleteRawSignatures = false
        @Volatile
        private var loggedSendMessageSignatures = false

        private fun buildHookInstallKey(lpparam: LoadParam): String {
            return buildString {
                append(lpparam.packageName)
                append('|')
                append(lpparam.processName.ifBlank { lpparam.packageName })
            }
        }

        private fun buildSharedProcessKey(
            prefix: String,
            packageName: String,
            processName: String,
        ): String {
            return buildString {
                append(prefix)
                append('|')
                append(packageName)
                append('|')
                append(processName.ifBlank { packageName })
                append("|pid:")
                append(android.os.Process.myPid())
            }
        }

        private fun claimProcessPropertyWithinWindow(
            key: String,
            windowMs: Long,
        ): Long? = synchronized(PROCESS_PROPERTY_LOCK) {
            val now = System.currentTimeMillis()
            val raw = System.getProperty(key)
            val last = raw?.toLongOrNull()
            if (last != null && now - last <= windowMs) {
                return@synchronized now - last
            }
            System.setProperty(key, now.toString())
            null
        }

        private fun markHookInstalled(key: String): Boolean = installedHookKeys.add(key)

        private const val SHARED_HOOK_INIT_WINDOW_MS = 5 * 60 * 1000L
        private const val SHARED_OBSERVER_WINDOW_MS = 5 * 60 * 1000L
        private val PROCESS_PROPERTY_LOCK = Any()
        private val DISPATCH_CHAIN_BLOCK_LOCK = Any()
    }
}

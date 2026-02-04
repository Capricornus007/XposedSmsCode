package com.tianma.xsmscode.xp.hook.code

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.provider.Telephony
import com.github.tianma8023.xposed.smscode.BuildConfig
import com.github.tianma8023.xposed.smscode.R
import com.tianma.xsmscode.common.constant.NotificationConst
import com.tianma.xsmscode.common.utils.ModuleActivationStore
import com.tianma.xsmscode.common.utils.NotificationUtils
import com.tianma.xsmscode.common.utils.PrefsReader
import com.tianma.xsmscode.common.utils.XLog
import com.tianma.xsmscode.xp.helper.XposedWrapper
import com.tianma.xsmscode.xp.hook.BaseHook
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

/**
 * Hook class com.android.internal.telephony.InboundSmsHandler
 */
class SmsHandlerHook : BaseHook() {

    private var mPhoneContext: Context? = null
    private var mPluginContext: Context? = null

    override fun onLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (ANDROID_PHONE_PACKAGE == lpparam.packageName) {
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
        @Suppress("DEPRECATION")
        val xposedVersion = try {
            XposedBridge.getXposedVersion()
        } catch (ignored: Throwable) {
            XposedBridge.XPOSED_BRIDGE_VERSION
        }
        XLog.i("Xposed bridge version: %d", xposedVersion)
        XLog.i("SmsCode version: %s (%d)", BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)
    }

    private fun hookSmsHandler(classloader: ClassLoader) {
        hookConstructor(classloader)
        hookDispatchIntent(classloader)
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
            XposedBridge.hookAllConstructors(smsHandlerClazz, ConstructorHook())
        }
    }

    private fun hookDispatchIntent(classloader: ClassLoader) {
        // minSdkVersion 35: Only hook for Android 10+ / 15+
        hookDispatchIntent29(classloader)
    }

    // Android 10+
    private fun hookDispatchIntent29(classLoader: ClassLoader) {
        XLog.d("Hooking dispatchIntent() for Android v29+")
        val inboundSmsHandlerClass = XposedWrapper.findClass(SMS_HANDLER_CLASS, classLoader) ?: run {
            XLog.e("Class: %s cannot found", SMS_HANDLER_CLASS)
            return
        }

        val methods = inboundSmsHandlerClass.declaredMethods
        var exactMethod: Method? = null
        val dispatchIntentMethodName = "dispatchIntent"
        var receiverIndex = 0
        for (method in methods) {
            if (dispatchIntentMethodName == method.name) {
                exactMethod = method
                val parameterTypes = method.parameterTypes
                for (i in parameterTypes.indices) {
                    if (BroadcastReceiver::class.java.isAssignableFrom(parameterTypes[i])) {
                        receiverIndex = i
                    }
                }
                break
            }
        }

        exactMethod?.let {
            XposedWrapper.hookMethod(it, DispatchIntentHook(receiverIndex))
        } ?: run {
            XLog.e("Method %s for Class %s cannot found", dispatchIntentMethodName, SMS_HANDLER_CLASS)
        }
    }

    private inner class ConstructorHook : XC_MethodHook() {
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

    private fun afterConstructorHandler(param: XC_MethodHook.MethodHookParam) {
        val context = param.args.getOrNull(1) as? Context ?: return
        if (mPhoneContext == null) {
            mPhoneContext = context
            try {
                mPluginContext = mPhoneContext?.createPackageContext(
                    SMSCODE_PACKAGE,
                    Context.CONTEXT_IGNORE_SECURITY,
                )
                if (mPluginContext != null) {
                    initNotificationChannel()
                    registerCopyCodeReceiver()
                    mPluginContext?.let { ModuleActivationStore.markActivated(it) }
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

    private inner class DispatchIntentHook(private val mReceiverIndex: Int) : XC_MethodHook() {
        @Throws(Throwable::class)
        override fun beforeHookedMethod(param: MethodHookParam) {
            try {
                beforeDispatchIntentHandler(param, mReceiverIndex)
            } catch (e: Throwable) {
                XLog.e("Error occurred in dispatchIntent() hook, ", e)
                throw e
            }
        }
    }

    private fun beforeDispatchIntentHandler(param: XC_MethodHook.MethodHookParam, receiverIndex: Int) {
        val intent = param.args.getOrNull(0) as? Intent ?: return
        val action = intent.action

        if (BuildConfig.DEBUG) {
            XLog.d("SmsHandlerHook: Received intent action: $action")
            intent.extras?.let { bundle ->
                for (key in bundle.keySet()) {
                    @Suppress("DEPRECATION")
                    XLog.d("SmsHandlerHook: Extra[$key] = ${bundle.get(key)}")
                }
            }
        }

        if (Telephony.Sms.Intents.SMS_DELIVER_ACTION != action) {
            return
        }

        val pluginContext = getPluginContext()
        val phoneContext = mPhoneContext
        if (pluginContext == null || phoneContext == null) {
            XLog.e("Context is null, skip parsing. pluginContext: %s, phoneContext: %s", pluginContext, phoneContext)
            return
        }

        val parseResult = CodeWorker(pluginContext, phoneContext, intent).parse()
        if (parseResult != null) {
            if (parseResult.isBlockSms) {
                XLog.d("Blocking code SMS...")
                param.args.getOrNull(receiverIndex)?.let { receiver ->
                    deleteRawTableAndSendMessage(param.thisObject, receiver)
                    param.result = null
                }
            }
        }
    }

    private fun deleteRawTableAndSendMessage(inboundSmsHandler: Any, smsReceiver: Any) {
        val token = Binder.clearCallingIdentity()
        try {
            deleteFromRawTable(inboundSmsHandler, smsReceiver)
        } catch (e: Throwable) {
            XLog.e("Error occurs when delete SMS data from raw table", e)
        } finally {
            Binder.restoreCallingIdentity(token)
        }

        sendEventBroadcastComplete(inboundSmsHandler)
    }

    private fun sendEventBroadcastComplete(inboundSmsHandler: Any) {
        XLog.d("Send event(EVENT_BROADCAST_COMPLETE)")
        XposedHelpers.callMethod(inboundSmsHandler, "sendMessage", EVENT_BROADCAST_COMPLETE)
    }

    @Throws(ReflectiveOperationException::class)
    private fun deleteFromRawTable(inboundSmsHandler: Any, smsReceiver: Any) {
        // minSdkVersion 35: Always use Android 24+ method
        deleteFromRawTable24(inboundSmsHandler, smsReceiver)
    }

    @Throws(ReflectiveOperationException::class)
    private fun deleteFromRawTable24(inboundSmsHandler: Any, smsReceiver: Any) {
        XLog.d("Delete raw SMS data from database on Android 24+")
        val deleteWhere = XposedHelpers.getObjectField(smsReceiver, "mDeleteWhere")
        val deleteWhereArgs = XposedHelpers.getObjectField(smsReceiver, "mDeleteWhereArgs")
        val markDeleted = 2

        callDeclaredMethod(
            SMS_HANDLER_CLASS,
            inboundSmsHandler,
            "deleteFromRawTable",
            deleteWhere,
            deleteWhereArgs,
            markDeleted,
        )
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

    companion object {
        const val ANDROID_PHONE_PACKAGE = "com.android.phone"
        private const val TELEPHONY_PACKAGE = "com.android.internal.telephony"
        private const val SMS_HANDLER_CLASS = "$TELEPHONY_PACKAGE.InboundSmsHandler"
        private val SMSCODE_PACKAGE = BuildConfig.APPLICATION_ID
        private const val EVENT_BROADCAST_COMPLETE = 3

        @Throws(InvocationTargetException::class, IllegalAccessException::class)
        private fun callDeclaredMethod(className: String, obj: Any, methodName: String, vararg args: Any?): Any? {
            val clz = XposedHelpers.findClass(className, obj.javaClass.classLoader)
            val method = XposedHelpers.findMethodBestMatch(clz, methodName, *args)
            return method.invoke(obj, *args)
        }
    }
}

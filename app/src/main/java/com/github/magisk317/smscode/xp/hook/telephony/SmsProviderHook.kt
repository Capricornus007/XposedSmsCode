package com.github.magisk317.smscode.xp.hook.telephony

import android.content.ContentProvider
import android.content.ContentValues
import android.net.Uri
import android.os.Binder
import com.github.magisk317.smscode.common.utils.XLog
import com.github.magisk317.smscode.xp.hook.BaseHook
import com.github.magisk317.smscode.xp.helper.XposedWrapper
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * Log SMS provider writes to identify who inserts/updates SMS rows.
 */
class SmsProviderHook : BaseHook() {

    override fun hookOnLoadPackage(): Boolean = true

    override fun onLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != TELEPHONY_PROVIDER_PACKAGE) return
        hookProviderMethods(lpparam.classLoader)
    }

    private fun hookProviderMethods(classLoader: ClassLoader) {
        val providerClass = XposedWrapper.findClass(TELEPHONY_PROVIDER_CLASS, classLoader) ?: run {
            XLog.w("SmsProviderHook: class not found: %s", TELEPHONY_PROVIDER_CLASS)
            return
        }

        hookMethod(providerClass, "insert")
        hookMethod(providerClass, "bulkInsert")
        hookMethod(providerClass, "update")
    }

    private fun hookMethod(clazz: Class<*>, methodName: String) {
        val methods = clazz.declaredMethods.filter { it.name == methodName }
        if (methods.isEmpty()) return
        methods.forEach { method ->
            XposedWrapper.hookMethod(
                method,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val uri = param.args.getOrNull(0) as? Uri ?: return
                        if (!isSmsUri(uri)) return
                        val provider = param.thisObject as? ContentProvider
                        val context = provider?.context
                        val callingUid = Binder.getCallingUid()
                        val callingPid = Binder.getCallingPid()
                        val packages = runCatching {
                            context?.packageManager?.getPackagesForUid(callingUid)?.toList()
                        }.getOrNull().orEmpty()
                        val valuesSummary = when (methodName) {
                            "insert", "update" -> {
                                val values = param.args.getOrNull(1) as? ContentValues
                                values?.keySet()?.joinToString(",") ?: "<none>"
                            }
                            "bulkInsert" -> {
                                val values = param.args.getOrNull(1) as? Array<*>
                                "count=${values?.size ?: 0}"
                            }
                            else -> "<none>"
                        }
                        XLog.w(
                            "Diag sms provider %s: uri=%s uid=%d pid=%d pkgs=%s values=%s",
                            methodName,
                            uri,
                            callingUid,
                            callingPid,
                            if (packages.isEmpty()) "<none>" else packages.joinToString(","),
                            valuesSummary,
                        )
                    }
                },
            )
        }
    }

    private fun isSmsUri(uri: Uri): Boolean {
        val authority = uri.authority.orEmpty()
        if (authority == "sms" || authority == "mms-sms" || authority == "com.android.providers.telephony") {
            return true
        }
        val path = uri.toString()
        return path.contains("content://sms") || path.contains("content://mms-sms")
    }

    companion object {
        private const val TELEPHONY_PROVIDER_PACKAGE = "com.android.providers.telephony"
        private const val TELEPHONY_PROVIDER_CLASS = "com.android.providers.telephony.TelephonyProvider"
    }
}

package com.github.magisk317.smscode.common.utils

import android.content.Context
import android.content.SharedPreferences
import com.github.magisk317.smscode.common.constant.CodeNotificationOwner
import com.github.magisk317.smscode.common.constant.PrefConst
import com.github.magisk317.smscode.data.db.entity.SmsMsg
import com.github.magisk317.smscode.runtime.BuildConfig
import com.github.magisk317.smscode.common.utils.XLog
import java.util.Collections

object PrefsReader {
    private const val PREFS_NAME = "xposed_prefs"
    @Volatile
    private var remotePrefsProvider: (() -> SharedPreferences?)? = null
    @Volatile
    private var remoteProviderLogged = false
    private val remoteTraceLoggedKeys = Collections.synchronizedSet(mutableSetOf<String>())
    private data class BooleanReadTrace(val value: Boolean, val source: String)
    private data class StringReadTrace(val value: String, val source: String)
    @JvmStatic
    fun setRemotePrefsProvider(provider: (() -> SharedPreferences?)?) {
        remotePrefsProvider = provider
        remoteProviderLogged = false
    }

    private fun getRemotePrefs(): SharedPreferences? {
        val provider = remotePrefsProvider ?: return null
        return runCatching { provider.invoke() }.getOrElse { t ->
            if (!remoteProviderLogged) {
                remoteProviderLogged = true
                XLog.w("PrefsReader: remote prefs provider failed", t)
            }
            null
        }
    }

    private fun useRemoteOnlyChain(): Boolean = BuildConfig.XPOSED_API_FLAVOR == "api101"

    private fun logRemoteTraceOnce(key: String, state: String) {
        if (remoteTraceLoggedKeys.add(key)) {
            XLog.w("Diag remote prefs: key=%s state=%s", key, state)
        }
    }

    private fun getBooleanViaRemote(key: String, defaultValue: Boolean): Boolean? {
        val prefs = getRemotePrefs() ?: return null
        return try {
            if (!prefs.contains(key)) return null
            prefs.getBoolean(key, defaultValue)
        } catch (t: Throwable) {
            XLog.w("PrefsReader: remote prefs boolean '%s' failed, fallback provider", key, t)
            null
        }
    }

    private fun getStringViaRemote(key: String, defaultValue: String): String? {
        val prefs = getRemotePrefs() ?: return null
        return try {
            if (!prefs.contains(key)) return null
            prefs.getString(key, defaultValue) ?: defaultValue
        } catch (t: Throwable) {
            XLog.w("PrefsReader: remote prefs string '%s' failed, fallback provider", key, t)
            null
        }
    }

    private fun getIntViaRemote(key: String, defaultValue: Int): Int? {
        val prefs = getRemotePrefs() ?: return null
        return try {
            if (!prefs.contains(key)) return null
            when (val any = prefs.all[key]) {
                is Int -> any
                is Long -> any.toInt()
                is String -> any.toIntOrNull() ?: defaultValue
                else -> prefs.getInt(key, defaultValue)
            }
        } catch (t: Throwable) {
            XLog.w("PrefsReader: remote prefs int '%s' failed, fallback provider", key, t)
            null
        }
    }

    private fun getSharedPrefs(context: Context): SharedPreferences? {
        return runCatching {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }.getOrElse {
            // CE storage may be unavailable before user unlock; fallback to DP storage context.
            runCatching {
                context.createDeviceProtectedStorageContext()
                    .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            }.getOrNull()
        }
    }

    private fun getBooleanViaSharedPrefs(context: Context, key: String, defaultValue: Boolean): Boolean {
        return try {
            getSharedPrefs(context)?.getBoolean(key, defaultValue) ?: defaultValue
        } catch (t: Throwable) {
            XLog.w("PrefsReader: sharedPrefs boolean '%s' failed, default=%s", key, defaultValue, t)
            defaultValue
        }
    }

    private fun getStringViaSharedPrefs(context: Context, key: String, defaultValue: String): String {
        return try {
            getSharedPrefs(context)?.getString(key, defaultValue) ?: defaultValue
        } catch (t: Throwable) {
            XLog.w("PrefsReader: sharedPrefs string '%s' failed, default=%s", key, defaultValue, t)
            defaultValue
        }
    }

    private fun getIntViaSharedPrefs(context: Context, key: String, defaultValue: Int): Int {
        return try {
            when (val any = getSharedPrefs(context)?.all?.get(key)) {
                is Int -> any
                is Long -> any.toInt()
                is String -> any.toIntOrNull() ?: defaultValue
                else -> defaultValue
            }
        } catch (t: Throwable) {
            XLog.w("PrefsReader: sharedPrefs int '%s' failed, default=%d", key, defaultValue, t)
            defaultValue
        }
    }

    private fun getBooleanViaProvider(context: Context, key: String, defaultValue: Boolean): Boolean {
        getBooleanViaRemote(key, defaultValue)?.let { return it }
        if (useRemoteOnlyChain()) return defaultValue
        return try {
            val uri = com.github.magisk317.smscode.data.prefs.PrefsProvider.buildBoolUri(context).buildUpon()
                .appendQueryParameter("key", key)
                .appendQueryParameter("default", defaultValue.toString())
                .build()
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val value = cursor.getString(0)
                    return value == "1" || value.equals("true", ignoreCase = true)
                }
            }
            getBooleanViaSharedPrefs(context, key, defaultValue)
        } catch (t: Throwable) {
            XLog.w("PrefsReader: read boolean '%s' via provider failed, fallback sharedPrefs", key, t)
            getBooleanViaSharedPrefs(context, key, defaultValue)
        }
    }

    private fun readBooleanWithTrace(context: Context, key: String, defaultValue: Boolean): BooleanReadTrace {
        val remotePrefs = getRemotePrefs()
        val remoteState = when {
            remotePrefs == null -> "unavailable"
            remotePrefs.contains(key) -> "hit"
            else -> "miss"
        }
        logRemoteTraceOnce("bool:$key", remoteState)
        if (remotePrefs != null) {
            try {
                if (remotePrefs.contains(key)) {
                    return BooleanReadTrace(
                        value = remotePrefs.getBoolean(key, defaultValue),
                        source = "remote",
                    )
                }
            } catch (t: Throwable) {
                XLog.w("PrefsReader: remote prefs boolean '%s' failed, fallback provider", key, t)
            }
        }
        if (useRemoteOnlyChain()) {
            return BooleanReadTrace(
                value = defaultValue,
                source = "default",
            )
        }
        try {
            val uri = com.github.magisk317.smscode.data.prefs.PrefsProvider.buildBoolUri(context).buildUpon()
                .appendQueryParameter("key", key)
                .appendQueryParameter("default", defaultValue.toString())
                .build()
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val value = cursor.getString(0)
                    return BooleanReadTrace(
                        value = value == "1" || value.equals("true", ignoreCase = true),
                        source = "provider",
                    )
                }
            }
        } catch (t: Throwable) {
            XLog.w("PrefsReader: read boolean '%s' via provider failed, fallback sharedPrefs", key, t)
        }
        return try {
            val prefs = getSharedPrefs(context)
            if (prefs?.contains(key) == true) {
                BooleanReadTrace(
                    value = prefs.getBoolean(key, defaultValue),
                    source = "shared_prefs",
                )
            } else {
                BooleanReadTrace(
                    value = defaultValue,
                    source = "default",
                )
            }
        } catch (t: Throwable) {
            XLog.w("PrefsReader: sharedPrefs boolean '%s' failed, default=%s", key, defaultValue, t)
            BooleanReadTrace(
                value = defaultValue,
                source = "default",
            )
        }
    }

    private fun getStringViaProvider(context: Context, key: String, defaultValue: String): String {
        getStringViaRemote(key, defaultValue)?.let { return it }
        if (useRemoteOnlyChain()) return defaultValue
        return try {
            val uri = com.github.magisk317.smscode.data.prefs.PrefsProvider.buildStringUri(context).buildUpon()
                .appendQueryParameter("key", key)
                .appendQueryParameter("default", defaultValue)
                .build()
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    return cursor.getString(0) ?: defaultValue
                }
            }
            getStringViaSharedPrefs(context, key, defaultValue)
        } catch (t: Throwable) {
            XLog.w("PrefsReader: read string '%s' via provider failed, fallback sharedPrefs", key, t)
            getStringViaSharedPrefs(context, key, defaultValue)
        }
    }

    private fun readStringWithTrace(context: Context, key: String, defaultValue: String): StringReadTrace {
        val remotePrefs = getRemotePrefs()
        val remoteState = when {
            remotePrefs == null -> "unavailable"
            remotePrefs.contains(key) -> "hit"
            else -> "miss"
        }
        logRemoteTraceOnce("string:$key", remoteState)
        if (remotePrefs != null) {
            try {
                if (remotePrefs.contains(key)) {
                    return StringReadTrace(
                        value = remotePrefs.getString(key, defaultValue) ?: defaultValue,
                        source = "remote",
                    )
                }
            } catch (t: Throwable) {
                XLog.w("PrefsReader: remote prefs string '%s' failed, fallback provider", key, t)
            }
        }
        if (useRemoteOnlyChain()) {
            return StringReadTrace(
                value = defaultValue,
                source = "default",
            )
        }
        try {
            val uri = com.github.magisk317.smscode.data.prefs.PrefsProvider.buildStringUri(context).buildUpon()
                .appendQueryParameter("key", key)
                .appendQueryParameter("default", defaultValue)
                .build()
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    return StringReadTrace(
                        value = cursor.getString(0) ?: defaultValue,
                        source = "provider",
                    )
                }
            }
        } catch (t: Throwable) {
            XLog.w("PrefsReader: read string '%s' via provider failed, fallback sharedPrefs", key, t)
        }
        return try {
            val prefs = getSharedPrefs(context)
            if (prefs?.contains(key) == true) {
                StringReadTrace(
                    value = prefs.getString(key, defaultValue) ?: defaultValue,
                    source = "shared_prefs",
                )
            } else {
                StringReadTrace(
                    value = defaultValue,
                    source = "default",
                )
            }
        } catch (t: Throwable) {
            XLog.w("PrefsReader: sharedPrefs string '%s' failed, default=%s", key, defaultValue, t)
            StringReadTrace(
                value = defaultValue,
                source = "default",
            )
        }
    }

    private fun getIntViaProvider(context: Context, key: String, defaultValue: Int): Int {
        getIntViaRemote(key, defaultValue)?.let { return it }
        if (useRemoteOnlyChain()) return defaultValue
        return try {
            val uri = com.github.magisk317.smscode.data.prefs.PrefsProvider.buildIntUri(context).buildUpon()
                .appendQueryParameter("key", key)
                .appendQueryParameter("default", defaultValue.toString())
                .build()
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    return cursor.getString(0)?.toIntOrNull() ?: defaultValue
                }
            }
            getIntViaSharedPrefs(context, key, defaultValue)
        } catch (t: Throwable) {
            XLog.w("PrefsReader: read int '%s' via provider failed, fallback sharedPrefs", key, t)
            getIntViaSharedPrefs(context, key, defaultValue)
        }
    }

    @JvmStatic
    fun isEnabled(context: Context): Boolean {
        val defaultValue = true
        return getBooleanViaProvider(context, PrefConst.KEY_ENABLE, defaultValue)
    }

    @JvmStatic
    fun isVerboseLogMode(context: Context): Boolean {
        val defaultValue = false
        return getBooleanViaProvider(context, PrefConst.KEY_VERBOSE_LOG_MODE, defaultValue)
    }

    @JvmStatic
    fun isSensitiveDebugLogSupported(): Boolean = BuildConfig.DEBUG

    @JvmStatic
    fun isSensitiveDebugLogMode(context: Context): Boolean {
        if (!isSensitiveDebugLogSupported()) return false
        return getBooleanViaProvider(context, PrefConst.KEY_SENSITIVE_DEBUG_LOG_MODE, false)
    }

    @JvmStatic
    fun autoInputCodeEnabled(context: Context): Boolean {
        val defaultValue = true
        return getBooleanViaProvider(context, PrefConst.KEY_ENABLE_AUTO_INPUT_CODE, defaultValue)
    }

    @JvmStatic
    fun autoEnterCodeEnabled(context: Context): Boolean {
        val defaultValue = false
        return getBooleanViaProvider(context, PrefConst.KEY_ENABLE_AUTO_ENTER_CODE, defaultValue)
    }

    @JvmStatic
    fun getAutoInputCodeDelay(context: Context): Long {
        val value = getStringViaProvider(
            context,
            PrefConst.KEY_AUTO_INPUT_CODE_DELAY,
            PrefConst.KEY_AUTO_INPUT_CODE_DELAY_DEFAULT,
        )
        return try {
            value.toLong()
        } catch (ignored: Exception) {
            PrefConst.KEY_AUTO_INPUT_CODE_DELAY_DEFAULT.toLong()
        }
    }

    @JvmStatic
    fun getAutoInputCodeIntervalMs(context: Context): Long {
        val value = getStringViaProvider(
            context,
            PrefConst.KEY_AUTO_INPUT_CODE_INTERVAL,
            PrefConst.KEY_AUTO_INPUT_CODE_INTERVAL_DEFAULT,
        )
        return try {
            value.toLong().coerceAtLeast(0L)
        } catch (ignored: Exception) {
            PrefConst.KEY_AUTO_INPUT_CODE_INTERVAL_DEFAULT.toLong()
        }
    }

    @JvmStatic
    fun shouldShowToast(context: Context): Boolean {
        val defaultValue = true
        return getBooleanViaProvider(context, PrefConst.KEY_SHOW_TOAST, defaultValue)
    }

    @JvmStatic
    fun getSMSCodeKeywords(context: Context): String? = getStringViaProvider(
        context,
        PrefConst.KEY_SMSCODE_KEYWORDS,
        PrefConst.SMSCODE_KEYWORDS_DEFAULT,
    )

    @JvmStatic
    fun markAsReadEnabled(context: Context): Boolean {
        val defaultValue = false
        return getBooleanViaProvider(context, PrefConst.KEY_MARK_AS_READ, defaultValue)
    }

    @JvmStatic
    fun deleteSmsEnabled(context: Context): Boolean {
        val defaultValue = false
        return getBooleanViaProvider(context, PrefConst.KEY_DELETE_SMS, defaultValue)
    }

    @JvmStatic
    fun copyToClipboardEnabled(context: Context): Boolean {
        val defaultValue = false
        val trace = readBooleanWithTrace(context, PrefConst.KEY_COPY_TO_CLIPBOARD, defaultValue)
        XLog.w(
            "Diag pref copy_to_clipboard: value=%s source=%s default=%s",
            trace.value,
            trace.source,
            defaultValue,
        )
        return trace.value
    }

    @JvmStatic
    fun recordSmsCodeEnabled(context: Context): Boolean {
        return recordCodeSmsEnabled(context)
    }

    @JvmStatic
    fun recordCodeSmsEnabled(context: Context): Boolean {
        return getBooleanViaProvider(context, PrefConst.KEY_ENABLE_CODE_RECORDS_CODE, true)
    }

    @JvmStatic
    fun recordPlainSmsEnabled(context: Context): Boolean {
        return getBooleanViaProvider(context, PrefConst.KEY_ENABLE_CODE_RECORDS_PLAIN_SMS, true)
    }

    @JvmStatic
    fun recordAppNotifyEnabled(context: Context): Boolean {
        return getBooleanViaProvider(context, PrefConst.KEY_ENABLE_CODE_RECORDS_APP_NOTIFY, true)
    }

    @JvmStatic
    fun recordCallNotifyEnabled(context: Context): Boolean {
        return getBooleanViaProvider(context, PrefConst.KEY_ENABLE_CODE_RECORDS_CALL_NOTIFY, true)
    }

    @JvmStatic
    fun blockSmsEnabled(context: Context): Boolean {
        val defaultValue = false
        return getBooleanViaProvider(context, PrefConst.KEY_BLOCK_SMS, defaultValue)
    }

    @JvmStatic
    fun killMeEnabled(context: Context): Boolean {
        val defaultValue = false
        return getBooleanViaProvider(context, PrefConst.KEY_KILL_ME, defaultValue)
    }

    @JvmStatic
    fun showCodeNotification(context: Context): Boolean {
        val defaultValue = true
        return getBooleanViaProvider(context, PrefConst.KEY_SHOW_CODE_NOTIFICATION, defaultValue)
    }

    @JvmStatic
    fun getCodeNotificationOwner(context: Context): String {
        val value = getStringViaProvider(context, PrefConst.KEY_CODE_NOTIFICATION_OWNER, "")
        return CodeNotificationOwner.normalize(value)
    }

    @JvmStatic
    fun autoCancelCodeNotification(context: Context): Boolean {
        val defaultValue = false
        return getBooleanViaProvider(context, PrefConst.KEY_AUTO_CANCEL_CODE_NOTIFICATION, defaultValue)
    }

    @JvmStatic
    fun getNotificationRetentionTime(context: Context): Int {
        val value = getStringViaProvider(
            context,
            PrefConst.KEY_NOTIFICATION_RETENTION_TIME,
            PrefConst.NOTIFICATION_RETENTION_TIME_DEFAULT,
        )
        return try {
            value.toInt()
        } catch (ignored: Exception) {
            0
        }
    }

    @JvmStatic
    fun deduplicateSms(context: Context): Boolean {
        val defaultValue = true
        return getBooleanViaProvider(context, PrefConst.KEY_DEDUPLICATE_SMS, defaultValue)
    }

    @JvmStatic
    fun smsBlacklistEnabled(context: Context): Boolean {
        val defaultValue = false
        return getBooleanViaProvider(context, PrefConst.KEY_ENABLE_SMS_BLACKLIST, defaultValue)
    }

    @JvmStatic
    fun smsBlacklistNumbers(context: Context): String =
        getStringViaProvider(context, PrefConst.KEY_SMS_BLACKLIST_NUMBERS, "")

    @JvmStatic
    fun smsBlacklistPrefixes(context: Context): String =
        getStringViaProvider(context, PrefConst.KEY_SMS_BLACKLIST_PREFIXES, "")

    @JvmStatic
    fun smsBlacklistRegex(context: Context): String =
        getStringViaProvider(context, PrefConst.KEY_SMS_BLACKLIST_REGEX, "")

    @JvmStatic
    fun smsBlacklistContent(context: Context): String =
        getStringViaProvider(context, PrefConst.KEY_SMS_BLACKLIST_CONTENT, "")

    @JvmStatic
    fun smsBlacklistActionDelete(context: Context): Boolean {
        val defaultValue = true
        return getBooleanViaProvider(context, PrefConst.KEY_SMS_BLACKLIST_ACTION_DELETE, defaultValue)
    }

    @JvmStatic
    fun smsBlacklistActionBlock(context: Context): Boolean {
        val defaultValue = false
        return getBooleanViaProvider(context, PrefConst.KEY_SMS_BLACKLIST_ACTION_BLOCK, defaultValue)
    }

    @JvmStatic
    fun getHistoryLimit(context: Context): Int {
        return getCodeHistoryLimit(context)
    }

    @JvmStatic
    fun getCodeHistoryLimit(context: Context): Int {
        return getHistoryLimitByKey(
            context = context,
            key = PrefConst.KEY_HISTORY_LIMIT_CODE,
        )
    }

    @JvmStatic
    fun getPlainSmsHistoryLimit(context: Context): Int {
        return getHistoryLimitByKey(
            context = context,
            key = PrefConst.KEY_HISTORY_LIMIT_PLAIN_SMS,
        )
    }

    @JvmStatic
    fun getAppNotifyHistoryLimit(context: Context): Int {
        return getHistoryLimitByKey(
            context = context,
            key = PrefConst.KEY_HISTORY_LIMIT_APP_NOTIFY,
        )
    }

    @JvmStatic
    fun getCallNotifyHistoryLimit(context: Context): Int {
        return getHistoryLimitByKey(
            context = context,
            key = PrefConst.KEY_HISTORY_LIMIT_CALL_NOTIFY,
        )
    }

    @JvmStatic
    fun getHistoryLimit(context: Context, msgType: Int, isCodeSms: Boolean): Int {
        return when (msgType) {
            SmsMsg.MSG_TYPE_APP_NOTIFY -> getAppNotifyHistoryLimit(context)
            SmsMsg.MSG_TYPE_CALL_NOTIFY -> getCallNotifyHistoryLimit(context)
            SmsMsg.MSG_TYPE_SMS -> if (isCodeSms) getCodeHistoryLimit(context) else getPlainSmsHistoryLimit(context)
            else -> getCodeHistoryLimit(context)
        }
    }

    private fun getHistoryLimitByKey(context: Context, key: String): Int {
        val value = getStringViaProvider(context, key, "0")
        return try {
            value.toInt()
        } catch (ignored: Exception) {
            0
        }
    }

    @JvmStatic
    fun getIpcToken(context: Context): String {
        val trace = readStringWithTrace(context, PrefConst.KEY_IPC_TOKEN, "")
        if (trace.value.isBlank() || trace.source != "provider") {
            XLog.w(
                "Diag pref ipc_token: blank=%s source=%s",
                trace.value.isBlank(),
                trace.source,
            )
        }
        return trace.value
    }

    @JvmStatic
    fun getSimSlotRemark(context: Context, simSlot: Int): String {
        val key = when (simSlot) {
            0 -> PrefConst.KEY_SIM_SLOT1_REMARK
            1 -> PrefConst.KEY_SIM_SLOT2_REMARK
            else -> return ""
        }
        return getStringViaProvider(context, key, "").trim()
    }
}

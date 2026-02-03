package com.tianma.xsmscode.common.utils

import android.content.Context
import com.tianma.xsmscode.common.constant.PrefConst

object PrefsReader {
    private fun getBooleanViaProvider(context: Context, key: String, defaultValue: Boolean): Boolean {
        return try {
            val uri = com.tianma.xsmscode.data.prefs.PrefsProvider.BOOL_URI.buildUpon()
                .appendQueryParameter("key", key)
                .appendQueryParameter("default", defaultValue.toString())
                .build()
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val value = cursor.getString(0)
                    return value == "1" || value.equals("true", ignoreCase = true)
                }
            }
            defaultValue
        } catch (t: Throwable) {
            XLog.w("PrefsReader: read boolean '%s' failed, default=%s", key, defaultValue, t)
            defaultValue
        }
    }

    private fun getStringViaProvider(context: Context, key: String, defaultValue: String): String {
        return try {
            val uri = com.tianma.xsmscode.data.prefs.PrefsProvider.STRING_URI.buildUpon()
                .appendQueryParameter("key", key)
                .appendQueryParameter("default", defaultValue)
                .build()
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    return cursor.getString(0) ?: defaultValue
                }
            }
            defaultValue
        } catch (t: Throwable) {
            XLog.w("PrefsReader: read string '%s' failed, default=%s", key, defaultValue, t)
            defaultValue
        }
    }

    private fun getIntViaProvider(context: Context, key: String, defaultValue: Int): Int {
        return try {
            val uri = com.tianma.xsmscode.data.prefs.PrefsProvider.INT_URI.buildUpon()
                .appendQueryParameter("key", key)
                .appendQueryParameter("default", defaultValue.toString())
                .build()
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    return cursor.getString(0)?.toIntOrNull() ?: defaultValue
                }
            }
            defaultValue
        } catch (t: Throwable) {
            XLog.w("PrefsReader: read int '%s' failed, default=%d", key, defaultValue, t)
            defaultValue
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
    fun autoInputCodeEnabled(context: Context): Boolean {
        val defaultValue = true
        return getBooleanViaProvider(context, PrefConst.KEY_ENABLE_AUTO_INPUT_CODE, defaultValue)
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
        } catch (e: Exception) {
            PrefConst.KEY_AUTO_INPUT_CODE_DELAY_DEFAULT.toLong()
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
        val defaultValue = true
        return getBooleanViaProvider(context, PrefConst.KEY_COPY_TO_CLIPBOARD, defaultValue)
    }

    @JvmStatic
    fun recordSmsCodeEnabled(context: Context): Boolean {
        val defaultValue = true
        return getBooleanViaProvider(context, PrefConst.KEY_ENABLE_CODE_RECORDS, defaultValue)
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
        } catch (e: Exception) {
            0
        }
    }

    @JvmStatic
    fun deduplicateSms(context: Context): Boolean {
        val defaultValue = true
        return getBooleanViaProvider(context, PrefConst.KEY_DEDUPLICATE_SMS, defaultValue)
    }

    @JvmStatic
    fun getHistoryLimit(context: Context): Int {
        val value = getStringViaProvider(
            context,
            PrefConst.KEY_HISTORY_LIMIT,
            "0",
        )
        return try {
            value.toInt()
        } catch (e: Exception) {
            0
        }
    }
}

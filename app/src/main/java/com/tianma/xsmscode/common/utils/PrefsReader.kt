package com.tianma.xsmscode.common.utils

import android.content.Context
import com.tianma.xsmscode.common.constant.PrefConst

import kotlinx.coroutines.runBlocking

object PrefsReader {

    @JvmStatic
    fun isEnabled(context: Context): Boolean {
        return runBlocking { AppPreferencesDataStore.getBoolean(context, PrefConst.KEY_ENABLE, true) }
    }

    @JvmStatic
    fun isVerboseLogMode(context: Context): Boolean {
        return runBlocking { AppPreferencesDataStore.getBoolean(context, PrefConst.KEY_VERBOSE_LOG_MODE, false) }
    }

    @JvmStatic
    fun autoInputCodeEnabled(context: Context): Boolean {
        return runBlocking { AppPreferencesDataStore.getBoolean(context, PrefConst.KEY_ENABLE_AUTO_INPUT_CODE, true) }
    }

    @JvmStatic
    fun getAutoInputCodeDelay(context: Context): Long {
        val value = runBlocking {
            AppPreferencesDataStore.getString(
                context,
                PrefConst.KEY_AUTO_INPUT_CODE_DELAY,
                PrefConst.KEY_AUTO_INPUT_CODE_DELAY_DEFAULT
            )
        }
        return try {
            value.toLong()
        } catch (e: Exception) {
            PrefConst.KEY_AUTO_INPUT_CODE_DELAY_DEFAULT.toLong()
        }
    }

    @JvmStatic
    fun shouldShowToast(context: Context): Boolean {
        return runBlocking { AppPreferencesDataStore.getBoolean(context, PrefConst.KEY_SHOW_TOAST, true) }
    }

    @JvmStatic
    fun getSMSCodeKeywords(context: Context): String? {
        return runBlocking {
            AppPreferencesDataStore.getString(
                context,
                PrefConst.KEY_SMSCODE_KEYWORDS,
                PrefConst.SMSCODE_KEYWORDS_DEFAULT
            )
        }
    }

    @JvmStatic
    fun markAsReadEnabled(context: Context): Boolean {
        return runBlocking { AppPreferencesDataStore.getBoolean(context, PrefConst.KEY_MARK_AS_READ, false) }
    }

    @JvmStatic
    fun deleteSmsEnabled(context: Context): Boolean {
        return runBlocking { AppPreferencesDataStore.getBoolean(context, PrefConst.KEY_DELETE_SMS, false) }
    }

    @JvmStatic
    fun copyToClipboardEnabled(context: Context): Boolean {
        return runBlocking { AppPreferencesDataStore.getBoolean(context, PrefConst.KEY_COPY_TO_CLIPBOARD, true) }
    }

    @JvmStatic
    fun recordSmsCodeEnabled(context: Context): Boolean {
        return runBlocking { AppPreferencesDataStore.getBoolean(context, PrefConst.KEY_ENABLE_CODE_RECORDS, true) }
    }

    @JvmStatic
    fun blockSmsEnabled(context: Context): Boolean {
        return runBlocking { AppPreferencesDataStore.getBoolean(context, PrefConst.KEY_BLOCK_SMS, false) }
    }

    @JvmStatic
    fun killMeEnabled(context: Context): Boolean {
        return runBlocking { AppPreferencesDataStore.getBoolean(context, PrefConst.KEY_KILL_ME, false) }
    }

    @JvmStatic
    fun showCodeNotification(context: Context): Boolean {
        return runBlocking { AppPreferencesDataStore.getBoolean(context, PrefConst.KEY_SHOW_CODE_NOTIFICATION, true) }
    }

    @JvmStatic
    fun autoCancelCodeNotification(context: Context): Boolean {
        return runBlocking { AppPreferencesDataStore.getBoolean(context, PrefConst.KEY_AUTO_CANCEL_CODE_NOTIFICATION, false) }
    }

    @JvmStatic
    fun getNotificationRetentionTime(context: Context): Int {
        val value = runBlocking {
            AppPreferencesDataStore.getString(
                context,
                PrefConst.KEY_NOTIFICATION_RETENTION_TIME,
                PrefConst.NOTIFICATION_RETENTION_TIME_DEFAULT
            )
        }
        return try {
            value.toInt()
        } catch (e: Exception) {
            0
        }
    }

    @JvmStatic
    fun deduplicateSms(context: Context): Boolean {
        return runBlocking { AppPreferencesDataStore.getBoolean(context, PrefConst.KEY_DEDUPLICATE_SMS, false) }
    }

    @JvmStatic
    fun getHistoryLimit(context: Context): Int {
        val value = runBlocking {
            AppPreferencesDataStore.getString(
                context,
                PrefConst.KEY_HISTORY_LIMIT,
                "0"
            )
        }
        return try {
            value.toInt()
        } catch (e: Exception) {
            0
        }
    }
}

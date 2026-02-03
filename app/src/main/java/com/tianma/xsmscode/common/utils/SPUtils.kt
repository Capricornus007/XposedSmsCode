package com.tianma.xsmscode.common.utils

import android.content.Context
import com.tianma.xsmscode.common.constant.PrefConst

object SPUtils {

    // 本地的版本号
    private const val LOCAL_VERSION_CODE = "local_version_code"
    private const val LOCAL_VERSION_CODE_DEFAULT = 16

    /**
     * 获取本地记录的版本号
     */
    suspend fun getLocalVersionCode(context: Context): Int {
        // 如果不存在,则默认返回16,即v1.4.5版本
        return AppPreferencesDataStore.getInt(context, LOCAL_VERSION_CODE, LOCAL_VERSION_CODE_DEFAULT)
    }

    /**
     * 设置当前版本号
     */
    suspend fun setLocalVersionCode(context: Context, versionCode: Int) {
        AppPreferencesDataStore.setInt(context, LOCAL_VERSION_CODE, versionCode)
    }

    /**
     * 获取短信验证码关键字
     */
    suspend fun getSMSCodeKeywords(context: Context): String? = AppPreferencesDataStore.getString(
        context,
        PrefConst.KEY_SMSCODE_KEYWORDS,
        PrefConst.SMSCODE_KEYWORDS_DEFAULT,
    )

    /**
     * 是否同意隐私协议
     */
    suspend fun isPrivacyPolicyAccepted(context: Context): Boolean =
        AppPreferencesDataStore.getBoolean(context, PrefConst.KEY_PRIVACY_POLICY_ACCEPTED, false)

    /**
     * 设置是否同意隐私协议
     */
    suspend fun setPrivacyPolicyAccepted(context: Context, accepted: Boolean) {
        AppPreferencesDataStore.setBoolean(context, PrefConst.KEY_PRIVACY_POLICY_ACCEPTED, accepted)
    }

    /**
     * 获取当前主题模式
     * 0: Follow System, 1: Light, 2: Dark
     */
    suspend fun getThemeMode(context: Context): Int =
        AppPreferencesDataStore.getInt(context, PrefConst.KEY_CHOOSE_THEME, 0)

    /**
     * 设置当前主题模式
     */
    suspend fun setThemeMode(context: Context, mode: Int) {
        AppPreferencesDataStore.setInt(context, PrefConst.KEY_CHOOSE_THEME, mode)
    }

    /**
     * 设置 FCM Token
     */
    @JvmStatic
    suspend fun setFcmToken(context: Context, token: String) {
        AppPreferencesDataStore.setString(context, PrefConst.KEY_FCM_TOKEN, token)
    }

    /**
     * 获取 FCM Token
     */
    @JvmStatic
    suspend fun getFcmToken(context: Context): String? {
        return AppPreferencesDataStore.getString(context, PrefConst.KEY_FCM_TOKEN, "")
            .takeIf { it.isNotEmpty() }
    }
    
    @JvmStatic
    suspend fun isFcmSyncEnabled(context: Context): Boolean {
        return AppPreferencesDataStore.getBoolean(context, PrefConst.KEY_FCM_ENABLE, false)
    }
    
    @JvmStatic
    suspend fun setFcmSyncEnabled(context: Context, enabled: Boolean) {
        AppPreferencesDataStore.setBoolean(context, PrefConst.KEY_FCM_ENABLE, enabled)
    }

    @JvmStatic
    suspend fun getFcmServerKey(context: Context): String? {
        return AppPreferencesDataStore.getString(context, PrefConst.KEY_FCM_SERVER_KEY, "")
    }

    @JvmStatic
    suspend fun setFcmServerKey(context: Context, key: String) {
        AppPreferencesDataStore.setString(context, PrefConst.KEY_FCM_SERVER_KEY, key)
    }


    @JvmStatic
    suspend fun getSyncGroupId(context: Context): String {
        return AppPreferencesDataStore.getString(context, PrefConst.KEY_SYNC_GROUP_ID, "")
    }

    @JvmStatic
    suspend fun setSyncGroupId(context: Context, groupId: String) {
        AppPreferencesDataStore.setString(context, PrefConst.KEY_SYNC_GROUP_ID, groupId)
    }

    suspend fun isCopyToClipboardEnabled(context: Context): Boolean =
        AppPreferencesDataStore.getBoolean(context, PrefConst.KEY_COPY_TO_CLIPBOARD, true)

    suspend fun isShowCodeNotificationEnabled(context: Context): Boolean =
        AppPreferencesDataStore.getBoolean(context, PrefConst.KEY_SHOW_CODE_NOTIFICATION, true)

    suspend fun isAutoCancelCodeNotificationEnabled(context: Context): Boolean =
        AppPreferencesDataStore.getBoolean(context, PrefConst.KEY_AUTO_CANCEL_CODE_NOTIFICATION, false)

    suspend fun getNotificationRetentionTime(context: Context): Int {
        val value = AppPreferencesDataStore.getString(
            context,
            PrefConst.KEY_NOTIFICATION_RETENTION_TIME,
            PrefConst.NOTIFICATION_RETENTION_TIME_DEFAULT
        )
        return try {
            value.toInt()
        } catch (e: Exception) {
            0
        }
    }
}

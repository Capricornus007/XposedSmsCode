package com.github.magisk317.smscode.forwarder.utils

import android.content.Context
import com.github.magisk317.smscode.forwarder.entity.MsgInfo
import com.github.magisk317.smscode.forwarder.entity.Sender
import com.github.magisk317.smscode.forwarder.entity.setting.BarkSetting
import com.github.magisk317.smscode.forwarder.entity.setting.DingtalkGroupRobotSetting
import com.github.magisk317.smscode.forwarder.entity.setting.DingtalkInnerRobotSetting
import com.github.magisk317.smscode.forwarder.entity.setting.EmailSetting
import com.github.magisk317.smscode.forwarder.entity.setting.FeishuAppSetting
import com.github.magisk317.smscode.forwarder.entity.setting.FeishuSetting
import com.github.magisk317.smscode.forwarder.entity.setting.GotifySetting
import com.github.magisk317.smscode.forwarder.entity.setting.PushplusSetting
import com.github.magisk317.smscode.forwarder.entity.setting.ServerchanSetting
import com.github.magisk317.smscode.forwarder.entity.setting.SmsSetting
import com.github.magisk317.smscode.forwarder.entity.setting.SocketSetting
import com.github.magisk317.smscode.forwarder.entity.setting.TelegramSetting
import com.github.magisk317.smscode.forwarder.entity.setting.UrlSchemeSetting
import com.github.magisk317.smscode.forwarder.entity.setting.WebhookSetting
import com.github.magisk317.smscode.forwarder.entity.setting.WeworkAgentSetting
import com.github.magisk317.smscode.forwarder.entity.setting.WeworkRobotSetting
import com.github.magisk317.smscode.forwarder.utils.sender.BarkUtils
import com.github.magisk317.smscode.forwarder.utils.sender.DingtalkGroupRobotUtils
import com.github.magisk317.smscode.forwarder.utils.sender.DingtalkInnerRobotUtils
import com.github.magisk317.smscode.forwarder.utils.sender.EmailUtils
import com.github.magisk317.smscode.forwarder.utils.sender.FeishuAppUtils
import com.github.magisk317.smscode.forwarder.utils.sender.FeishuUtils
import com.github.magisk317.smscode.forwarder.utils.sender.GotifyUtils
import com.github.magisk317.smscode.forwarder.utils.sender.PushplusUtils
import com.github.magisk317.smscode.forwarder.utils.sender.ServerchanUtils
import com.github.magisk317.smscode.forwarder.utils.sender.SmsUtils
import com.github.magisk317.smscode.forwarder.utils.sender.SocketUtils
import com.github.magisk317.smscode.forwarder.utils.sender.TelegramUtils
import com.github.magisk317.smscode.forwarder.utils.sender.UrlSchemeUtils
import com.github.magisk317.smscode.forwarder.utils.sender.WebhookUtils
import com.github.magisk317.smscode.forwarder.utils.sender.WeworkAgentUtils
import com.github.magisk317.smscode.forwarder.utils.sender.WeworkRobotUtils
import com.google.gson.Gson
import com.tianma.xsmscode.storage.BuildConfig
import com.tianma.xsmscode.common.utils.XLog
import com.tianma.xsmscode.data.db.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object SendUtils {
    private const val TAG = "SendUtils"
    private val gson = Gson()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Entry point called from [ForwardReceiver] after receiving IPC broadcast from Xposed layer.
     * Queries all enabled Senders from Room DB and dispatches the message to each channel.
     */
    fun sendMsg(context: Context, msgInfo: MsgInfo, isCodeSms: Boolean = true) {
        XLog.i("Dispatching MsgInfo to enabled senders: isCodeSms=%s msg=%s", isCodeSms, msgInfo)
        scope.launch {
            try {
                val db = AppDatabase.getInstance(context)
                val senders = db.senderDao().getAll().filter { sender ->
                    sender.status == 1 && (isCodeSms || sender.receiveNonCode == 1)
                }
                if (senders.isEmpty()) {
                    XLog.w("No eligible senders found (isCodeSms=%s), skipping dispatch.", isCodeSms)
                    return@launch
                }
                val commonConfig = ForwardCommonConfigStore.load(context)
                val msgForSend = ForwardCommonConfigStore.applyToMessage(context, msgInfo, commonConfig)
                for (sender in senders) {
                    dispatchToSender(context, sender, msgForSend)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                XLog.e("Dispatch failed", e)
            }
        }
    }

    private suspend fun dispatchToSender(context: Context, sender: Sender, msgInfo: MsgInfo) {
        XLog.d("Dispatching to sender: id=%d, type=%d, name=%s", sender.id, sender.type, sender.name)
        try {
            when (sender.type) {
                SenderType.DINGTALK_GROUP_ROBOT -> {
                    val setting = gson.fromJson(sender.jsonSetting, DingtalkGroupRobotSetting::class.java)
                    DingtalkGroupRobotUtils.sendMsg(setting, msgInfo)
                }
                SenderType.EMAIL -> {
                    val setting = gson.fromJson(sender.jsonSetting, EmailSetting::class.java)
                    EmailUtils.sendMsg(setting, msgInfo)
                }
                SenderType.BARK -> {
                    val setting = gson.fromJson(sender.jsonSetting, BarkSetting::class.java)
                    BarkUtils.sendMsg(setting, msgInfo)
                }
                SenderType.WEBHOOK -> {
                    val setting = gson.fromJson(sender.jsonSetting, WebhookSetting::class.java)
                    WebhookUtils.sendMsg(setting, msgInfo)
                }
                SenderType.WEWORK_ROBOT -> {
                    val setting = gson.fromJson(sender.jsonSetting, WeworkRobotSetting::class.java)
                    WeworkRobotUtils.sendMsg(setting, msgInfo)
                }
                SenderType.WEWORK_AGENT -> {
                    val setting = gson.fromJson(sender.jsonSetting, WeworkAgentSetting::class.java)
                    WeworkAgentUtils.sendMsg(setting, msgInfo)
                }
                SenderType.SERVERCHAN -> {
                    val setting = gson.fromJson(sender.jsonSetting, ServerchanSetting::class.java)
                    ServerchanUtils.sendMsg(setting, msgInfo)
                }
                SenderType.PUSHPLUS -> {
                    val setting = gson.fromJson(sender.jsonSetting, PushplusSetting::class.java)
                    PushplusUtils.sendMsg(setting, msgInfo)
                }
                SenderType.TELEGRAM -> {
                    val setting = gson.fromJson(sender.jsonSetting, TelegramSetting::class.java)
                    TelegramUtils.sendMsg(setting, msgInfo)
                }
                SenderType.SMS -> {
                    if (!BuildConfig.ENABLE_SMS_CHANNEL) {
                        XLog.w("SMS sender disabled in current distribution, skipping sender [%s]", sender.name)
                    } else {
                        val setting = gson.fromJson(sender.jsonSetting, SmsSetting::class.java)
                        SmsUtils.sendMsg(context, setting, msgInfo)
                    }
                }
                SenderType.FEISHU -> {
                    val setting = gson.fromJson(sender.jsonSetting, FeishuSetting::class.java)
                    FeishuUtils.sendMsg(setting, msgInfo)
                }
                SenderType.GOTIFY -> {
                    val setting = gson.fromJson(sender.jsonSetting, GotifySetting::class.java)
                    GotifyUtils.sendMsg(setting, msgInfo)
                }
                SenderType.DINGTALK_INNER_ROBOT -> {
                    val setting = gson.fromJson(sender.jsonSetting, DingtalkInnerRobotSetting::class.java)
                    DingtalkInnerRobotUtils.sendMsg(setting, msgInfo)
                }
                SenderType.FEISHU_APP -> {
                    val setting = gson.fromJson(sender.jsonSetting, FeishuAppSetting::class.java)
                    FeishuAppUtils.sendMsg(setting, msgInfo)
                }
                SenderType.URL_SCHEME -> {
                    val setting = gson.fromJson(sender.jsonSetting, UrlSchemeSetting::class.java)
                    UrlSchemeUtils.sendMsg(context, setting, msgInfo)
                }
                SenderType.SOCKET -> {
                    val setting = gson.fromJson(sender.jsonSetting, SocketSetting::class.java)
                    SocketUtils.sendMsg(setting, msgInfo)
                }
                else -> XLog.w("Unsupported sender type: %d, skipping.", sender.type)
            }
            XLog.i("Dispatched to sender [%s] type=%d", sender.name, sender.type)
        } catch (e: com.google.gson.JsonSyntaxException) {
            XLog.e("Failed to parse sender setting for [%s]", sender.name, e)
        } catch (e: Exception) {
            XLog.e("Failed to dispatch to sender [%s] type=%d", sender.name, sender.type, e)
        }
    }
}

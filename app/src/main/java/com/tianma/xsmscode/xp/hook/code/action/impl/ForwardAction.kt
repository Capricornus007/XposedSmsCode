package com.tianma.xsmscode.xp.hook.code.action.impl

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.os.Bundle
import com.github.tianma8023.xposed.smscode.R
import com.tianma.xsmscode.common.utils.PrefsReader
import com.tianma.xsmscode.common.utils.XLog
import com.tianma.xsmscode.data.db.DBProvider
import com.tianma.xsmscode.data.db.entity.SmsMsg
import com.tianma.xsmscode.xp.hook.code.action.CallableAction
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * Forward SMS code to webhook and record forwarding result.
 */
class ForwardAction(pluginContext: Context, phoneContext: Context, smsMsg: SmsMsg) :
    CallableAction(pluginContext, phoneContext, smsMsg) {

    private data class ChannelResult(
        val target: String,
        val success: Boolean,
        val message: String,
    )

    override fun action(): Bundle? {
        if (!PrefsReader.forwardEnabled(mPluginContext)) return null

        val isCodeSms = !mSmsMsg.smsCode.isNullOrBlank()
        val webhookEnabled = PrefsReader.forwardWebhookEnabled(mPluginContext)
        val webhookUrl = PrefsReader.forwardWebhookUrl(mPluginContext).trim()
        val webhookIncludeBody = PrefsReader.forwardWebhookIncludeBody(mPluginContext)
        val webhookNonCodeEnabled = PrefsReader.forwardWebhookNonCodeEnabled(mPluginContext)
        val tgEnabled = PrefsReader.forwardTelegramEnabled(mPluginContext)
        val tgBotToken = PrefsReader.forwardTelegramBotToken(mPluginContext).trim()
        val tgChatId = PrefsReader.forwardTelegramChatId(mPluginContext).trim()
        val tgTopicId = PrefsReader.forwardTelegramTopicId(mPluginContext).trim()
        val tgIncludeBody = PrefsReader.forwardTelegramIncludeBody(mPluginContext)
        val tgNonCodeEnabled = PrefsReader.forwardTelegramNonCodeEnabled(mPluginContext)
        if (webhookUrl.isBlank()) {
            if (!tgEnabled) {
                persistForwardResult(
                    success = false,
                    target = "",
                    message = mPluginContext.getString(R.string.forward_result_no_channel),
                )
                return null
            }
        }

        val channelResults = mutableListOf<ChannelResult>()

        if (webhookEnabled && (isCodeSms || webhookNonCodeEnabled)) {
            if (webhookUrl.isBlank()) {
                channelResults += ChannelResult(
                    target = mPluginContext.getString(R.string.forward_channel_webhook),
                    success = false,
                    message = mPluginContext.getString(R.string.forward_result_webhook_empty),
                )
            } else {
                channelResults += forwardToWebhook(webhookUrl, webhookIncludeBody, isCodeSms)
            }
        }

        if (tgEnabled && (isCodeSms || tgNonCodeEnabled)) {
            if (tgBotToken.isBlank() || tgChatId.isBlank()) {
                channelResults += ChannelResult(
                    target = mPluginContext.getString(R.string.forward_channel_tg),
                    success = false,
                    message = mPluginContext.getString(R.string.forward_result_tg_config_invalid),
                )
            } else {
                channelResults += forwardToTelegram(tgBotToken, tgChatId, tgTopicId, tgIncludeBody, isCodeSms)
            }
        }

        if (channelResults.isEmpty()) {
            persistForwardResult(
                success = false,
                target = "",
                message = mPluginContext.getString(R.string.forward_result_no_channel),
            )
            return null
        }

        val anySuccess = channelResults.any { it.success }
        val target = channelResults.joinToString(" | ") { it.target }
        val message = channelResults.joinToString("; ") { it.message }
        persistForwardResult(success = anySuccess, target = target, message = message)
        return null
    }

    private fun forwardToWebhook(webhookUrl: String, includeBody: Boolean, isCodeSms: Boolean): ChannelResult {
        val isFeishuWebhook = isFeishuWebhookUrl(webhookUrl)
        val payload = if (isFeishuWebhook) {
            buildFeishuPayload(includeBody, isCodeSms)
        } else {
            buildGenericPayload(includeBody, isCodeSms)
        }
        val request = Request.Builder()
            .url(webhookUrl)
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()
        return runCatching {
            CLIENT.newCall(request).execute().use { response ->
                val responseBody = response.body.string()
                val responseSummary = parseWebhookResponseSummary(response, responseBody, isFeishuWebhook)
                val success = if (isFeishuWebhook) {
                    response.isSuccessful && responseSummary.feishuCode == 0
                } else {
                    response.isSuccessful
                }
                ChannelResult(
                    target = "${mPluginContext.getString(R.string.forward_channel_webhook)}:$webhookUrl",
                    success = success,
                    message = responseSummary.message,
                )
            }
        }.getOrElse { t ->
            ChannelResult(
                target = "${mPluginContext.getString(R.string.forward_channel_webhook)}:$webhookUrl",
                success = false,
                message = "${mPluginContext.getString(R.string.forward_channel_webhook)} ${
                    mPluginContext.getString(
                        R.string.forward_result_failed,
                        t.message ?: mPluginContext.getString(R.string.unknown),
                    )
                }",
            )
        }
    }

    private fun buildGenericPayload(includeBody: Boolean, isCodeSms: Boolean): JSONObject {
        if (!isCodeSms) {
            return JSONObject().apply {
                put("body", mSmsMsg.body.orEmpty())
            }
        }
        return JSONObject().apply {
            put("sender", mSmsMsg.sender)
            put("company", mSmsMsg.company)
            put("code", mSmsMsg.smsCode)
            put("smsDate", mSmsMsg.date)
            put("forwardAt", System.currentTimeMillis())
            put("packageName", mSmsMsg.packageName)
            if (includeBody) {
                put("body", mSmsMsg.body)
            }
        }
    }

    private fun buildFeishuPayload(includeBody: Boolean, isCodeSms: Boolean): JSONObject {
        val text = buildString {
            if (isCodeSms) {
                append("Code: ").append(mSmsMsg.smsCode.orEmpty())
                append('\n').append("Sender: ").append(mSmsMsg.sender.orEmpty())
                if (!mSmsMsg.company.isNullOrBlank()) {
                    append('\n').append("Company: ").append(mSmsMsg.company)
                }
                append('\n').append("Time: ").append(mSmsMsg.date)
            }
            if (!isCodeSms) {
                append(mSmsMsg.body.orEmpty())
            } else if (includeBody) {
                append('\n').append("Body: ").append(mSmsMsg.body.orEmpty())
            }
        }
        return JSONObject().apply {
            put("msg_type", "text")
            put(
                "content",
                JSONObject().apply {
                    put("text", text)
                },
            )
        }
    }

    private data class WebhookResponseSummary(
        val message: String,
        val feishuCode: Int? = null,
    )

    private fun parseWebhookResponseSummary(
        response: Response,
        responseBody: String,
        isFeishuWebhook: Boolean,
    ): WebhookResponseSummary {
        if (!isFeishuWebhook) {
            return WebhookResponseSummary(
                message = "${mPluginContext.getString(R.string.forward_channel_webhook)} " +
                    mPluginContext.getString(R.string.forward_result_http, response.code),
            )
        }

        return runCatching {
            val json = JSONObject(responseBody)
            val code = if (json.has("code")) json.optInt("code", -1) else json.optInt("StatusCode", -1)
            val msg = json.optString("msg", json.optString("StatusMessage", ""))
            val suffix = if (msg.isNotBlank()) "($msg)" else ""
            WebhookResponseSummary(
                message = "${mPluginContext.getString(R.string.forward_channel_webhook)} HTTP ${response.code}, code=$code$suffix",
                feishuCode = code,
            )
        }.getOrElse {
            WebhookResponseSummary(
                message = "${mPluginContext.getString(R.string.forward_channel_webhook)} HTTP ${response.code}",
                feishuCode = if (response.isSuccessful) 0 else -1,
            )
        }
    }

    private fun isFeishuWebhookUrl(url: String): Boolean {
        return runCatching {
            val parsed = URL(url)
            val host = parsed.host.lowercase()
            val path = parsed.path.lowercase()
            host.contains("feishu.cn") || host.contains("larksuite.com") ||
                path.contains("/open-apis/bot/") || path.contains("/base/automation/webhook/")
        }.getOrDefault(false)
    }

    private fun forwardToTelegram(
        botToken: String,
        chatId: String,
        topicId: String,
        includeBody: Boolean,
        isCodeSms: Boolean,
    ): ChannelResult {
        val tgApi = "https://api.telegram.org/bot$botToken/sendMessage"
        val text = buildString {
            if (isCodeSms) {
                append("Code: ").append(mSmsMsg.smsCode.orEmpty())
                append('\n').append("Sender: ").append(mSmsMsg.sender.orEmpty())
                if (!mSmsMsg.company.isNullOrBlank()) {
                    append('\n').append("Company: ").append(mSmsMsg.company)
                }
                append('\n').append("Time: ").append(mSmsMsg.date)
            }
            if (!isCodeSms) {
                append(mSmsMsg.body.orEmpty())
            } else if (includeBody) {
                append('\n').append("Body: ").append(mSmsMsg.body.orEmpty())
            }
        }
        val body = FormBody.Builder()
            .add("chat_id", chatId)
            .add("text", text)
        if (topicId.isNotBlank()) {
            body.add("message_thread_id", topicId)
        }
        val reqBody = body.build()
        val request = Request.Builder()
            .url(tgApi)
            .post(reqBody)
            .build()
        return runCatching {
            CLIENT.newCall(request).execute().use { response ->
                ChannelResult(
                    target = "${mPluginContext.getString(R.string.forward_channel_tg)}:$chatId",
                    success = response.isSuccessful,
                    message = buildString {
                        append(mPluginContext.getString(R.string.forward_channel_tg))
                        append(' ')
                        append(mPluginContext.getString(R.string.forward_result_http, response.code))
                    },
                )
            }
        }.getOrElse { t ->
            ChannelResult(
                target = "${mPluginContext.getString(R.string.forward_channel_tg)}:$chatId",
                success = false,
                message = "${mPluginContext.getString(R.string.forward_channel_tg)} ${
                    mPluginContext.getString(
                        R.string.forward_result_failed,
                        t.message ?: mPluginContext.getString(R.string.unknown),
                    )
                }",
            )
        }
    }

    private fun persistForwardResult(success: Boolean, target: String?, message: String) {
        val resolver = mPluginContext.contentResolver
        val smsMsgUri = DBProvider.SMS_MSG_CONTENT_URI
        val now = System.currentTimeMillis()
        val status = if (success) SmsMsg.FORWARD_STATUS_SUCCESS else SmsMsg.FORWARD_STATUS_FAILED
        val trimmedMessage = message.take(MAX_MESSAGE_LEN)
        val values = ContentValues().apply {
            put("forward_status", status)
            put("forward_target", target)
            put("forward_message", trimmedMessage)
            put("forward_time", now)
        }

        try {
            var matchedId: Long? = null
            val projection = arrayOf("_id", "sender", "body", "date")
            resolver.query(smsMsgUri, projection, null, null, "date DESC")?.use { cursor ->
                val idIdx = cursor.getColumnIndexOrThrow("_id")
                val senderIdx = cursor.getColumnIndexOrThrow("sender")
                val bodyIdx = cursor.getColumnIndexOrThrow("body")
                val dateIdx = cursor.getColumnIndexOrThrow("date")
                while (cursor.moveToNext()) {
                    val sender = cursor.getString(senderIdx)
                    val body = cursor.getString(bodyIdx)
                    val date = cursor.getLong(dateIdx)
                    if (sender == mSmsMsg.sender && body == mSmsMsg.body && date == mSmsMsg.date) {
                        matchedId = cursor.getLong(idIdx)
                        break
                    }
                }
            }

            if (matchedId != null) {
                val itemUri = ContentUris.withAppendedId(smsMsgUri, matchedId)
                resolver.update(itemUri, values, null, null)
            } else {
                values.put("sender", mSmsMsg.sender)
                values.put("body", mSmsMsg.body)
                values.put("date", mSmsMsg.date)
                values.put("company", mSmsMsg.company)
                values.put("sms_code", mSmsMsg.smsCode)
                values.put("package_name", mSmsMsg.packageName)
                resolver.insert(smsMsgUri, values)
            }
        } catch (t: Throwable) {
            XLog.w("Persist forward result failed: %s", t.message ?: "unknown")
        }
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private const val MAX_MESSAGE_LEN = 300
        private val CLIENT = OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .writeTimeout(8, TimeUnit.SECONDS)
            .build()
    }
}

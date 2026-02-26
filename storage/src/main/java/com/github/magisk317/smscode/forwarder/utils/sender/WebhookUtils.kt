package com.github.magisk317.smscode.forwarder.utils.sender

import android.text.TextUtils
import android.util.Base64
import com.github.magisk317.smscode.forwarder.entity.MsgInfo
import com.github.magisk317.smscode.forwarder.entity.setting.WebhookSetting
import com.github.magisk317.smscode.forwarder.utils.HttpUtils
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object WebhookUtils {
    private const val TAG = "WebhookUtils"
    private val client = OkHttpClient.Builder().build()

    suspend fun sendMsg(setting: WebhookSetting, msgInfo: MsgInfo) = withContext(Dispatchers.IO) {
        var requestUrl: String = setting.webServer
        val from: String = msgInfo.from
        val content: String = msgInfo.content
        val timestamp = System.currentTimeMillis()

        var sign = ""
        if (!TextUtils.isEmpty(setting.secret)) {
            val stringToSign = "$timestamp\n" + setting.secret
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(setting.secret.toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
            val signData = mac.doFinal(stringToSign.toByteArray(StandardCharsets.UTF_8))
            sign = URLEncoder.encode(String(Base64.encode(signData, Base64.NO_WRAP)), "UTF-8")
        }

        val regex = "^(https?://)([^:]+):([^@]+)@(.+)".toRegex(RegexOption.IGNORE_CASE)
        val matches = regex.find(requestUrl)
        if (matches != null) {
            val groupValues = matches.groupValues
            if (groupValues.size >= 5) {
                requestUrl = groupValues[1] + groupValues[4]
            }
        }

        val headersBuilder = Headers.Builder()
        for ((key, value) in setting.headers) {
            headersBuilder.add(key, value)
        }

        if (setting.method == "GET") {
            if (requestUrl.contains("?")) {
                requestUrl += "&from=${URLEncoder.encode(from, "UTF-8")}&content=${URLEncoder.encode(content, "UTF-8")}"
            } else {
                requestUrl += "?from=${URLEncoder.encode(from, "UTF-8")}&content=${URLEncoder.encode(content, "UTF-8")}"
            }
            if (sign.isNotEmpty()) {
                requestUrl += "&timestamp=$timestamp&sign=$sign"
            }

            val request = Request.Builder()
                .url(requestUrl)
                .headers(headersBuilder.build())
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                val respBody = response.body.string()
                if (!response.isSuccessful) {
                    SLog.e(TAG, "Webhook GET Failed: ${response.code} ${response.message} $respBody")
                    throw IllegalStateException("Webhook GET 失败: HTTP ${response.code}")
                }
                SLog.i(TAG, "Webhook GET Success: ${response.code}")
            }
        } else {
            var bodyContent = setting.webParams
            if (bodyContent.isEmpty()) {
                val json = """{"from":"$from","content":"${content.replace("\"", "\\\"")}","timestamp":"$timestamp"}"""
                bodyContent = json
            } else {
                bodyContent = bodyContent.replace("[from]", from)
                    .replace("[content]", content)
            }

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val body = bodyContent.toRequestBody(mediaType)

            val request = Request.Builder()
                .url(requestUrl)
                .headers(headersBuilder.build())
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                val respBody = response.body.string()
                if (!response.isSuccessful) {
                    SLog.e(TAG, "Webhook POST Failed: ${response.code} ${response.message} $respBody")
                    throw IllegalStateException("Webhook POST 失败: HTTP ${response.code}")
                }
                SLog.i(TAG, "Webhook POST Success: ${response.code}")
            }
        }
    }
}

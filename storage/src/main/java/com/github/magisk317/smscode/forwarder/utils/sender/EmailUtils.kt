package com.github.magisk317.smscode.forwarder.utils.sender

import com.github.magisk317.smscode.forwarder.entity.MsgInfo
import com.github.magisk317.smscode.forwarder.entity.setting.EmailSetting
import jakarta.mail.Message
import jakarta.mail.PasswordAuthentication
import jakarta.mail.Session
import jakarta.mail.Transport
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Properties

object EmailUtils {
    private const val TAG = "EmailUtils"

    suspend fun sendMsg(setting: EmailSetting, msgInfo: MsgInfo) = withContext(Dispatchers.IO) {
        runCatching {
            normalizeMailType(setting)

            val fromEmail = setting.fromEmail
            val password = setting.pwd
            val host = setting.host
            val port = setting.port.ifBlank { "465" }
            val recipients = buildRecipients(setting)

            if (fromEmail.isBlank() || password.isBlank() || host.isBlank() || recipients.isEmpty()) {
                SLog.e(TAG, "Email config invalid")
                throw IllegalArgumentException("邮箱配置不完整")
            }

            val props = Properties().apply {
                put("mail.smtp.host", host)
                put("mail.smtp.port", port)
                put("mail.smtp.auth", "true")
                put("mail.smtp.ssl.enable", setting.ssl.toString())
                put("mail.smtp.starttls.enable", setting.startTls.toString())
            }

            val session = Session.getInstance(props, object : jakarta.mail.Authenticator() {
                override fun getPasswordAuthentication(): PasswordAuthentication {
                    return PasswordAuthentication(fromEmail, password)
                }
            })

            val message = MimeMessage(session)
            message.setFrom(InternetAddress(fromEmail, setting.fromEmailAlias.ifBlank { fromEmail }))
            message.setRecipients(Message.RecipientType.TO, recipients.map { InternetAddress(it) }.toTypedArray())
            message.subject = if (setting.title.isBlank()) "SmsCode: ${msgInfo.from}" else setting.title
            message.setText(msgInfo.content)

            Transport.send(message)
            SLog.i(TAG, "Email send success")
        }.onFailure {
            SLog.e(TAG, "Email send failed", it)
        }.getOrElse { throw it }
    }

    private fun buildRecipients(setting: EmailSetting): List<String> {
        val fromMap = setting.recipients.keys.toList().filter { it.isNotBlank() }
        if (fromMap.isNotEmpty()) return fromMap
        return setting.toEmail
            .replace("[,，;；]".toRegex(), ",")
            .split(',')
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }

    private fun normalizeMailType(setting: EmailSetting) {
        when (setting.mailType) {
            "@qq.com", "@foxmail.com" -> {
                setting.host = "smtp.qq.com"
                setting.port = "465"
                setting.ssl = true
                setting.fromEmail = appendDomain(setting.fromEmail, setting.mailType)
            }
            "@gmail.com" -> {
                setting.host = "smtp.gmail.com"
                setting.port = "465"
                setting.ssl = true
                setting.fromEmail = appendDomain(setting.fromEmail, setting.mailType)
            }
            "@163.com" -> {
                setting.host = "smtp.163.com"
                setting.port = "465"
                setting.ssl = true
                setting.fromEmail = appendDomain(setting.fromEmail, setting.mailType)
            }
            "@126.com" -> {
                setting.host = "smtp.126.com"
                setting.port = "465"
                setting.ssl = true
                setting.fromEmail = appendDomain(setting.fromEmail, setting.mailType)
            }
            "@outlook.com" -> {
                setting.host = "smtp.office365.com"
                setting.port = "587"
                setting.ssl = false
                setting.startTls = true
                setting.fromEmail = appendDomain(setting.fromEmail, setting.mailType)
            }
            "@icloud.com" -> {
                setting.host = "smtp.mail.me.com"
                setting.port = "587"
                setting.ssl = false
                setting.startTls = true
                setting.fromEmail = appendDomain(setting.fromEmail, setting.mailType)
            }
        }
    }

    private fun appendDomain(name: String, domain: String): String {
        if (name.contains("@")) return name
        return "$name$domain"
    }
}

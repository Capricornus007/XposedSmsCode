package com.github.magisk317.smscode.xp.hook.code.action.impl

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import com.github.magisk317.smscode.common.utils.PrefsReader
import com.github.magisk317.smscode.common.utils.SmsCodeUtils
import com.github.magisk317.smscode.common.utils.StringUtils
import io.github.magisk317.smscode.xposed.utils.XLog
import com.github.magisk317.smscode.data.db.DBManager
import com.github.magisk317.smscode.data.db.entity.SmsMsg
import com.github.magisk317.smscode.xp.hook.code.action.CallableAction

/**
 * 解析短信中的验证码
 */
class SmsParseAction(pluginContext: Context, phoneContext: Context, smsMsg: SmsMsg?) :
    CallableAction(pluginContext, phoneContext, smsMsg ?: SmsMsg()) {

    private var mSmsIntent: Intent? = null
    private var mDeduplicateEnabled: Boolean = false

    fun setSmsIntent(smsIntent: Intent?) {
        mSmsIntent = smsIntent
    }

    fun setDeduplicateEnabled(enabled: Boolean) {
        mDeduplicateEnabled = enabled
    }

    override fun action(): Bundle? = parseSmsMsg()

    private fun parseSmsMsg(): Bundle? {
        val intent = mSmsIntent ?: return null
        val smsMsg = SmsMsg.fromIntent(intent)
        XLog.w(
            "Diag SMS parsed from intent: senderPresent=%s, bodyLength=%d, timestamp=%d",
            !smsMsg.sender.isNullOrBlank(),
            smsMsg.body?.length ?: 0,
            smsMsg.date,
        )
        // Update the member variable of super class if possible, but it's val.
        // Actually, CallableAction should have var mSmsMsg or we use the local one.
        // Wait, CallableAction has @JvmField protected val mSmsMsg.
        // I'll use a local variable and update fields of mSmsMsg if it's not final in Java.
        // But in Kotlin it's val.

        val sender = smsMsg.sender
        val msgBody = smsMsg.body
        val sensitiveDebugLog = PrefsReader.isSensitiveDebugLogMode(mPluginContext)

        XLog.d(
            "Sender: %s",
            if (sensitiveDebugLog) StringUtils.escape(sender) else StringUtils.summarizeSender(sender),
        )
        XLog.d(
            "Body: %s",
            if (sensitiveDebugLog) StringUtils.escape(msgBody) else StringUtils.summarizeBody(msgBody),
        )

        if (TextUtils.isEmpty(sender) || TextUtils.isEmpty(msgBody)) {
            XLog.w("Diag SMS parse aborted: sender/body is empty")
            return null
        }

        val msgBodyNotNull = msgBody ?: ""
        val timestamp = if (smsMsg.date > 0) smsMsg.date else System.currentTimeMillis()
        XLog.w(
            "Diag SMS body: %s",
            if (sensitiveDebugLog) StringUtils.escape(msgBodyNotNull) else StringUtils.summarizeBody(msgBodyNotNull),
        )
        if (mDeduplicateEnabled) {
            val duplicated = runCatching {
                DBManager.get(mPluginContext).querySmsMsgByFingerprint(sender, msgBodyNotNull, timestamp) != null
            }.getOrDefault(false)
            if (duplicated) {
                XLog.i("Duplicate SMS detected by fingerprint, skip parsing.")
                return Bundle().apply { putBoolean(SMS_DUPLICATED, true) }
            }
        }

        val smsCode = kotlinx.coroutines.runBlocking {
            SmsCodeUtils.parseSmsCodeIfExists(
                mPluginContext,
                msgBodyNotNull,
            )
        }
        if (TextUtils.isEmpty(smsCode)) { // isn't code message
            XLog.w(
                "Diag SMS parsed but no code matched, body=%s",
                if (sensitiveDebugLog) StringUtils.escape(msgBodyNotNull) else StringUtils.summarizeBody(msgBodyNotNull),
            )
            return null
        }

        // Prefer the first bracket label that can be mapped to an installed package.
        // Once package is resolved, keep that label only and do not append other tokens.
        val companyCandidates = SmsCodeUtils.parseCompanyCandidates(msgBodyNotNull)
            .map { it.trim().trim('【', '】', '[', ']') }
            .filter { it.isNotBlank() }
        var company = SmsCodeUtils.parseCompany(msgBodyNotNull)
            .trim()
            .trim('【', '】', '[', ']')
        var resolvedPackageName: String? = null
        for (candidate in companyCandidates) {
            val pkg = SmsCodeUtils.findPackageNameByLabel(mPhoneContext, candidate)
            if (!pkg.isNullOrBlank()) {
                company = candidate
                resolvedPackageName = pkg
                break
            }
        }
        if (resolvedPackageName.isNullOrBlank()) {
            resolvedPackageName = SmsCodeUtils.findPackageNameByLabel(mPhoneContext, company)
        }
        mSmsMsg = smsMsg.copy(
            smsCode = smsCode,
            company = company,
            date = timestamp,
            packageName = resolvedPackageName,
        )
        XLog.w(
            "Diag SMS code matched: companyPresent=%s, codeLength=%d, code=%s, body=%s",
            !company.isNullOrBlank(),
            smsCode.length,
            if (sensitiveDebugLog) StringUtils.escape(smsCode) else StringUtils.summarizeCode(smsCode),
            if (sensitiveDebugLog) StringUtils.escape(msgBodyNotNull) else StringUtils.summarizeBody(msgBodyNotNull),
        )

        val bundle = Bundle()
        bundle.putParcelable(SMS_MSG, mSmsMsg)

        bundle.putBoolean(SMS_DUPLICATED, false)
        return bundle
    }

    companion object {
        const val SMS_MSG = "sms_msg"
        const val SMS_DUPLICATED = "sms_duplicated"
    }
}

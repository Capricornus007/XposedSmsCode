package com.tianma.xsmscode.xp.hook.code.action.impl

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import com.github.tianma8023.xposed.smscode.BuildConfig
import com.tianma.xsmscode.common.utils.SmsCodeUtils
import com.tianma.xsmscode.common.utils.StringUtils
import com.tianma.xsmscode.common.utils.XLog
import com.tianma.xsmscode.data.db.entity.SmsMsg
import com.tianma.xsmscode.xp.hook.code.action.CallableAction

/**
 * 解析短信中的验证码
 */
class SmsParseAction(pluginContext: Context, phoneContext: Context, smsMsg: SmsMsg?) :
    CallableAction(pluginContext, phoneContext, smsMsg ?: SmsMsg()) {

    private var mSmsIntent: Intent? = null

    fun setSmsIntent(smsIntent: Intent?) {
        mSmsIntent = smsIntent
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

        if (BuildConfig.DEBUG) {
            XLog.d("Sender: %s", sender)
            XLog.d("Body: %s", msgBody)
        } else {
            XLog.d("Sender: %s", StringUtils.escape(sender))
            XLog.d("Body length: %d", msgBody?.length ?: 0)
        }

        if (TextUtils.isEmpty(sender) || TextUtils.isEmpty(msgBody)) {
            XLog.w("Diag SMS parse aborted: sender/body is empty")
            return null
        }

        val msgBodyNotNull = msgBody ?: ""
        val smsCode = kotlinx.coroutines.runBlocking {
            SmsCodeUtils.parseSmsCodeIfExists(
                mPluginContext,
                msgBodyNotNull,
            )
        }
        if (TextUtils.isEmpty(smsCode)) { // isn't code message
            XLog.w("Diag SMS parsed but no code matched")
            return null
        }

        val timestamp = if (smsMsg.date > 0) smsMsg.date else System.currentTimeMillis()

        // Update mSmsMsg using copy() to maintain immutability pattern
        val company = SmsCodeUtils.parseCompany(msgBodyNotNull)
        mSmsMsg = smsMsg.copy(
            smsCode = smsCode,
            company = company,
            date = timestamp,
            packageName = SmsCodeUtils.findPackageNameByLabel(mPhoneContext, company),
        )
        XLog.w(
            "Diag SMS code matched: companyPresent=%s, codeLength=%d",
            !company.isNullOrBlank(),
            smsCode.length,
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

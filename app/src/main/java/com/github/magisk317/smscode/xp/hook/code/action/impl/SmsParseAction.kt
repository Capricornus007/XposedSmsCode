package com.github.magisk317.smscode.xp.hook.code.action.impl

import android.content.Context
import android.content.Intent
import com.github.magisk317.smscode.runtime.RuntimePrefsFacade as PrefsReader
import com.github.magisk317.smscode.common.utils.SmsCodeUtils
import io.github.magisk317.smscode.runtime.common.utils.StringUtils
import com.github.magisk317.smscode.data.db.entity.SmsMsg
import com.github.magisk317.smscode.runtime.RuntimeStorageFacade
import io.github.magisk317.smscode.verification.SmsParseAction as SharedSmsParseAction
import com.github.magisk317.smscode.xp.hook.code.action.CallableAction
import com.github.magisk317.smscode.xp.hook.code.VerificationSmsMsg
import com.github.magisk317.smscode.xp.hook.code.toVerificationMessage

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

    override fun action(): android.os.Bundle? {
        val outcome = SharedSmsParseAction<VerificationSmsMsg>(
            pluginContext = mPluginContext,
            phoneContext = mPhoneContext,
            smsIntent = mSmsIntent,
            deduplicateEnabled = mDeduplicateEnabled,
            incomingSmsParser = { SmsMsg.fromIntent(it).toVerificationMessage() },
            sensitiveDebugLogReader = PrefsReader::isSensitiveDebugLogMode,
            summarizeSender = { sensitive, value ->
                if (sensitive) StringUtils.escape(value).orEmpty() else StringUtils.summarizeSender(value)
            },
            summarizeBody = { sensitive, value ->
                if (sensitive) StringUtils.escape(value).orEmpty() else StringUtils.summarizeBody(value)
            },
            summarizeCode = { sensitive, value ->
                if (sensitive) StringUtils.escape(value).orEmpty() else StringUtils.summarizeCode(value)
            },
            duplicateChecker = { sender, body, timestamp ->
                runCatching {
                    RuntimeStorageFacade.dbManager(mPluginContext).querySmsMsgByFingerprint(sender, body, timestamp) != null
                }.getOrDefault(false)
            },
            preparedSmsResolver = { pluginContext, phoneContext, smsMsg, _, timestamp ->
                val msgBody = smsMsg.raw.body.orEmpty()
                val smsCode = SmsCodeUtils.parseSmsCodeIfExists(pluginContext, msgBody)
                if (smsCode.isBlank()) {
                    return@SharedSmsParseAction null
                }
                val companyCandidates = SmsCodeUtils.parseCompanyCandidates(msgBody)
                    .map { it.trim().trim('【', '】', '[', ']') }
                    .filter { it.isNotBlank() }
                var company = SmsCodeUtils.parseCompany(msgBody)
                    .trim()
                    .trim('【', '】', '[', ']')
                var resolvedPackageName: String? = null
                for (candidate in companyCandidates) {
                    val pkg = SmsCodeUtils.findPackageNameByLabel(phoneContext, candidate)
                    if (!pkg.isNullOrBlank()) {
                        company = candidate
                        resolvedPackageName = pkg
                        break
                    }
                }
                if (resolvedPackageName.isNullOrBlank()) {
                    resolvedPackageName = SmsCodeUtils.findPackageNameByLabel(phoneContext, company)
                }
                smsMsg.raw.copy(
                    smsCode = smsCode,
                    company = company,
                    date = timestamp,
                    packageName = resolvedPackageName,
                ).toVerificationMessage()
            },
        ).parse() ?: return null
        mSmsMsg = outcome.smsMsg?.raw ?: mSmsMsg
        return outcome.toBundle()
    }

    companion object {
        const val SMS_MSG = SharedSmsParseAction.KEY_SMS_MSG
        const val SMS_DUPLICATED = SharedSmsParseAction.KEY_SMS_DUPLICATED
    }
}

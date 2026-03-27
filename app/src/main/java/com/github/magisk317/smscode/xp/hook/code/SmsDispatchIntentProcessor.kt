package com.github.magisk317.smscode.xp.hook.code

import android.content.Context
import android.content.Intent
import com.github.magisk317.smscode.common.utils.SmsBlacklistUtils
import com.github.magisk317.smscode.data.db.entity.SmsMsg
import io.github.magisk317.smscode.verification.BlacklistMatchResult
import io.github.magisk317.smscode.verification.SmsHandlerDispatchDecision
import io.github.magisk317.smscode.xposed.utils.XLog

internal class SmsDispatchIntentProcessor(
    private val pluginContext: Context,
    private val phoneContext: Context,
    private val incomingSmsParser: (Intent) -> SmsMsg? = SmsMsg::fromIntent,
    private val blacklistMatcher: (Context, String?, String?) -> BlacklistMatchResult = { context, sender, body ->
        val result = SmsBlacklistUtils.match(context, sender, body)
        BlacklistMatchResult(
            matched = result.matched,
            matchType = result.matchType,
            pattern = result.pattern,
            actionDelete = result.actionDelete,
            actionBlock = result.actionBlock,
        )
    },
    private val codeParser: (Context, Context, Intent, String) -> ParseResult? = { resolvedPluginContext, resolvedPhoneContext, intent, eventId ->
        CodeWorker(resolvedPluginContext, resolvedPhoneContext, intent, eventId).parse()
    },
) {
    data class Outcome(
        val smsMsg: SmsMsg?,
        val blacklistResult: BlacklistMatchResult,
        val parseResult: ParseResult?,
        val decision: SmsHandlerDispatchDecision.Decision,
    )

    fun handle(intent: Intent, eventId: String): Outcome {
        val smsMsg = incomingSmsParser(intent)
        val blacklistResult = blacklistMatcher(pluginContext, smsMsg?.sender, smsMsg?.body)
        if (blacklistResult.matched) {
            XLog.w(
                "Diag sms blacklist matched: event_id=%s type=%s, pattern=%s, delete=%s, block=%s",
                eventId,
                blacklistResult.matchType,
                blacklistResult.pattern,
                blacklistResult.actionDelete,
                blacklistResult.actionBlock,
            )
        }

        val parseResult = codeParser(pluginContext, phoneContext, intent, eventId)
        if (parseResult == null) {
            XLog.w("Diag parse result is null: event_id=%s no code matched or parse failed", eventId)
        } else {
            XLog.w("Diag parse result: event_id=%s blockSms=%s", eventId, parseResult.isBlockSms)
        }

        val decision = SmsHandlerDispatchDecision.evaluate(
            blacklistMatched = blacklistResult.matched,
            blacklistActionDelete = blacklistResult.actionDelete,
            blacklistActionBlock = blacklistResult.actionBlock,
            smsMsgAvailable = smsMsg != null,
            parseResultBlockSms = parseResult?.isBlockSms,
        )
        return Outcome(
            smsMsg = smsMsg,
            blacklistResult = blacklistResult,
            parseResult = parseResult,
            decision = decision,
        )
    }
}

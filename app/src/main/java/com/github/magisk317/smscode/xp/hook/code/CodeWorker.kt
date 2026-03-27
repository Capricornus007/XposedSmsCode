package com.github.magisk317.smscode.xp.hook.code

import android.content.Context
import android.content.Intent
import androidx.core.os.BundleCompat
import com.github.tianma8023.xposed.smscode.BuildConfig
import com.github.magisk317.smscode.common.utils.PrefsReader
import com.github.magisk317.smscode.data.db.entity.SmsMsg
import io.github.magisk317.smscode.verification.CodeWorker as SharedCodeWorker
import io.github.magisk317.smscode.verification.SmsCodePostParseCoordinator
import io.github.magisk317.smscode.xposed.utils.XLog
import com.github.magisk317.smscode.xp.hook.code.action.impl.KillMeAction
import com.github.magisk317.smscode.xp.hook.code.action.impl.SmsParseAction
import java.util.concurrent.TimeUnit

class CodeWorker(
    private val mPluginContext: Context,
    private val mPhoneContext: Context,
    private val mSmsIntent: Intent,
    private val eventId: String = "",
) {
    fun parse(): ParseResult? {
        return SharedCodeWorker<VerificationSmsMsg, ParseResult>(
            pluginContext = mPluginContext,
            phoneContext = mPhoneContext,
            smsIntent = mSmsIntent,
            eventId = eventId,
            settingsLoader = { context -> SmsCodePostParseCoordinator.loadSettings(SmsCodeVerificationPrefs(context)) },
            moduleEnabledReader = PrefsReader::isEnabled,
            verboseLogReader = PrefsReader::isVerboseLogMode,
            logLevelSetter = XLog::setLogLevel,
            currentLogLevelReader = XLog::getLogLevel,
            defaultLogLevel = BuildConfig.LOG_LEVEL,
            parseRunner = ::runSmsParseAction,
            parsedSmsDispatcher = { uiHandler, executor, pluginContext, phoneContext, smsMsg, eventId, plan ->
                SmsCodeActionDispatcher.dispatchParsedSmsActions(
                    uiHandler = uiHandler,
                    executor = executor,
                    pluginContext = pluginContext,
                    phoneContext = phoneContext,
                    smsMsg = smsMsg.raw,
                    eventId = eventId,
                    plan = plan,
                )
            },
            afterDispatch = { executor, pluginContext, phoneContext, smsMsg, plan ->
                if (PrefsReader.killMeEnabled(pluginContext)) {
                    scheduleKillMe(executor, pluginContext, phoneContext, smsMsg.raw, plan.autoInputDelayMs)
                }
            },
            parseResultFactory = ::buildParseResult,
        ).parse()
    }

    private fun scheduleKillMe(
        executor: java.util.concurrent.ScheduledExecutorService,
        pluginContext: Context,
        phoneContext: Context,
        smsMsg: SmsMsg,
        autoInputDelayMs: Long?,
    ) {
        if (autoInputDelayMs == null) {
            XLog.w("KillMe enabled but auto-input disabled, skip KillMeAction")
            return
        }
        val killDelayMs = maxOf(autoInputDelayMs + 1500L, 2500L)
        executor.schedule(
            KillMeAction(pluginContext, phoneContext, smsMsg),
            killDelayMs,
            TimeUnit.MILLISECONDS,
        )
    }

    private fun runSmsParseAction(
        executor: java.util.concurrent.ScheduledExecutorService,
        pluginContext: Context,
        phoneContext: Context,
        smsIntent: Intent,
        deduplicateEnabled: Boolean,
    ): SharedCodeWorker.ParseOutcome<VerificationSmsMsg>? {
        val smsParseAction = SmsParseAction(pluginContext, phoneContext, null)
        smsParseAction.setSmsIntent(smsIntent)
        smsParseAction.setDeduplicateEnabled(deduplicateEnabled)
        val parseBundle = executor.schedule(smsParseAction, 0, TimeUnit.MILLISECONDS).get() ?: return null
        if (parseBundle.getBoolean(SmsParseAction.SMS_DUPLICATED, false)) {
            return SharedCodeWorker.ParseOutcome<VerificationSmsMsg>(duplicated = true)
        }
        val smsMsg = BundleCompat.getParcelable(parseBundle, SmsParseAction.SMS_MSG, SmsMsg::class.java)
            ?: return null
        return SharedCodeWorker.ParseOutcome(
            smsMsg = smsMsg.toVerificationMessage(),
            duplicated = false,
        )
    }

    private fun buildParseResult(blockSms: Boolean): ParseResult {
        return ParseResult().apply { isBlockSms = blockSms }
    }
}

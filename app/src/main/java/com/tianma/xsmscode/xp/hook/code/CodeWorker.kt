package com.tianma.xsmscode.xp.hook.code

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.os.BundleCompat
import com.github.tianma8023.xposed.smscode.BuildConfig
import com.tianma.xsmscode.common.utils.PrefsReader
import com.tianma.xsmscode.common.utils.XLog
import com.tianma.xsmscode.data.db.entity.SmsMsg
import com.tianma.xsmscode.xp.hook.code.action.impl.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class CodeWorker(
    private val mPluginContext: Context,
    private val mPhoneContext: Context,
    private val mSmsIntent: Intent,
) {
    private val mUIHandler: Handler = Handler(Looper.getMainLooper())
    private val mScheduledExecutor = Executors.newSingleThreadScheduledExecutor()

    fun parse(): ParseResult? {
        if (!PrefsReader.isEnabled(mPluginContext)) {
            XLog.i("XposedSmsCode disabled, exiting")
            return null
        }

        val verboseLog = PrefsReader.isVerboseLogMode(mPluginContext)
        if (verboseLog) {
            XLog.setLogLevel(Log.VERBOSE)
        } else {
            XLog.setLogLevel(BuildConfig.LOG_LEVEL)
        }

        val smsParseAction = SmsParseAction(mPluginContext, mPhoneContext, null)
        smsParseAction.setSmsIntent(mSmsIntent)
        val smsParseFuture = mScheduledExecutor.schedule(smsParseAction, 0, TimeUnit.MILLISECONDS)

        val smsMsg: SmsMsg
        try {
            val parseBundle = smsParseFuture.get() ?: return null

            val duplicated = parseBundle.getBoolean(SmsParseAction.SMS_DUPLICATED, false)
            if (duplicated) {
                return buildParseResult()
            }

            smsMsg = BundleCompat.getParcelable(parseBundle, SmsParseAction.SMS_MSG, SmsMsg::class.java) ?: return null
        } catch (e: Exception) {
            XLog.e("Error occurs when get SmsParseAction call value", e)
            return null
        }

        // 复制到剪切板 Action
        mUIHandler.post(CopyToClipboardAction(mPluginContext, mPhoneContext, smsMsg))

        // 显示Toast Action
        mUIHandler.post(ToastAction(mPluginContext, mPhoneContext, smsMsg))

        // 自动输入 Action
        if (PrefsReader.autoInputCodeEnabled(mPluginContext)) {
            val autoInputAction = AutoInputAction(mPluginContext, mPhoneContext, smsMsg)
            val autoInputDelay = PrefsReader.getAutoInputCodeDelay(mPluginContext) * 1000L
            mScheduledExecutor.schedule(autoInputAction, autoInputDelay, TimeUnit.MILLISECONDS)
        }

        // 显示通知 Action
        val notifyAction = NotifyAction(mPluginContext, mPhoneContext, smsMsg)
        val notificationFuture = mScheduledExecutor.schedule(notifyAction, 0, TimeUnit.MILLISECONDS)

        // 记录验证码短信 Action
        val recordSmsAction = RecordSmsAction(mPluginContext, mPhoneContext, smsMsg)
        mScheduledExecutor.schedule(recordSmsAction, 0, TimeUnit.MILLISECONDS)

        // 操作验证码短信（标记为已读 或者 删除） Action
        val operateSmsAction = OperateSmsAction(mPluginContext, mPhoneContext, smsMsg)
        mScheduledExecutor.schedule(operateSmsAction, SMS_OPERATE_DELAY_MS, TimeUnit.MILLISECONDS)

        var autoCancelRetentionMs = 0L
        if (PrefsReader.autoCancelCodeNotification(mPluginContext)) {
            autoCancelRetentionMs = PrefsReader.getNotificationRetentionTime(mPluginContext) * 1000L
            val notificationId = smsMsg.hashCode()

            val cancelNotifyAction = CancelNotifyAction(mPluginContext, mPhoneContext, smsMsg)
            cancelNotifyAction.setNotificationId(notificationId)

            mScheduledExecutor.schedule(cancelNotifyAction, autoCancelRetentionMs, TimeUnit.MILLISECONDS)
            XLog.d("Scheduled CancelNotifyAction with delay: ${autoCancelRetentionMs}ms for ID: $notificationId")
        }

        // 自杀 Action - delay it if we need time for auto-cancel to fire.
        val killMeAction = KillMeAction(mPluginContext, mPhoneContext, smsMsg)
        val killDelayMs = if (autoCancelRetentionMs > 0L) {
            maxOf(4000L, autoCancelRetentionMs + 500L)
        } else {
            4000L
        }
        mScheduledExecutor.schedule(killMeAction, killDelayMs, TimeUnit.MILLISECONDS)

        mScheduledExecutor.shutdown()
        return buildParseResult()
    }

    private fun buildParseResult(): ParseResult {
        val parseResult = ParseResult()
        parseResult.isBlockSms = PrefsReader.blockSmsEnabled(mPluginContext)
        return parseResult
    }
    companion object {
        private const val SMS_OPERATE_DELAY_MS = 3000L
    }
}

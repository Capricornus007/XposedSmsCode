package com.github.magisk317.smscode.xp.hook.code

import android.content.Context
import android.os.Handler
import io.github.magisk317.smscode.runtime.common.utils.SharedRuntimeGate
import com.github.magisk317.smscode.data.db.entity.SmsMsg
import com.github.magisk317.smscode.xp.hook.code.action.impl.AutoInputAction
import com.github.magisk317.smscode.xp.hook.code.action.impl.CopyToClipboardAction
import com.github.magisk317.smscode.xp.hook.code.action.impl.NotifyAction
import com.github.magisk317.smscode.xp.hook.code.action.impl.OperateSmsAction
import com.github.magisk317.smscode.xp.hook.code.action.impl.RecordSmsAction
import com.github.magisk317.smscode.xp.hook.code.action.impl.ToastAction
import io.github.magisk317.smscode.verification.SmsCodeActionDispatcher as SharedSmsCodeActionDispatcher
import io.github.magisk317.smscode.verification.SmsCodePostParseCoordinator
import io.github.magisk317.smscode.verification.SmsMessageDedupKeys
import io.github.magisk317.smscode.xposed.utils.XLog
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

internal object SmsCodeActionDispatcher {
    fun dispatchParsedSmsActions(
        uiHandler: Handler,
        executor: ScheduledExecutorService,
        pluginContext: Context,
        phoneContext: Context,
        smsMsg: SmsMsg,
        eventId: String,
        plan: SmsCodePostParseCoordinator.ParsedSmsPlan,
        attemptId: Long? = null,
    ) {
        SharedSmsCodeActionDispatcher.dispatchParsedSmsActions(
            uiHandler = uiHandler,
            executor = executor,
            pluginContext = pluginContext,
            phoneContext = phoneContext,
            smsMsg = smsMsg.toVerificationMessage(),
            eventId = eventId,
            plan = plan,
            uiDispatcher = { handler, plugin, phone, message, uiPlan ->
                dispatchUiActions(handler, plugin, phone, message.raw, uiPlan)
            },
            autoInputScheduler = { scheduledExecutor, plugin, phone, message, delayMs, deduplicateEnabled, _ ->
                scheduleAutoInput(scheduledExecutor, plugin, phone, message.raw, delayMs, deduplicateEnabled, attemptId)
            },
            notificationScheduler = { scheduledExecutor, plugin, phone, message, notificationPlan ->
                scheduleNotification(scheduledExecutor, plugin, phone, message.raw, notificationPlan)
            },
            recordScheduler = { scheduledExecutor, plugin, phone, message, recordEventId, deduplicateEnabled ->
                scheduleRecord(scheduledExecutor, plugin, phone, message.raw, recordEventId, deduplicateEnabled)
            },
            operateSmsScheduler = { scheduledExecutor, plugin, phone, message, delays ->
                scheduleOperateSmsActions(scheduledExecutor, plugin, phone, message.raw, delays)
            },
        )
    }

    fun dispatchObservedSmsActions(
        executor: ScheduledExecutorService?,
        pluginContext: Context,
        phoneContext: Context,
        smsMsg: SmsMsg,
        eventId: String,
        plan: SmsCodePostParseCoordinator.ObservedSmsPlan,
        autoInputRunner: (Context, Context, SmsMsg, Boolean, Long?) -> Unit = ::runAutoInputNow,
        autoInputScheduler: (ScheduledExecutorService, Context, Context, SmsMsg, Long, Boolean, Long?) -> Unit = ::scheduleAutoInput,
        recordRunner: (Context, Context, SmsMsg, String, Boolean) -> Unit = ::runRecordNow,
    ) {
        SharedSmsCodeActionDispatcher.dispatchObservedSmsActions(
            executor = executor,
            pluginContext = pluginContext,
            phoneContext = phoneContext,
            smsMsg = smsMsg.toVerificationMessage(),
            eventId = eventId,
            plan = plan,
            autoInputRunner = { plugin, phone, message, deduplicateEnabled, attemptId ->
                autoInputRunner(plugin, phone, message.raw, deduplicateEnabled, attemptId)
            },
            autoInputScheduler = { scheduledExecutor, plugin, phone, message, delayMs, deduplicateEnabled, attemptId ->
                autoInputScheduler(scheduledExecutor, plugin, phone, message.raw, delayMs, deduplicateEnabled, attemptId)
            },
            recordRunner = { plugin, phone, message, recordEventId, deduplicateEnabled ->
                recordRunner(plugin, phone, message.raw, recordEventId, deduplicateEnabled)
            },
        )
    }

    private fun dispatchUiActions(
        uiHandler: Handler,
        pluginContext: Context,
        phoneContext: Context,
        smsMsg: SmsMsg,
        uiPlan: SmsCodePostParseCoordinator.UiPlan,
    ) {
        uiHandler.post(
            CopyToClipboardAction(
                pluginContext = pluginContext,
                phoneContext = phoneContext,
                smsMsg = smsMsg,
                enabled = uiPlan.copyToClipboardEnabled,
            ),
        )
        uiHandler.post(
            ToastAction(
                pluginContext = pluginContext,
                phoneContext = phoneContext,
                smsMsg = smsMsg,
                enabled = uiPlan.showToast,
            ),
        )
    }

    private fun runAutoInputNow(
        pluginContext: Context,
        phoneContext: Context,
        smsMsg: SmsMsg,
        deduplicateEnabled: Boolean,
        attemptId: Long? = null,
    ) {
        if (!claimAutoInputDispatch(pluginContext, smsMsg, delayMs = 0L)) {
            return
        }
        AutoInputAction(
            pluginContext = pluginContext,
            phoneContext = phoneContext,
            smsMsg = smsMsg,
            deduplicateEnabled = deduplicateEnabled,
            dispatchDelayMs = 0L,
            attemptId = attemptId,
        ).call()
    }

    private fun scheduleAutoInput(
        executor: ScheduledExecutorService,
        pluginContext: Context,
        phoneContext: Context,
        smsMsg: SmsMsg,
        delayMs: Long,
        deduplicateEnabled: Boolean,
        attemptId: Long? = null,
    ) {
        if (!claimAutoInputDispatch(pluginContext, smsMsg, delayMs)) {
            return
        }
        executor.schedule(
            AutoInputAction(
                pluginContext = pluginContext,
                phoneContext = phoneContext,
                smsMsg = smsMsg,
                deduplicateEnabled = deduplicateEnabled,
                dispatchDelayMs = delayMs,
                attemptId = attemptId,
            ),
            delayMs,
            TimeUnit.MILLISECONDS,
        )
    }

    private fun runRecordNow(
        pluginContext: Context,
        phoneContext: Context,
        smsMsg: SmsMsg,
        eventId: String,
        deduplicateEnabled: Boolean,
    ) {
        RecordSmsAction(
            pluginContext = pluginContext,
            phoneContext = phoneContext,
            smsMsg = smsMsg,
            eventId = eventId,
            enabled = true,
            deduplicateEnabled = deduplicateEnabled,
        ).call()
    }

    private fun scheduleRecord(
        executor: ScheduledExecutorService,
        pluginContext: Context,
        phoneContext: Context,
        smsMsg: SmsMsg,
        eventId: String,
        deduplicateEnabled: Boolean,
    ) {
        executor.schedule(
            RecordSmsAction(
                pluginContext = pluginContext,
                phoneContext = phoneContext,
                smsMsg = smsMsg,
                eventId = eventId,
                enabled = true,
                deduplicateEnabled = deduplicateEnabled,
            ),
            0,
            TimeUnit.MILLISECONDS,
        )
    }

    private fun scheduleNotification(
        executor: ScheduledExecutorService,
        pluginContext: Context,
        phoneContext: Context,
        smsMsg: SmsMsg,
        plan: SmsCodePostParseCoordinator.NotificationPlan,
    ) {
        executor.schedule(
            NotifyAction(
                pluginContext = pluginContext,
                phoneContext = phoneContext,
                smsMsg = smsMsg,
                enabled = true,
                autoCancelEnabled = plan.autoCancelDelayMs != null,
                retentionTimeMs = plan.autoCancelDelayMs ?: 0L,
            ),
            0,
            TimeUnit.MILLISECONDS,
        )
    }

    private fun scheduleOperateSmsActions(
        executor: ScheduledExecutorService,
        pluginContext: Context,
        phoneContext: Context,
        smsMsg: SmsMsg,
        delays: List<Long>,
    ) {
        delays.forEach { delayMs ->
            executor.schedule(
                OperateSmsAction(pluginContext, phoneContext, smsMsg),
                delayMs,
                TimeUnit.MILLISECONDS,
            )
        }
    }

    private fun claimAutoInputDispatch(
        pluginContext: Context,
        smsMsg: SmsMsg,
        delayMs: Long,
    ): Boolean {
        val key = SmsMessageDedupKeys.buildMessageKey(smsMsg.toVerificationMessage())
        if (key.isBlank()) return true
        val windowMs = (delayMs + AUTO_INPUT_DISPATCH_GUARD_EXTRA_MS)
            .coerceAtLeast(AUTO_INPUT_DISPATCH_GUARD_MIN_WINDOW_MS)
        val claim = SharedRuntimeGate.claimWithinWindow(
            context = pluginContext,
            fileName = SHARED_AUTO_INPUT_DISPATCH_GUARD_FILE_NAME,
            key = key,
            windowMs = windowMs,
            maxEntries = MAX_AUTO_INPUT_DISPATCH_GUARD_ENTRIES,
        )
        if (claim.claimed) {
            return true
        }
        XLog.w(
            "Auto input dispatch skipped: key=%s ageMs=%d delayMs=%d windowMs=%d",
            key,
            claim.ageMs ?: -1L,
            delayMs,
            windowMs,
        )
        return false
    }

    private const val SHARED_AUTO_INPUT_DISPATCH_GUARD_FILE_NAME = "auto_input_dispatch_guard"
    private const val AUTO_INPUT_DISPATCH_GUARD_EXTRA_MS = 5_000L
    private const val AUTO_INPUT_DISPATCH_GUARD_MIN_WINDOW_MS = 8_000L
    private const val MAX_AUTO_INPUT_DISPATCH_GUARD_ENTRIES = 128
}

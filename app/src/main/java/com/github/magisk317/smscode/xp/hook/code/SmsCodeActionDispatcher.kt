package com.github.magisk317.smscode.xp.hook.code

import android.content.Context
import android.os.Handler
import com.github.magisk317.smscode.data.db.entity.SmsMsg
import io.github.magisk317.smscode.verification.SmsCodeActionDispatcher as SharedSmsCodeActionDispatcher
import io.github.magisk317.smscode.verification.SmsCodePostParseCoordinator
import com.github.magisk317.smscode.xp.hook.code.action.impl.AutoInputAction
import com.github.magisk317.smscode.xp.hook.code.action.impl.CopyToClipboardAction
import com.github.magisk317.smscode.xp.hook.code.action.impl.NotifyAction
import com.github.magisk317.smscode.xp.hook.code.action.impl.OperateSmsAction
import com.github.magisk317.smscode.xp.hook.code.action.impl.RecordSmsAction
import com.github.magisk317.smscode.xp.hook.code.action.impl.ToastAction
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
            autoInputScheduler = { scheduledExecutor, plugin, phone, message, delayMs, deduplicateEnabled ->
                scheduleAutoInput(scheduledExecutor, plugin, phone, message.raw, delayMs, deduplicateEnabled)
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
        pluginContext: Context,
        phoneContext: Context,
        smsMsg: SmsMsg,
        eventId: String,
        plan: SmsCodePostParseCoordinator.ObservedSmsPlan,
    ) {
        SharedSmsCodeActionDispatcher.dispatchObservedSmsActions(
            pluginContext = pluginContext,
            phoneContext = phoneContext,
            smsMsg = smsMsg.toVerificationMessage(),
            eventId = eventId,
            plan = plan,
            autoInputRunner = { plugin, phone, message, deduplicateEnabled ->
                runAutoInputNow(plugin, phone, message.raw, deduplicateEnabled)
            },
            recordRunner = { plugin, phone, message, recordEventId, deduplicateEnabled ->
                runRecordNow(plugin, phone, message.raw, recordEventId, deduplicateEnabled)
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
    ) {
        AutoInputAction(
            pluginContext = pluginContext,
            phoneContext = phoneContext,
            smsMsg = smsMsg,
            deduplicateEnabled = deduplicateEnabled,
        ).call()
    }

    private fun scheduleAutoInput(
        executor: ScheduledExecutorService,
        pluginContext: Context,
        phoneContext: Context,
        smsMsg: SmsMsg,
        delayMs: Long,
        deduplicateEnabled: Boolean,
    ) {
        executor.schedule(
            AutoInputAction(
                pluginContext = pluginContext,
                phoneContext = phoneContext,
                smsMsg = smsMsg,
                deduplicateEnabled = deduplicateEnabled,
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
}

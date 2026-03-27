package com.github.magisk317.smscode.xp.hook.code

import android.content.Context
import android.os.Handler
import com.github.magisk317.smscode.data.db.entity.SmsMsg
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
        dispatchUiActions(uiHandler, pluginContext, phoneContext, smsMsg, plan.uiPlan)

        plan.autoInputDelayMs?.let { delayMs ->
            scheduleAutoInput(
                executor,
                pluginContext,
                phoneContext,
                smsMsg,
                delayMs,
                plan.deduplicateSmsEnabled,
            )
        }

        plan.notificationPlan?.let { notificationPlan ->
            scheduleNotification(
                executor,
                pluginContext,
                phoneContext,
                smsMsg,
                notificationPlan,
            )
        }

        if (plan.shouldRecord) {
            scheduleRecord(
                executor,
                pluginContext,
                phoneContext,
                smsMsg,
                eventId,
                plan.deduplicateSmsEnabled,
            )
        }

        scheduleOperateSmsActions(
            executor,
            pluginContext,
            phoneContext,
            smsMsg,
            plan.operateSmsDelays,
        )
    }

    fun dispatchObservedSmsActions(
        pluginContext: Context,
        phoneContext: Context,
        smsMsg: SmsMsg,
        eventId: String,
        plan: SmsCodePostParseCoordinator.ObservedSmsPlan,
    ) {
        if (plan.autoInputEnabled) {
            runAutoInputNow(
                pluginContext,
                phoneContext,
                smsMsg,
                plan.deduplicateSmsEnabled,
            )
        }
        if (plan.shouldRecord) {
            runRecordNow(
                pluginContext,
                phoneContext,
                smsMsg,
                eventId,
                plan.deduplicateSmsEnabled,
            )
        }
    }

    private fun dispatchUiActions(
        uiHandler: Handler,
        pluginContext: Context,
        phoneContext: Context,
        smsMsg: SmsMsg,
        uiPlan: SmsCodePostParseCoordinator.UiPlan,
    ) {
        if (uiPlan.copyToClipboardEnabled) {
            uiHandler.post(CopyToClipboardAction(pluginContext, phoneContext, smsMsg))
        }
        if (uiPlan.showToast) {
            uiHandler.post(ToastAction(pluginContext, phoneContext, smsMsg))
        }
    }

    private fun runAutoInputNow(
        pluginContext: Context,
        phoneContext: Context,
        smsMsg: SmsMsg,
        deduplicateEnabled: Boolean,
    ) {
        AutoInputAction(pluginContext, phoneContext, smsMsg).call()
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
            AutoInputAction(pluginContext, phoneContext, smsMsg),
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
        RecordSmsAction(pluginContext, phoneContext, smsMsg, eventId).call()
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
            RecordSmsAction(pluginContext, phoneContext, smsMsg, eventId),
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
            NotifyAction(pluginContext, phoneContext, smsMsg),
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

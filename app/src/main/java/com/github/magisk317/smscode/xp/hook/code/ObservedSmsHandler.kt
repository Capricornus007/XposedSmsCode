package com.github.magisk317.smscode.xp.hook.code

import android.content.Context
import com.github.magisk317.smscode.runtime.RuntimePrefsFacade as PrefsReader
import io.github.magisk317.smscode.runtime.common.utils.SharedRuntimeGate
import com.github.magisk317.smscode.common.utils.SmsCodeUtils
import com.github.magisk317.smscode.data.db.entity.SmsMsg
import com.github.magisk317.smscode.runtime.RuntimeStorageFacade
import io.github.magisk317.smscode.verification.ObservedInboxScanRecord
import io.github.magisk317.smscode.verification.ObservedSmsHandler as SharedObservedSmsHandler
import io.github.magisk317.smscode.verification.SmsCodePostParseCoordinator
import io.github.magisk317.smscode.verification.SmsInboxObserverDecision
import com.github.magisk317.smscode.xp.helper.ModuleConflictArbiter
import java.util.concurrent.ScheduledExecutorService

internal class ObservedSmsHandler(
    private val pluginContext: Context,
    private val phoneContext: Context,
    private val actionExecutor: ScheduledExecutorService? = null,
    private val settingsLoader: (Context) -> SmsCodePostParseCoordinator.Settings = { context ->
        SmsCodePostParseCoordinator.loadSettings(SmsCodeVerificationPrefs(context))
    },
    private val planFactory: (SmsCodePostParseCoordinator.Settings) -> SmsCodePostParseCoordinator.ObservedSmsPlan =
        SmsCodePostParseCoordinator::createObservedSmsPlan,
    private val moduleEnabledReader: (Context) -> Boolean = PrefsReader::isEnabled,
    private val conflictSuppressor: (Context, String) -> Boolean = { context, source ->
        ModuleConflictArbiter.shouldSuppressByRelay(context, source)
    },
    private val sharedGateClaimer: (Context, String, String, Long, Int) -> SharedRuntimeGate.ClaimResult =
        { context, fileName, key, windowMs, maxEntries ->
            SharedRuntimeGate.claimWithinWindow(
                context = context,
                fileName = fileName,
                key = key,
                windowMs = windowMs,
                maxEntries = maxEntries,
            )
        },
    private val roleStateLogger: (String) -> Unit = {},
    private val duplicateChecker: ((SmsCodePostParseCoordinator.Settings, String, String, Long) -> Boolean)? = null,
    private val smsEnricher: (Context, String, String, Long, String) -> VerificationSmsMsg = { context, sender, body, date, code ->
        val (company, packageName) = resolveCompanyAndPackage(context, body)
        SmsMsg(
            sender = sender,
            body = body,
            date = if (date > 0) date else System.currentTimeMillis(),
            company = company,
            smsCode = code,
            packageName = packageName,
            msgType = SmsMsg.MSG_TYPE_SMS,
        ).toVerificationMessage()
    },
    private val dispatcher: (
        Context,
        Context,
        VerificationSmsMsg,
        String,
        SmsCodePostParseCoordinator.ObservedSmsPlan,
    ) -> Unit = { resolvedPluginContext, resolvedPhoneContext, smsMsg, eventId, plan ->
        SmsCodeActionDispatcher.dispatchObservedSmsActions(
            executor = actionExecutor,
            pluginContext = resolvedPluginContext,
            phoneContext = resolvedPhoneContext,
            smsMsg = smsMsg.raw,
            eventId = eventId,
            plan = plan,
        )
    },
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
) {
    data class Outcome(
        val eventId: String,
        val decision: SmsInboxObserverDecision.Decision,
        val dispatched: Boolean,
    )

    private val delegate = SharedObservedSmsHandler(
        pluginContext = pluginContext,
        phoneContext = phoneContext,
        settingsLoader = settingsLoader,
        planFactory = planFactory,
        moduleEnabledReader = moduleEnabledReader,
        conflictSuppressor = conflictSuppressor,
        sharedGateClaimer = { context, fileName, key, windowMs, maxEntries ->
            sharedGateClaimer(context, fileName, key, windowMs, maxEntries).toShared()
        },
        roleStateLogger = roleStateLogger,
        duplicateChecker = { settings, sender, body, date ->
            (duplicateChecker ?: ::defaultDuplicateCheck)(settings, sender, body, date)
        },
        smsEnricher = smsEnricher,
        dispatcher = dispatcher,
        currentTimeMillis = currentTimeMillis,
    )

    fun handle(record: ObservedInboxScanRecord): Outcome {
        val outcome = delegate.handle(record)
        return Outcome(
            eventId = outcome.eventId,
            decision = outcome.decision,
            dispatched = outcome.dispatched,
        )
    }

    private fun defaultDuplicateCheck(
        settings: SmsCodePostParseCoordinator.Settings,
        sender: String,
        body: String,
        date: Long,
    ): Boolean {
        if (!settings.deduplicateSmsEnabled) {
            return false
        }
        val timestamp = if (date > 0) date else currentTimeMillis()
        return runCatching {
            RuntimeStorageFacade.dbManager(pluginContext).querySmsMsgByFingerprint(sender, body, timestamp) != null
        }.getOrDefault(false)
    }

    private fun SharedRuntimeGate.ClaimResult.toShared(): SharedObservedSmsHandler.ClaimResult {
        return SharedObservedSmsHandler.ClaimResult(
            claimed = claimed,
            ageMs = ageMs,
        )
    }

    private companion object {
        private fun resolveCompanyAndPackage(
            phoneContext: Context,
            body: String,
        ): Pair<String, String?> {
            val companyCandidates = SmsCodeUtils.parseCompanyCandidates(body)
                .map { it.trim().trim('【', '】', '[', ']') }
                .filter { it.isNotBlank() }
            var company = SmsCodeUtils.parseCompany(body)
                .trim()
                .trim('【', '】', '[', ']')
            var resolvedPackage: String? = null
            for (candidate in companyCandidates) {
                val pkg = SmsCodeUtils.findPackageNameByLabel(phoneContext, candidate)
                if (!pkg.isNullOrBlank()) {
                    company = candidate
                    resolvedPackage = pkg
                    break
                }
            }
            if (resolvedPackage.isNullOrBlank()) {
                resolvedPackage = SmsCodeUtils.findPackageNameByLabel(phoneContext, company)
            }
            return company to resolvedPackage
        }
    }
}

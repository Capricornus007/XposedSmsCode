package com.github.magisk317.smscode.xp.hook.code

import android.content.Context
import com.github.magisk317.smscode.common.utils.PrefsReader
import com.github.magisk317.smscode.common.utils.SharedRuntimeGate
import com.github.magisk317.smscode.common.utils.SmsCodeUtils
import com.github.magisk317.smscode.data.db.DBManager
import com.github.magisk317.smscode.data.db.entity.SmsMsg
import io.github.magisk317.smscode.verification.ObservedInboxScanRecord
import io.github.magisk317.smscode.verification.SmsCodePostParseCoordinator
import io.github.magisk317.smscode.verification.SmsInboxObserverDecision
import io.github.magisk317.smscode.verification.SmsMessageDedupKeys
import com.github.magisk317.smscode.xp.helper.ModuleConflictArbiter
import io.github.magisk317.smscode.xposed.utils.XLog

internal class ObservedSmsHandler(
    private val pluginContext: Context,
    private val phoneContext: Context,
    private val settingsLoader: (Context) -> SmsCodePostParseCoordinator.Settings = { context ->
        SmsCodePostParseCoordinator.loadSettings(SmsCodeVerificationPrefs(context))
    },
    private val planFactory: (SmsCodePostParseCoordinator.Settings) -> SmsCodePostParseCoordinator.ObservedSmsPlan =
        SmsCodePostParseCoordinator::createObservedSmsPlan,
    private val moduleEnabledReader: (Context) -> Boolean = PrefsReader::isEnabled,
    private val conflictSuppressor: (Context, String) -> Boolean = { context, source ->
        ModuleConflictArbiter.shouldSuppressByRelay(context, source)
    },
    private val roleStateLogger: (String) -> Unit = {},
    private val duplicateChecker: ((SmsCodePostParseCoordinator.Settings, String, String, Long) -> Boolean)? = null,
    private val smsEnricher: (Context, String, String, Long, String) -> SmsMsg = { context, sender, body, date, code ->
        val (company, packageName) = resolveCompanyAndPackage(context, body)
        SmsMsg(
            sender = sender,
            body = body,
            date = if (date > 0) date else System.currentTimeMillis(),
            company = company,
            smsCode = code,
            packageName = packageName,
            msgType = SmsMsg.MSG_TYPE_SMS,
        )
    },
    private val dispatcher: (
        Context,
        Context,
        SmsMsg,
        String,
        SmsCodePostParseCoordinator.ObservedSmsPlan,
    ) -> Unit = { resolvedPluginContext, resolvedPhoneContext, smsMsg, eventId, plan ->
        SmsCodeActionDispatcher.dispatchObservedSmsActions(
            pluginContext = resolvedPluginContext,
            phoneContext = resolvedPhoneContext,
            smsMsg = smsMsg,
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

    fun handle(record: ObservedInboxScanRecord): Outcome {
        val settings = settingsLoader(pluginContext)
        val plan = planFactory(settings)
        val eventId = buildObservedEventId(record.smsId, record.date)
        val decision = SmsInboxObserverDecision.evaluate(
            moduleEnabled = moduleEnabledReader(pluginContext),
            suppressedByRelay = conflictSuppressor(phoneContext, OBSERVER_CONFLICT_SOURCE),
            duplicated = (duplicateChecker ?: ::defaultDuplicateCheck)(
                settings,
                record.sender,
                record.body,
                record.date,
            ),
            autoInputEnabled = plan.autoInputEnabled,
            shouldRecord = plan.shouldRecord,
            deduplicateSmsEnabled = plan.deduplicateSmsEnabled,
        )

        when (decision.skipReason) {
            SmsInboxObserverDecision.SkipReason.CONFLICT_SUPPRESSED -> {
                XLog.w("Diag observer conflict skip: event_id=%s sms_id=%d", eventId, record.smsId)
                return Outcome(eventId = eventId, decision = decision, dispatched = false)
            }

            SmsInboxObserverDecision.SkipReason.MODULE_DISABLED -> {
                XLog.w("Diag observer skip: module disabled event_id=%s", eventId)
                return Outcome(eventId = eventId, decision = decision, dispatched = false)
            }

            SmsInboxObserverDecision.SkipReason.DUPLICATED -> {
                XLog.w("Diag observer duplicate skip: event_id=%s", eventId)
                return Outcome(eventId = eventId, decision = decision, dispatched = false)
            }

            null -> Unit
        }

        if (record.read) {
            XLog.w(
                "Diag observer skip: sms already read event_id=%s sms_id=%d uri=%s",
                eventId,
                record.smsId,
                record.triggerUri,
            )
            return Outcome(eventId = eventId, decision = decision, dispatched = false)
        }
        if (!claimObservedSms(eventId, record)) {
            return Outcome(eventId = eventId, decision = decision, dispatched = false)
        }

        roleStateLogger(eventId)

        val smsMsg = smsEnricher(
            phoneContext,
            record.sender,
            record.body,
            record.date,
            record.code,
        )

        if (decision.autoInputEnabled) {
            XLog.w(
                "Diag observer auto-input: event_id=%s sender_hash=%s read=%s uri=%s",
                eventId,
                senderHash(record.sender),
                record.read,
                record.triggerUri,
            )
        } else {
            XLog.w("Diag observer auto-input disabled: event_id=%s", eventId)
        }

        decision.recordSkipReason?.let { reason ->
            XLog.w("Diag observer record skipped: reason=%s event_id=%s", reason.wireValue, eventId)
        }

        dispatcher(pluginContext, phoneContext, smsMsg, eventId, plan)
        return Outcome(eventId = eventId, decision = decision, dispatched = true)
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
            DBManager.get(pluginContext).querySmsMsgByFingerprint(sender, body, timestamp) != null
        }.getOrDefault(false)
    }

    private fun buildObservedEventId(smsId: Long, date: Long): String {
        val ts = if (date > 0) date else currentTimeMillis()
        return "sms_observed_${ts.toString(EVENT_ID_RADIX)}_${smsId.toString(EVENT_ID_RADIX)}"
    }

    private fun claimObservedSms(
        eventId: String,
        record: ObservedInboxScanRecord,
    ): Boolean {
        val key = SmsMessageDedupKeys.buildObservedKey(
            smsId = record.smsId,
            date = record.date,
            sender = record.sender,
            body = record.body,
            code = record.code,
        )
        val claim = SharedRuntimeGate.claimWithinWindow(
            context = pluginContext,
            fileName = SHARED_OBSERVED_SMS_FILE_NAME,
            key = key,
            windowMs = OBSERVED_SMS_DEDUP_WINDOW_MS,
            maxEntries = MAX_TRACKED_SMS_IDS,
        )
        if (claim.claimed) {
            return true
        }
        XLog.w(
            "Diag observer dedup skip: event_id=%s key=%s ageMs=%d",
            eventId,
            key,
            claim.ageMs ?: -1L,
        )
        return false
    }

    private fun senderHash(sender: String): String {
        if (sender.isBlank()) return "none"
        return Integer.toHexString(sender.hashCode())
    }

    private companion object {
        private const val OBSERVER_CONFLICT_SOURCE = "SmsInboxObserver#handleObservedCode"
        private const val SHARED_OBSERVED_SMS_FILE_NAME = "observed_sms_dedup"
        private const val OBSERVED_SMS_DEDUP_WINDOW_MS = 5_000L
        private const val MAX_TRACKED_SMS_IDS = 128
        private const val EVENT_ID_RADIX = 36

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

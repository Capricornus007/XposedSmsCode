package com.github.magisk317.smscode.migration.model

import com.github.magisk317.smscode.data.db.entity.AppInfo
import com.github.magisk317.smscode.data.db.entity.NotifyRouteRule
import com.github.magisk317.smscode.data.db.entity.SmsCodeRule
import com.github.magisk317.smscode.data.db.entity.SmsMsg
import com.github.magisk317.smscode.feature.backup.BackupRule
import com.github.magisk317.smscode.feature.backup.BackupSmsRecord
import com.github.magisk317.smscode.forwarder.entity.ForwardFilterRule
import com.github.magisk317.smscode.forwarder.entity.Rule
import com.github.magisk317.smscode.forwarder.entity.Sender
import java.util.Date
import kotlinx.serialization.Serializable

@Serializable
data class MigrationMeta(
    val schemaVersion: Int,
    val sourcePackage: String,
    val sourceVersion: String,
    val exportedAt: Long,
)

@Serializable
data class MigrationExportPayload(
    val schemaVersion: Int,
    val sourcePackage: String,
    val sourceVersion: String,
    val exportedAt: Long,
    val preferences: Map<String, String?> = emptyMap(),
    val codeRules: List<BackupRule> = emptyList(),
    val records: List<BackupSmsRecord> = emptyList(),
    val senders: List<MigrationSenderDto> = emptyList(),
    val forwardRules: List<MigrationRuleDto> = emptyList(),
    val forwardFilterRules: List<MigrationForwardFilterRuleDto> = emptyList(),
    val notifyRouteRules: List<MigrationNotifyRouteRuleDto> = emptyList(),
    val appInfos: List<AppInfo> = emptyList(),
)

@Serializable
data class MigrationSenderDto(
    val id: Long,
    val type: Int,
    val name: String,
    val jsonSetting: String,
    val status: Int,
    val time: Long,
    val receiveCode: Int,
    val receiveNonCode: Int,
    val receiveAppNotify: Int,
    val receiveCallNotify: Int,
)

@Serializable
data class MigrationRuleDto(
    val id: Long,
    val type: String,
    val filed: String,
    val check: String,
    val value: String,
    val senderId: Long,
    val smsTemplate: String,
    val regexReplace: String,
    val simSlot: String,
    val status: Int,
    val time: Long,
    val senderListIds: List<Long> = emptyList(),
    val senderLogic: String,
    val silentPeriodStart: Int,
    val silentPeriodEnd: Int,
    val silentDayOfWeek: String,
    val title: String,
)

@Serializable
data class MigrationForwardFilterRuleDto(
    val id: Long,
    val msgType: String,
    val scopeType: String,
    val scopeKey: String,
    val senderId: Long,
    val policy: String,
    val matchMode: String,
    val pattern: String,
    val enabled: Int,
    val updateTime: Long,
)

@Serializable
data class MigrationNotifyRouteRuleDto(
    val id: Long,
    val scope: Int,
    val packageName: String,
    val senderId: Long,
    val updateTime: Long,
)

fun Sender.toMigrationDto(): MigrationSenderDto = MigrationSenderDto(
    id = id,
    type = type,
    name = name,
    jsonSetting = jsonSetting,
    status = status,
    time = time.time,
    receiveCode = receiveCode,
    receiveNonCode = receiveNonCode,
    receiveAppNotify = receiveAppNotify,
    receiveCallNotify = receiveCallNotify,
)

fun Rule.toMigrationDto(): MigrationRuleDto = MigrationRuleDto(
    id = id,
    type = type,
    filed = filed,
    check = check,
    value = value,
    senderId = senderId,
    smsTemplate = smsTemplate,
    regexReplace = regexReplace,
    simSlot = simSlot,
    status = status,
    time = time.time,
    senderListIds = senderList.map { it.id },
    senderLogic = senderLogic,
    silentPeriodStart = silentPeriodStart,
    silentPeriodEnd = silentPeriodEnd,
    silentDayOfWeek = silentDayOfWeek,
    title = title,
)

fun ForwardFilterRule.toMigrationDto(): MigrationForwardFilterRuleDto = MigrationForwardFilterRuleDto(
    id = id,
    msgType = msgType,
    scopeType = scopeType,
    scopeKey = scopeKey,
    senderId = senderId,
    policy = policy,
    matchMode = matchMode,
    pattern = pattern,
    enabled = enabled,
    updateTime = updateTime,
)

fun NotifyRouteRule.toMigrationDto(): MigrationNotifyRouteRuleDto = MigrationNotifyRouteRuleDto(
    id = id,
    scope = scope,
    packageName = packageName,
    senderId = senderId,
    updateTime = updateTime,
)

fun MigrationSenderDto.toEntity(): Sender = Sender(
    id = id,
    type = type,
    name = name,
    jsonSetting = jsonSetting,
    status = status,
    time = Date(time),
    receiveCode = receiveCode,
    receiveNonCode = receiveNonCode,
    receiveAppNotify = receiveAppNotify,
    receiveCallNotify = receiveCallNotify,
)

fun MigrationRuleDto.toEntity(senderMapById: Map<Long, Sender>): Rule = Rule(
    id = id,
    type = type,
    filed = filed,
    check = check,
    value = value,
    senderId = senderId,
    smsTemplate = smsTemplate,
    regexReplace = regexReplace,
    simSlot = simSlot,
    status = status,
    time = Date(time),
    senderList = senderListIds.mapNotNull { senderMapById[it] },
    senderLogic = senderLogic,
    silentPeriodStart = silentPeriodStart,
    silentPeriodEnd = silentPeriodEnd,
    silentDayOfWeek = silentDayOfWeek,
    title = title,
)

fun MigrationForwardFilterRuleDto.toEntity(): ForwardFilterRule = ForwardFilterRule(
    id = id,
    msgType = msgType,
    scopeType = scopeType,
    scopeKey = scopeKey,
    senderId = senderId,
    policy = policy,
    matchMode = matchMode,
    pattern = pattern,
    enabled = enabled,
    updateTime = updateTime,
)

fun MigrationNotifyRouteRuleDto.toEntity(): NotifyRouteRule = NotifyRouteRule(
    id = id,
    scope = scope,
    packageName = packageName,
    senderId = senderId,
    updateTime = updateTime,
)

fun SmsCodeRule.toBackupRule(): BackupRule = BackupRule(
    company = company,
    codeKeyword = codeKeyword,
    codeRegex = codeRegex,
)

fun SmsMsg.toBackupSmsRecord(): BackupSmsRecord = BackupSmsRecord(
    sender = sender,
    body = body,
    date = date,
    company = company,
    smsCode = smsCode,
    packageName = packageName,
    msgType = msgType,
    callType = callType,
    forwardStatus = forwardStatus,
    forwardTarget = forwardTarget,
    forwardMessage = forwardMessage,
    forwardTime = forwardTime,
)

fun BackupRule.toSmsCodeRule(): SmsCodeRule = SmsCodeRule(
    company = company,
    codeKeyword = codeKeyword,
    codeRegex = codeRegex,
)

fun BackupSmsRecord.toSmsMsg(): SmsMsg = SmsMsg(
    sender = sender,
    body = body,
    date = date,
    company = company,
    smsCode = smsCode,
    packageName = packageName,
    msgType = msgType,
    callType = callType,
    forwardStatus = forwardStatus,
    forwardTarget = forwardTarget,
    forwardMessage = forwardMessage,
    forwardTime = forwardTime,
)

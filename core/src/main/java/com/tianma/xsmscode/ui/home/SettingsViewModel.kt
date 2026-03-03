package com.tianma.xsmscode.ui.home

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.TextUtils
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tianma.xsmscode.core.BuildConfig
import com.tianma.xsmscode.common.constant.Const
import com.tianma.xsmscode.common.constant.PrefConst
import com.tianma.xsmscode.common.utils.*
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import android.content.Intent
import com.tianma.xsmscode.core.R
import com.tianma.xsmscode.data.db.DBManager
import com.tianma.xsmscode.feature.backup.BackupImportResult
import com.tianma.xsmscode.feature.backup.BackupManager
import com.tianma.xsmscode.feature.backup.BackupRule
import com.tianma.xsmscode.feature.backup.BackupSmsRecord
import com.tianma.xsmscode.feature.backup.ExportResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class SettingsEvent {
    data object ShowPrivacyPolicy : SettingsEvent()
    data object ShowAlipayPacket : SettingsEvent()
    data class SmsCodeTestResult(val code: String) : SettingsEvent()
    data object NavigateToRules : SettingsEvent()
    data object NavigateToRecords : SettingsEvent()
    data object StartPlayUpdate : SettingsEvent()
    data object StartGithubUpdateCheck : SettingsEvent()
    data class BackupResultEvent(val success: Boolean) : SettingsEvent()
    data class RestoreResultEvent(val result: BackupImportResult) : SettingsEvent()
    data class ImportDialogConfirm(val uri: android.net.Uri) : SettingsEvent()
}

fun resolvePreferredUpdateEvent(installedFromPlay: Boolean): SettingsEvent =
    if (installedFromPlay) SettingsEvent.StartPlayUpdate else SettingsEvent.StartGithubUpdateCheck

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val booleanPrefKeys = setOf(
        PrefConst.KEY_ENABLE,
        PrefConst.KEY_SHOW_LAUNCHER_ICON,
        PrefConst.KEY_SETTINGS_ACCORDION_MODE,
        PrefConst.KEY_SHOW_TOAST,
        PrefConst.KEY_COPY_TO_CLIPBOARD,
        PrefConst.KEY_ENABLE_AUTO_INPUT_CODE,
        PrefConst.KEY_BLOCK_SMS,
        PrefConst.KEY_DEDUPLICATE_SMS,
        PrefConst.KEY_ENABLE_SMS_BLACKLIST,
        PrefConst.KEY_SMS_BLACKLIST_ACTION_DELETE,
        PrefConst.KEY_SMS_BLACKLIST_ACTION_BLOCK,
        PrefConst.KEY_SHOW_CODE_NOTIFICATION,
        PrefConst.KEY_AUTO_CANCEL_CODE_NOTIFICATION,
        PrefConst.KEY_ENABLE_CODE_RECORDS,
        PrefConst.KEY_MARK_AS_READ,
        PrefConst.KEY_DELETE_SMS,
        PrefConst.KEY_KILL_ME,
        PrefConst.KEY_FORCE_STOP_RECOVERY,
        PrefConst.KEY_FORCE_STOP_RECOVERY_RELAUNCH_ONCE,
        PrefConst.KEY_VERBOSE_LOG_MODE,
        PrefConst.KEY_AUTO_UPDATE_ON_START,
        PrefConst.KEY_AUTO_UPDATE_WIFI_ONLY,
        PrefConst.KEY_ENABLE_AUTO_ENTER_CODE,
        PrefConst.KEY_FORWARD_COMMON_INCLUDE_TIME,
        PrefConst.KEY_FORWARD_COMMON_INCLUDE_SENDER,
        PrefConst.KEY_FORWARD_COMMON_INCLUDE_DEVICE_NAME,
        PrefConst.KEY_WEBUI_LAN_ACCESS,
        PrefConst.KEY_PRIVACY_POLICY_ACCEPTED,
        PrefConst.KEY_BACKUP_COMPAT_TIP_SHOWN,
    )

    private val intPrefKeys = setOf(
        PrefConst.KEY_CHOOSE_THEME,
        PrefConst.KEY_HAZE_BLUR_RADIUS,
        "local_version_code",
    )

    private val floatPrefKeys = setOf(
        PrefConst.KEY_HAZE_TINT_ALPHA,
    )

    private val _eventsFlow = MutableSharedFlow<SettingsEvent>(
        extraBufferCapacity = 10,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val eventsFlow = _eventsFlow.asSharedFlow()

    data class ThemeState(val mode: Int, val centerX: Float = -1f, val centerY: Float = -1f)

    private val _themeState = MutableStateFlow(ThemeState(0))
    val themeState: StateFlow<ThemeState> = _themeState.asStateFlow()

    val smsRecordCount: StateFlow<Long> = DBManager.get(application)
        .queryAllSmsMsgCountFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(Const.FLOW_STOP_TIMEOUT_MS),
            initialValue = 0L,
        )

    init {
        viewModelScope.launch {
            val mode = SPUtils.getThemeMode(getApplication())
            _themeState.value = ThemeState(mode)
        }
        viewModelScope.launch {
            AppPreferencesDataStore.syncToSharedPrefs(getApplication())
        }
    }

    fun setThemeMode(mode: Int, x: Float = -1f, y: Float = -1f) {
        viewModelScope.launch {
            SPUtils.setThemeMode(getApplication(), mode)
            _themeState.value = ThemeState(mode, x, y)
        }
    }

    override fun onCleared() {
        super.onCleared()
    }

    fun handleArguments(args: Bundle?) {
        if (args == null) return

        viewModelScope.launch {
            if (!SPUtils.isPrivacyPolicyAccepted(getApplication())) {
                _eventsFlow.tryEmit(SettingsEvent.ShowPrivacyPolicy)
            } else {
                val extraAction = args.getString(Const.EXTRA_ACTION)
                if (Const.ACTION_DONATE_BY_ALIPAY == extraAction) {
                    args.remove(Const.EXTRA_ACTION)
                    _eventsFlow.tryEmit(SettingsEvent.ShowAlipayPacket)
                } else if ("smscode_records" == extraAction) {
                    args.remove(Const.EXTRA_ACTION)
                    _eventsFlow.tryEmit(SettingsEvent.NavigateToRecords)
                } else if ("smscode_rules" == extraAction) {
                    args.remove(Const.EXTRA_ACTION)
                    _eventsFlow.tryEmit(SettingsEvent.NavigateToRules)
                }
            }
        }
    }

    fun pinShortcutToDesktop() {
        val context = getApplication<Application>()
        if (ShortcutManagerCompat.isRequestPinShortcutSupported(context)) {
            val intent = Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_MAIN
            }
            // 使用挂载了 CATEGORY_INFO 的主入口强行注册
            val mainActivity = android.content.ComponentName(context, MainActivity::class.java)
            val shortcut = ShortcutInfoCompat.Builder(context, "shortcut_main")
                .setShortLabel(context.getString(R.string.app_name))
                .setIcon(IconCompat.createWithResource(context, R.mipmap.ic_launcher))
                .setIntent(intent)
                .setActivity(mainActivity)
                .build()
            ShortcutManagerCompat.requestPinShortcut(context, shortcut, null)
        } else {
            android.widget.Toast.makeText(context, "当前系统不支持创建快捷方式", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    fun isLauncherIconVisible(): Boolean {
        val context = getApplication<Application>()
        val component = ComponentName(context, LauncherActivity::class.java)
        val pm = context.packageManager
        return when (pm.getComponentEnabledSetting(component)) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED,
            -> false

            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> runCatching {
                pm.getActivityInfo(component, 0).enabled
            }.getOrDefault(false)

            else -> false
        }
    }

    fun setLauncherIconVisible(visible: Boolean): Boolean {
        val context = getApplication<Application>()
        val component = ComponentName(context, LauncherActivity::class.java)
        val pm = context.packageManager
        val newState = if (visible) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        return runCatching {
            pm.setComponentEnabledSetting(
                component,
                newState,
                PackageManager.DONT_KILL_APP,
            )
            true
        }.onFailure {
            XLog.e("Failed to set launcher icon visible=$visible", it)
        }.getOrElse { false }
    }

    fun performSmsCodeTest(msgBody: String) {
        viewModelScope.launch {
            val code = try {
                withContext(Dispatchers.IO) {
                    if (TextUtils.isEmpty(msgBody)) {
                        ""
                    } else {
                        SmsCodeUtils.parseSmsCodeIfExists(getApplication(), msgBody)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                ""
            }
            _eventsFlow.tryEmit(SettingsEvent.SmsCodeTestResult(code))
        }
    }

    fun joinQQGroup() {
        PackageUtils.joinQQGroup(getApplication())
    }

    fun showSourceProject() {
        Utils.showWebPage(getApplication(), Const.PROJECT_SOURCE_CODE_URL)
    }

    fun setInternalFilesWritable() {
        StorageUtils.setFileWorldWritable(StorageUtils.getFilesDir(getApplication()), 1)
        AppPreferencesDataStore.ensureReadable(getApplication())
    }

    fun requestPreferredUpdate() {
        viewModelScope.launch {
            val event = resolvePreferredUpdateEvent(PackageUtils.isInstalledFromPlay(getApplication()))
            _eventsFlow.tryEmit(event)
        }
    }

    fun handleBackupArguments(uri: android.net.Uri?) {
        if (uri == null) return
        viewModelScope.launch {
            _eventsFlow.tryEmit(SettingsEvent.ImportDialogConfirm(uri))
        }
    }

    fun performBackup(
        uri: android.net.Uri,
        includeConfig: Boolean,
        includeRules: Boolean,
        includeRecords: Boolean,
        includeDatabase: Boolean,
    ) {
        viewModelScope.launch {
            val context = getApplication<Application>()
            try {
                XLog.i(
                    "Backup start: uri=%s includeConfig=%s includeRules=%s includeRecords=%s includeDatabase=%s",
                    uri.toString(),
                    includeConfig,
                    includeRules,
                    includeRecords,
                    includeDatabase,
                )
                val rules = if (includeRules) {
                    withContext(Dispatchers.IO) {
                        DBManager.get(context).queryAllSmsCodeRules()
                            .map { BackupRule(it.company, it.codeKeyword, it.codeRegex) }
                    }
                } else {
                    emptyList()
                }

                val records = if (includeRecords) {
                    withContext(Dispatchers.IO) {
                        DBManager.get(context).queryAllSmsMsg()
                            .map {
                                BackupSmsRecord(
                                    sender = it.sender,
                                    body = it.body,
                                    date = it.date,
                                    company = it.company,
                                    smsCode = it.smsCode,
                                    packageName = it.packageName,
                                    msgType = it.msgType,
                                    forwardStatus = it.forwardStatus,
                                    forwardTarget = it.forwardTarget,
                                    forwardMessage = it.forwardMessage,
                                    forwardTime = it.forwardTime,
                                )
                            }
                    }
                } else {
                    null
                }

                val prefs = if (includeConfig) {
                    withContext(Dispatchers.IO) {
                        ensureDataStoreLoaded(context)
                        val sharedPrefs = context.getSharedPreferences(
                            "xposed_prefs",
                            android.content.Context.MODE_PRIVATE,
                        )
                        val allPrefs = sharedPrefs.all
                        val map = HashMap<String, String?>()
                        for ((k, v) in allPrefs) {
                            map[k] = v?.toString()
                        }
                        map
                    }
                } else {
                    null
                }

                XLog.i(
                    "Backup payload prepared: rules=%d records=%d prefs=%d",
                    rules.size,
                    records?.size ?: 0,
                    prefs?.size ?: 0,
                )
                val result = withContext(Dispatchers.IO) {
                    BackupManager.exportBackup(
                        context = context,
                        uri = uri,
                        ruleList = rules,
                        preferences = prefs,
                        records = records,
                        appVersion = BuildConfig.VERSION_NAME,
                        includeDatabase = includeDatabase,
                    )
                }
                XLog.i("Backup finished: result=%s", result.name)
                _eventsFlow.tryEmit(SettingsEvent.BackupResultEvent(result == ExportResult.SUCCESS))
            } catch (e: Exception) {
                XLog.e("Backup failed", e)
                _eventsFlow.tryEmit(SettingsEvent.BackupResultEvent(false))
            }
        }
    }

    fun performRestore(
        uri: android.net.Uri,
        restoreConfig: Boolean,
        restoreRules: Boolean,
        restoreRecords: Boolean,
        restoreDatabase: Boolean,
    ) {
        viewModelScope.launch {
            val context = getApplication<Application>()
            try {
                XLog.i(
                    "Restore start: uri=%s restoreConfig=%s restoreRules=%s restoreRecords=%s restoreDatabase=%s",
                    uri.toString(),
                    restoreConfig,
                    restoreRules,
                    restoreRecords,
                    restoreDatabase,
                )
                val importResult = withContext(Dispatchers.IO) {
                    BackupManager.importRuleList(context, uri, BuildConfig.VERSION_NAME)
                }
                XLog.i(
                    "Restore import result=%s rules=%d records=%d prefs=%d warning=%s",
                    importResult.result.name,
                    importResult.rules.size,
                    importResult.records?.size ?: 0,
                    importResult.preferences?.size ?: 0,
                    importResult.warning?.name ?: "none",
                )

                if (importResult.result == com.tianma.xsmscode.feature.backup.ImportResult.SUCCESS) {
                    withContext(Dispatchers.IO) {
                        if (restoreDatabase) {
                            val restored = BackupManager.restoreDatabaseFromBackup(context, uri)
                            if (!restored) {
                                throw IllegalStateException("Restore database failed: backup zip has no database files")
                            }
                            if (restoreRules || restoreRecords) {
                                XLog.i(
                                    "Restore database enabled: skip logical restore rules=%s records=%s",
                                    restoreRules,
                                    restoreRecords,
                                )
                            }
                        } else {
                            if (restoreRules) restoreRules(context, importResult.rules)
                            if (restoreRecords) restoreRecords(context, importResult.records.orEmpty())
                        }
                        if (restoreConfig) restorePreferences(context, importResult.preferences.orEmpty())
                    }
                    XLog.i("Restore apply finished")
                }
                _eventsFlow.tryEmit(SettingsEvent.RestoreResultEvent(importResult))
            } catch (e: Exception) {
                XLog.e("Restore failed", e)
                // Return failed event
                _eventsFlow.tryEmit(
                    SettingsEvent.RestoreResultEvent(
                        BackupImportResult(com.tianma.xsmscode.feature.backup.ImportResult.READ_FAILED),
                    ),
                )
            }
        }
    }

    private suspend fun restoreRules(context: Context, rules: List<BackupRule>) {
        if (rules.isEmpty()) return
        val dbManager = DBManager.get(context)
        val entities = rules.map {
            com.tianma.xsmscode.data.db.entity.SmsCodeRule(it.company, it.codeKeyword, it.codeRegex)
        }
        dbManager.addSmsCodeRules(entities)
    }

    private suspend fun restoreRecords(context: Context, records: List<BackupSmsRecord>) {
        val dbManager = DBManager.get(context)
        if (records.isEmpty()) {
            XLog.w("Restore records skipped: empty list")
            return
        }
        val beforeCount = dbManager.queryAllSmsMsg().size
        val entities = records.map {
            com.tianma.xsmscode.data.db.entity.SmsMsg(
                sender = it.sender,
                body = it.body,
                date = it.date,
                company = it.company,
                smsCode = it.smsCode,
                packageName = it.packageName,
                msgType = it.msgType,
                forwardStatus = it.forwardStatus,
                forwardTarget = it.forwardTarget,
                forwardMessage = it.forwardMessage,
                forwardTime = it.forwardTime,
            )
        }
        dbManager.addSmsMsgList(entities)
        val afterCount = dbManager.queryAllSmsMsg().size
        XLog.i(
            "Restore records finished: requested=%d before=%d after=%d delta=%d",
            records.size,
            beforeCount,
            afterCount,
            afterCount - beforeCount,
        )
    }

    private suspend fun restorePreferences(context: Context, prefsMap: Map<String, String?>) {
        if (prefsMap.isEmpty()) return
        for ((k, v) in prefsMap) {
            if (v == null) continue
            val strV = v
            when {
                booleanPrefKeys.contains(k) -> {
                    val normalized = strV.trim().lowercase()
                    val boolValue = when (normalized) {
                        "true", "1" -> true
                        "false", "0" -> false
                        else -> null
                    }
                    if (boolValue != null) {
                        AppPreferencesDataStore.setBoolean(context, k, boolValue)
                        if (k == PrefConst.KEY_SHOW_LAUNCHER_ICON) {
                            setLauncherIconVisible(boolValue)
                        }
                    }
                }

                intPrefKeys.contains(k) -> {
                    val intValue = strV.trim().toIntOrNull()
                    if (intValue != null) {
                        AppPreferencesDataStore.setInt(context, k, intValue)
                    }
                }

                floatPrefKeys.contains(k) -> {
                    val floatValue = strV.trim().toFloatOrNull()
                    if (floatValue != null) {
                        AppPreferencesDataStore.setFloat(context, k, floatValue)
                    }
                }

                else -> {
                    AppPreferencesDataStore.setString(context, k, strV)
                }
            }
        }
    }

    private suspend fun ensureDataStoreLoaded(_context: android.content.Context) {
        // Trigger read to ensure in-memory cache if needed; keep no-op for now.
    }
}

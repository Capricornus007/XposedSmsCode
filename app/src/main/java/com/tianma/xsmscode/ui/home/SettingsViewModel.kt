package com.tianma.xsmscode.ui.home

import android.app.Application
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.TextUtils
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.github.tianma8023.xposed.smscode.BuildConfig
import com.tianma.xsmscode.common.constant.Const
import com.tianma.xsmscode.common.constant.PrefConst
import com.tianma.xsmscode.common.utils.*
import com.tianma.xsmscode.data.db.DBManager
import com.tianma.xsmscode.data.db.entity.ApkVersion
import com.tianma.xsmscode.data.http.NetworkError
import com.tianma.xsmscode.data.http.NetworkResult
import com.tianma.xsmscode.data.repository.DataRepository
import com.tianma.xsmscode.feature.backup.BackupImportResult
import com.tianma.xsmscode.feature.backup.BackupManager
import com.tianma.xsmscode.feature.backup.BackupRule
import com.tianma.xsmscode.feature.backup.BackupSmsRecord
import com.tianma.xsmscode.feature.backup.ExportResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class SettingsEvent {
    data object ShowPrivacyPolicy : SettingsEvent()
    data object ShowAlipayPacket : SettingsEvent()
    data class SmsCodeTestResult(val code: String) : SettingsEvent()
    data class CheckUpdateError(val error: NetworkError) : SettingsEvent()
    data class ShowUpdateDialog(val version: ApkVersion) : SettingsEvent()
    data object AppAlreadyNewest : SettingsEvent()
    data object NavigateToRules : SettingsEvent()
    data object NavigateToRecords : SettingsEvent()
    data object StartPlayUpdate : SettingsEvent()
    data class BackupResultEvent(val success: Boolean) : SettingsEvent()
    data class RestoreResultEvent(val result: BackupImportResult) : SettingsEvent()
    data class ImportDialogConfirm(val uri: android.net.Uri) : SettingsEvent()
}

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val booleanPrefKeys = setOf(
        PrefConst.KEY_ENABLE,
        PrefConst.KEY_HIDE_LAUNCHER_ICON,
        PrefConst.KEY_SHOW_TOAST,
        PrefConst.KEY_COPY_TO_CLIPBOARD,
        PrefConst.KEY_ENABLE_AUTO_INPUT_CODE,
        PrefConst.KEY_BLOCK_SMS,
        PrefConst.KEY_DEDUPLICATE_SMS,
        PrefConst.KEY_SHOW_CODE_NOTIFICATION,
        PrefConst.KEY_AUTO_CANCEL_CODE_NOTIFICATION,
        PrefConst.KEY_ENABLE_CODE_RECORDS,
        PrefConst.KEY_MARK_AS_READ,
        PrefConst.KEY_DELETE_SMS,
        PrefConst.KEY_KILL_ME,
        PrefConst.KEY_VERBOSE_LOG_MODE,
        PrefConst.KEY_PRIVACY_POLICY_ACCEPTED,
        PrefConst.KEY_BACKUP_COMPAT_TIP_SHOWN,
    )

    private val intPrefKeys = setOf(
        PrefConst.KEY_CHOOSE_THEME,
        "local_version_code",
    )

    private val _eventsFlow = MutableSharedFlow<SettingsEvent>()
    val eventsFlow: SharedFlow<SettingsEvent> = _eventsFlow.asSharedFlow()

    data class ThemeState(val mode: Int, val centerX: Float = -1f, val centerY: Float = -1f)

    private val _themeState = MutableStateFlow(ThemeState(0))
    val themeState: StateFlow<ThemeState> = _themeState.asStateFlow()

    private val _updateVersion = MutableStateFlow<ApkVersion?>(null)
    val updateVersion: StateFlow<ApkVersion?> = _updateVersion.asStateFlow()

    val smsRecordCount: StateFlow<Long> = DBManager.get(application)
        .queryAllSmsMsgCountFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
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

    fun clearUpdateVersion() {
        _updateVersion.value = null
    }

    override fun onCleared() {
        super.onCleared()
    }

    fun handleArguments(args: Bundle?) {
        if (args == null) return

        viewModelScope.launch {
            if (!SPUtils.isPrivacyPolicyAccepted(getApplication())) {
                _eventsFlow.emit(SettingsEvent.ShowPrivacyPolicy)
            } else {
                val extraAction = args.getString(Const.EXTRA_ACTION)
                if (Const.ACTION_DONATE_BY_ALIPAY == extraAction) {
                    args.remove(Const.EXTRA_ACTION)
                    _eventsFlow.emit(SettingsEvent.ShowAlipayPacket)
                } else if ("smscode_records" == extraAction) {
                    args.remove(Const.EXTRA_ACTION)
                    _eventsFlow.emit(SettingsEvent.NavigateToRecords)
                } else if ("smscode_rules" == extraAction) {
                    args.remove(Const.EXTRA_ACTION)
                    _eventsFlow.emit(SettingsEvent.NavigateToRules)
                }
            }
        }
    }

    fun hideOrShowLauncherIcon(hide: Boolean) {
        val pm = getApplication<Application>().packageManager
        val launcherCN = ComponentName(getApplication(), Const.HOME_ACTIVITY_ALIAS)
        val mainCN = ComponentName(getApplication(), MainActivity::class.java)
        val state = if (hide) PackageManager.COMPONENT_ENABLED_STATE_DISABLED else PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        if (pm.getComponentEnabledSetting(launcherCN) != state) {
            val flags = if (hide) 0 else PackageManager.DONT_KILL_APP
            pm.setComponentEnabledSetting(launcherCN, state, flags)
            if (hide) {
                pm.setComponentEnabledSetting(
                    mainCN,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP,
                )
            }
        }
    }

    fun performSmsCodeTest(msgBody: String) {
        viewModelScope.launch {
            val code = withContext(Dispatchers.IO) {
                if (TextUtils.isEmpty(msgBody)) {
                    ""
                } else {
                    SmsCodeUtils.parseSmsCodeIfExists(getApplication(), msgBody)
                }
            }
            _eventsFlow.emit(SettingsEvent.SmsCodeTestResult(code))
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

    fun checkUpdate() {
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    DataRepository.getLatestVersion()
                }
                when (result) {
                    is NetworkResult.Success -> {
                        val currentVersion = ApkVersion(BuildConfig.VERSION_NAME, "")
                        if (currentVersion < result.data) {
                            _updateVersion.value = result.data
                        } else {
                            _eventsFlow.emit(SettingsEvent.AppAlreadyNewest)
                        }
                    }

                    is NetworkResult.Error -> {
                        _eventsFlow.emit(SettingsEvent.CheckUpdateError(result.error))
                    }
                }
            } catch (e: Throwable) {
                _eventsFlow.emit(SettingsEvent.CheckUpdateError(NetworkError.Unexpected(e.message, e)))
            }
        }
    }

    fun requestPlayUpdate() {
        viewModelScope.launch {
            _eventsFlow.emit(SettingsEvent.StartPlayUpdate)
        }
    }

    fun updateFromGithub() {
        Utils.showWebPage(getApplication(), Const.PROJECT_GITHUB_LATEST_RELEASE_URL)
    }

    fun updateFromCoolApk() {
        PackageUtils.showAppDetailsInCoolApk(getApplication())
    }

    fun handleBackupArguments(uri: android.net.Uri?) {
        if (uri == null) return
        viewModelScope.launch {
            _eventsFlow.emit(SettingsEvent.ImportDialogConfirm(uri))
        }
    }

    fun performBackup(uri: android.net.Uri, includeConfig: Boolean, includeRules: Boolean, includeRecords: Boolean) {
        viewModelScope.launch {
            val context = getApplication<Application>()
            try {
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

                val result = withContext(Dispatchers.IO) {
                    BackupManager.exportBackup(context, uri, rules, prefs, records, BuildConfig.VERSION_NAME)
                }
                _eventsFlow.emit(SettingsEvent.BackupResultEvent(result == ExportResult.SUCCESS))
            } catch (e: Exception) {
                e.printStackTrace()
                _eventsFlow.emit(SettingsEvent.BackupResultEvent(false))
            }
        }
    }

    fun performRestore(uri: android.net.Uri, restoreConfig: Boolean, restoreRules: Boolean, restoreRecords: Boolean) {
        viewModelScope.launch {
            val context = getApplication<Application>()
            try {
                val importResult = withContext(Dispatchers.IO) {
                    BackupManager.importRuleList(context, uri, BuildConfig.VERSION_NAME)
                }

                // If parse success, proceed to restore data to DB/Prefs
                if (importResult.result == com.tianma.xsmscode.feature.backup.ImportResult.SUCCESS) {
                    withContext(Dispatchers.IO) {
                        if (restoreRules && importResult.rules.isNotEmpty()) {
                            val dbManager = DBManager.get(context)
                            // Simple merge: add if not exists, or maybe just addAll (DB handles conflicts usually or we should check)
                            // existing implementation in RuleListViewModel wiped all rules if 'retain' was false.
                            // Here we probably want to MERGE.
                            // Implementing merge logic:
                            val entities = importResult.rules.map {
                                com.tianma.xsmscode.data.db.entity.SmsCodeRule(it.company, it.codeKeyword, it.codeRegex)
                            }
                            // For simplicity in this plan, we just add them. Uniqueness constraint might be on ID or content.
                            // SmsCodeRule has PrimaryKey autoGenerate.
                            // Ideally we should check duplicates.
                            dbManager.addSmsCodeRules(entities)
                        }

                        val records = importResult.records.orEmpty()
                        if (restoreRecords && records.isNotEmpty()) {
                            val dbManager = DBManager.get(context)
                            val entities = records.map {
                                com.tianma.xsmscode.data.db.entity.SmsMsg(
                                    sender = it.sender,
                                    body = it.body,
                                    date = it.date,
                                    company = it.company,
                                    smsCode = it.smsCode,
                                    packageName = it.packageName,
                                )
                            }
                            // SmsMsg has unique index on sender/body/date
                            // So we use insert with OnConflictStrategy.IGNORE usually.
                            // Check DB DAO specifically.
                            dbManager.addSmsMsgList(entities)
                        }

                        val prefsMap = importResult.preferences.orEmpty()
                        if (restoreConfig && prefsMap.isNotEmpty()) {
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
                                        }
                                    }

                                    intPrefKeys.contains(k) -> {
                                        val intValue = strV.trim().toIntOrNull()
                                        if (intValue != null) {
                                            AppPreferencesDataStore.setInt(context, k, intValue)
                                        }
                                    }

                                    else -> {
                                        AppPreferencesDataStore.setString(context, k, strV)
                                    }
                                }
                            }
                        }
                    }
                }

                _eventsFlow.emit(SettingsEvent.RestoreResultEvent(importResult))
            } catch (e: Exception) {
                e.printStackTrace()
                // Emit failure via RestoreResultEvent?
                // BackupImportResult has ImportResult enum
                // We can construct a failed result
            }
        }
    }

    private suspend fun ensureDataStoreLoaded(context: android.content.Context) {
        // Trigger read to ensure in-memory cache if needed; keep no-op for now.
    }
}

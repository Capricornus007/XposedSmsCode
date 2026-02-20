package com.tianma.xsmscode.ui.home

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.TextUtils
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.github.tianma8023.xposed.smscode.BuildConfig
import com.tianma.xsmscode.common.constant.Const
import com.tianma.xsmscode.common.constant.PrefConst
import com.tianma.xsmscode.common.utils.*
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import android.content.Intent
import com.github.tianma8023.xposed.smscode.R
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

internal fun resolvePreferredUpdateEvent(installedFromPlay: Boolean): SettingsEvent =
    if (installedFromPlay) SettingsEvent.StartPlayUpdate else SettingsEvent.StartGithubUpdateCheck

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val booleanPrefKeys = setOf(
        PrefConst.KEY_ENABLE,
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
        PrefConst.KEY_AUTO_UPDATE_ON_START,
        PrefConst.KEY_AUTO_UPDATE_WIFI_ONLY,
        PrefConst.KEY_PRIVACY_POLICY_ACCEPTED,
        PrefConst.KEY_BACKUP_COMPAT_TIP_SHOWN,
    )

    private val intPrefKeys = setOf(
        PrefConst.KEY_CHOOSE_THEME,
        "local_version_code",
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
                _eventsFlow.tryEmit(SettingsEvent.BackupResultEvent(result == ExportResult.SUCCESS))
            } catch (e: Exception) {
                e.printStackTrace()
                _eventsFlow.tryEmit(SettingsEvent.BackupResultEvent(false))
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

                if (importResult.result == com.tianma.xsmscode.feature.backup.ImportResult.SUCCESS) {
                    withContext(Dispatchers.IO) {
                        if (restoreRules) restoreRules(context, importResult.rules)
                        if (restoreRecords) restoreRecords(context, importResult.records.orEmpty())
                        if (restoreConfig) restorePreferences(context, importResult.preferences.orEmpty())
                    }
                }
                _eventsFlow.tryEmit(SettingsEvent.RestoreResultEvent(importResult))
            } catch (ignored: Exception) {
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
        if (records.isEmpty()) return
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
        dbManager.addSmsMsgList(entities)
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

    private suspend fun ensureDataStoreLoaded(_context: android.content.Context) {
        // Trigger read to ensure in-memory cache if needed; keep no-op for now.
    }
}

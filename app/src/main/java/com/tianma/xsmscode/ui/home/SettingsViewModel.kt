package com.tianma.xsmscode.ui.home

import android.app.Application
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.TextUtils
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import com.github.tianma8023.xposed.smscode.BuildConfig
import com.tianma.xsmscode.common.constant.Const
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.tianma.xsmscode.common.utils.*
import com.tianma.xsmscode.data.db.entity.ApkVersion
import com.tianma.xsmscode.data.http.NetworkError
import com.tianma.xsmscode.data.http.NetworkResult
import com.tianma.xsmscode.data.repository.DataRepository
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
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
}

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val _eventsFlow = MutableSharedFlow<SettingsEvent>()
    val eventsFlow: SharedFlow<SettingsEvent> = _eventsFlow.asSharedFlow()

    private val _themeMode = MutableStateFlow(0)
    val themeMode: StateFlow<Int> = _themeMode.asStateFlow()

    private val _updateVersion = MutableStateFlow<ApkVersion?>(null)
    val updateVersion: StateFlow<ApkVersion?> = _updateVersion.asStateFlow()

    init {
        viewModelScope.launch {
            _themeMode.value = SPUtils.getThemeMode(getApplication())
        }
    }

    fun setThemeMode(mode: Int) {
        viewModelScope.launch {
            SPUtils.setThemeMode(getApplication(), mode)
            _themeMode.value = mode
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
        val state = if (hide) PackageManager.COMPONENT_ENABLED_STATE_DISABLED else PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        if (pm.getComponentEnabledSetting(launcherCN) != state) {
            pm.setComponentEnabledSetting(launcherCN, state, PackageManager.DONT_KILL_APP)
        }
    }

    fun performSmsCodeTest(msgBody: String) {
        viewModelScope.launch {
            val code = withContext(Dispatchers.IO) {
                if (TextUtils.isEmpty(msgBody)) "" else
                    SmsCodeUtils.parseSmsCodeIfExists(getApplication(), msgBody)
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

    fun updateFromGithub() {
        Utils.showWebPage(getApplication(), Const.PROJECT_GITHUB_LATEST_RELEASE_URL)
    }

    fun updateFromCoolApk() {
        PackageUtils.showAppDetailsInCoolApk(getApplication())
    }
}

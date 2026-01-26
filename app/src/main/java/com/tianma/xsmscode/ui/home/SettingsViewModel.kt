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
import com.tianma.xsmscode.common.livedata.SingleLiveEvent
import com.tianma.xsmscode.common.utils.*
import com.tianma.xsmscode.data.db.entity.ApkVersion
import com.tianma.xsmscode.data.http.NetworkError
import com.tianma.xsmscode.data.http.NetworkResult
import com.tianma.xsmscode.data.repository.DataRepository
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val mShowPrivacyPolicyEvent = SingleLiveEvent<Void>()
    private val mShowAlipayPacketEvent = SingleLiveEvent<Void>()
    private val mSmsCodeTestResultEvent = SingleLiveEvent<String>()
    private val mCheckUpdateErrorEvent = SingleLiveEvent<NetworkError>()
    private val mShowUpdateDialogEvent = SingleLiveEvent<ApkVersion>()
    private val mAppAlreadyNewestEvent = SingleLiveEvent<Void>()

    val showPrivacyPolicyEvent: LiveData<Void> = mShowPrivacyPolicyEvent
    val showAlipayPacketEvent: LiveData<Void> = mShowAlipayPacketEvent
    val smsCodeTestResultEvent: LiveData<String> = mSmsCodeTestResultEvent
    val checkUpdateErrorEvent: LiveData<NetworkError> = mCheckUpdateErrorEvent
    val showUpdateDialogEvent: LiveData<ApkVersion> = mShowUpdateDialogEvent
    val appAlreadyNewestEvent: LiveData<Void> = mAppAlreadyNewestEvent

    override fun onCleared() {
        super.onCleared()
    }

    fun handleArguments(args: Bundle?) {
        if (args == null) return

        if (!SPUtils.isPrivacyPolicyAccepted(getApplication())) {
            mShowPrivacyPolicyEvent.call()
            return
        }

        val extraAction = args.getString(Const.EXTRA_ACTION)
        if (Const.ACTION_DONATE_BY_ALIPAY == extraAction) {
            args.remove(Const.EXTRA_ACTION)
            mShowAlipayPacketEvent.call()
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
            mSmsCodeTestResultEvent.value = code
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
                            mShowUpdateDialogEvent.value = result.data
                        } else {
                            mAppAlreadyNewestEvent.call()
                        }
                    }
                    is NetworkResult.Error -> {
                        mCheckUpdateErrorEvent.value = result.error
                    }
                }
            } catch (e: Throwable) {
                mCheckUpdateErrorEvent.value = NetworkError.Unexpected(e.message, e)
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

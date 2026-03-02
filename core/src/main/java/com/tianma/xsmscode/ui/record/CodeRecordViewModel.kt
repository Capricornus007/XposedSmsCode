package com.tianma.xsmscode.ui.record

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tianma.xsmscode.common.constant.Const
import com.tianma.xsmscode.common.utils.JsonUtils
import com.tianma.xsmscode.common.utils.XLog
import com.tianma.xsmscode.data.db.DBManager
import com.tianma.xsmscode.data.db.entity.SmsMsg
import kotlinx.serialization.Serializable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

@Immutable
data class CodeRecordUiState(val smsList: ImmutableList<SmsMsg> = persistentListOf(), val isLoading: Boolean = false)

@Serializable
private data class RecordExportPayload(
    val codeRecords: List<SmsMsg>,
    val plainSmsRecords: List<SmsMsg>,
    val appNotifyRecords: List<SmsMsg>,
)

class CodeRecordViewModel(application: Application) : AndroidViewModel(application) {

    private val _loading = MutableStateFlow(false)

    val uiState: StateFlow<CodeRecordUiState> = DBManager.get(application)
        .queryAllSmsMsgFlow()
        .combine(_loading) { smsList, loading ->
            CodeRecordUiState(smsList.toImmutableList(), loading)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(Const.FLOW_STOP_TIMEOUT_MS),
            initialValue = CodeRecordUiState(isLoading = true),
        )

    fun loadData() {
        // Data is automatically loaded via queryAllSmsMsgFlow() in uiState
    }

    fun refreshData() {
        viewModelScope.launch {
            _loading.value = true
            try {
                withContext(Dispatchers.IO) {
                    DBManager.get(getApplication()).queryAllSmsMsg()
                }
            } finally {
                _loading.value = false
            }
        }
    }

    fun removeSmsMsg(smsMsgList: List<SmsMsg>) {
        viewModelScope.launch {
            try {
                DBManager.get(getApplication())
                    .removeSmsMsgListSuspend(smsMsgList)
            } catch (ignored: Throwable) {
                XLog.e("Error occurs when remove SMS records", ignored)
            }
        }
    }

    fun restoreSmsMsgList(smsMsgList: List<SmsMsg>) {
        viewModelScope.launch {
            try {
                DBManager.get(getApplication())
                    .insertSmsMsgListSuspend(smsMsgList)
            } catch (ignored: Throwable) {
                XLog.e("Error occurs when restore SMS records", ignored)
            }
        }
    }

    fun exportRecords(context: Context, uri: Uri, currentTab: Int, exportAllTabs: Boolean) {
        viewModelScope.launch {
            _loading.value = true
            try {
                val allRecords = uiState.value.smsList.toList()
                val codeRecords = allRecords.filter {
                    it.msgType == SmsMsg.MSG_TYPE_SMS && !it.smsCode.isNullOrBlank()
                }
                val plainSmsRecords = allRecords.filter {
                    it.msgType == SmsMsg.MSG_TYPE_SMS && it.smsCode.isNullOrBlank()
                }
                val appNotifyRecords = allRecords.filter {
                    it.msgType == SmsMsg.MSG_TYPE_APP_NOTIFY
                }
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { os ->
                        OutputStreamWriter(os, StandardCharsets.UTF_8).use { osw ->
                            if (exportAllTabs) {
                                JsonUtils.toJson(
                                    RecordExportPayload(
                                        codeRecords = codeRecords,
                                        plainSmsRecords = plainSmsRecords,
                                        appNotifyRecords = appNotifyRecords,
                                    ),
                                    osw,
                                    true,
                                )
                            } else {
                                val currentRecords = when (currentTab) {
                                    0 -> codeRecords
                                    1 -> plainSmsRecords
                                    else -> appNotifyRecords
                                }
                                JsonUtils.toJson(currentRecords, osw, true)
                            }
                        }
                    }
                }
                // We might want an event for success/failure
            } catch (ignored: Throwable) {
                XLog.e("Export records failed", ignored)
            } finally {
                _loading.value = false
            }
        }
    }
}

package com.tianma.xsmscode.ui.rule.list

import android.app.Application
import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.core.os.BundleCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.github.tianma8023.xposed.smscode.BuildConfig
import com.tianma.xsmscode.common.constant.Const
import com.tianma.xsmscode.common.utils.XLog
import com.tianma.xsmscode.data.db.DBManager
import com.tianma.xsmscode.data.db.entity.SmsCodeRule
import com.tianma.xsmscode.feature.backup.BackupManager
import com.tianma.xsmscode.feature.backup.BackupRule
import com.tianma.xsmscode.feature.backup.ExportResult
import com.tianma.xsmscode.feature.backup.ImportResult
import com.tianma.xsmscode.feature.backup.ImportWarning
import com.tianma.xsmscode.feature.store.EntityStoreManager
import com.tianma.xsmscode.feature.store.EntityType
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class RuleListEvent {
    data class ImportDirect(val uri: Uri) : RuleListEvent()
    data class ImportDialogConfirm(val uri: Uri) : RuleListEvent()
    data class ShowProgress(val msg: String) : RuleListEvent()
    data object CancelProgress : RuleListEvent()
    data class ExportResultEvent(val success: Boolean) : RuleListEvent()
    data class ImportResultEvent(val result: ImportResult) : RuleListEvent()
    data class ImportWarningEvent(val warning: ImportWarning) : RuleListEvent()
}

class RuleListViewModel(application: Application) : AndroidViewModel(application) {

    // Flow-backed StateFlow with ImmutableList
    val rulesFlow: StateFlow<ImmutableList<SmsCodeRule>> = DBManager.get(application)
        .queryAllSmsCodeRulesFlow()
        .map { it.toImmutableList() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = persistentListOf(),
        )

    private val _eventsFlow = MutableSharedFlow<RuleListEvent>()
    val eventsFlow: SharedFlow<RuleListEvent> = _eventsFlow.asSharedFlow()

    override fun onCleared() {
        super.onCleared()
    }

    fun handleArguments(args: Bundle?) {
        if (args == null) return

        val importUri = BundleCompat.getParcelable(args, Const.EXTRA_IMPORT_URI, Uri::class.java)
        if (importUri != null) {
            args.remove(Const.EXTRA_IMPORT_URI)
            if (ContentResolver.SCHEME_FILE == importUri.scheme) {
                viewModelScope.launch { _eventsFlow.emit(RuleListEvent.ImportDirect(importUri)) }
            } else {
                viewModelScope.launch { _eventsFlow.emit(RuleListEvent.ImportDialogConfirm(importUri)) }
            }
        }
    }

    fun removeRule(codeRule: SmsCodeRule) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    DBManager.get(getApplication()).removeSmsCodeRuleSuspend(codeRule)
                }
            } catch (t: Throwable) {
                XLog.e("Remove $codeRule failed", t)
            }
        }
    }

    fun exportRules(rules: List<SmsCodeRule>, context: Context, uri: Uri, progressMsg: String) {
        viewModelScope.launch {
            _eventsFlow.emit(RuleListEvent.ShowProgress(progressMsg))
            try {
                val result = withContext(Dispatchers.IO) {
                    BackupManager.exportRuleList(context, uri, rules.toBackupRules(), BuildConfig.VERSION_NAME)
                }
                _eventsFlow.emit(RuleListEvent.ExportResultEvent(result == ExportResult.SUCCESS))
                _eventsFlow.emit(RuleListEvent.CancelProgress)
            } catch (t: Throwable) {
                XLog.e("Export failed", t)
                _eventsFlow.emit(RuleListEvent.CancelProgress)
            }
        }
    }

    fun importRules(uri: Uri, retain: Boolean, progressMsg: String) {
        viewModelScope.launch {
            _eventsFlow.emit(RuleListEvent.ShowProgress(progressMsg))
            try {
                val importResult = withContext(Dispatchers.IO) {
                    val result = BackupManager.importRuleList(getApplication(), uri, BuildConfig.VERSION_NAME)
                    if (result.result == ImportResult.SUCCESS) {
                        val dbManager = DBManager.get(getApplication())
                        if (!retain) {
                            dbManager.removeAllSmsCodeRules()
                        }
                        val rules = result.rules.toSmsCodeRules()
                        if (rules.isNotEmpty()) {
                            dbManager.addSmsCodeRules(rules)
                        }
                    }
                    result
                }
                _eventsFlow.emit(RuleListEvent.ImportResultEvent(importResult.result))
                importResult.warning?.let { warning ->
                    _eventsFlow.emit(RuleListEvent.ImportWarningEvent(warning))
                }
                _eventsFlow.emit(RuleListEvent.CancelProgress)
            } catch (t: Throwable) {
                XLog.e("Import rules failed", t)
                _eventsFlow.emit(RuleListEvent.CancelProgress)
            }
        }
    }

    fun saveRulesToFile(rules: List<SmsCodeRule>) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                EntityStoreManager.storeEntitiesToFile(
                    getApplication(),
                    EntityType.CODE_RULES,
                    rules,
                    SmsCodeRule::class.java,
                )
            }
        }
    }

    private fun List<SmsCodeRule>.toBackupRules(): List<BackupRule> = map { rule ->
        BackupRule(
            company = rule.company,
            codeKeyword = rule.codeKeyword,
            codeRegex = rule.codeRegex,
        )
    }

    private fun List<BackupRule>.toSmsCodeRules(): List<SmsCodeRule> = map { rule ->
        SmsCodeRule(
            company = rule.company,
            codeKeyword = rule.codeKeyword,
            codeRegex = rule.codeRegex,
        )
    }
}

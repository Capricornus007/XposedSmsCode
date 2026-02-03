package com.tianma.xsmscode.ui.rule.edit

import android.app.Application
import android.os.Bundle
import androidx.core.os.BundleCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tianma.xsmscode.common.constant.Const
import com.tianma.xsmscode.data.db.DBManager
import com.tianma.xsmscode.data.db.entity.SmsCodeRule
import com.tianma.xsmscode.feature.store.EntityStoreManager
import com.tianma.xsmscode.feature.store.EntityType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class RuleEditEvent {
    data class TemplateSaved(val success: Boolean) : RuleEditEvent()
    data class ValidationError(val result: RuleEditViewModel.ValidationResult) : RuleEditEvent()
    data object HideSoftInput : RuleEditEvent()
    data class CodeRuleSaved(val success: Boolean) : RuleEditEvent()
}

class RuleEditViewModel(application: Application) : AndroidViewModel(application) {

    private val _eventsFlow = MutableSharedFlow<RuleEditEvent>()
    val eventsFlow = _eventsFlow.asSharedFlow()

    private val _codeRuleFlow = MutableStateFlow(SmsCodeRule())
    val codeRuleFlow: StateFlow<SmsCodeRule> = _codeRuleFlow.asStateFlow()

    private var mRuleEditType: Int = Const.EDIT_TYPE_CREATE
    private var mCodeRule: SmsCodeRule = SmsCodeRule()

    override fun onCleared() {
        super.onCleared()
    }

    fun handleArguments(args: Bundle?) {
        if (args == null) return

        mRuleEditType = args.getInt(Const.KEY_RULE_EDIT_TYPE)
        val ruleId = args.getLong(Const.KEY_RULE_ID, -1L)
        if (ruleId != -1L) {
            loadRule(ruleId)
            return
        }

        val codeRule = BundleCompat.getParcelable(args, Const.KEY_CODE_RULE, SmsCodeRule::class.java)
        if (mRuleEditType == Const.EDIT_TYPE_EDIT && codeRule != null) {
            mCodeRule = codeRule
            _codeRuleFlow.value = mCodeRule
        } else {
            loadTemplate()
        }
    }

    fun loadRule(ruleId: Long) {
        viewModelScope.launch {
            try {
                val rule = withContext(Dispatchers.IO) {
                    DBManager.get(getApplication()).querySmsCodeRuleByIdSuspend(ruleId)
                }
                if (rule != null) {
                    mCodeRule = rule
                    mRuleEditType = Const.EDIT_TYPE_EDIT
                    _codeRuleFlow.value = mCodeRule
                } else {
                    loadTemplate()
                }
            } catch (e: Throwable) {
                loadTemplate()
            }
        }
    }

    fun loadTemplate() {
        viewModelScope.launch {
            try {
                val codeRule = withContext(Dispatchers.IO) {
                    EntityStoreManager.loadEntityFromFile(
                        getApplication(),
                        EntityType.CODE_RULE_TEMPLATE,
                        SmsCodeRule::class.java,
                    ) ?: SmsCodeRule()
                }
                mCodeRule = codeRule
                _codeRuleFlow.value = mCodeRule
            } catch (e: Throwable) {
                // ignore
            }
        }
    }

    fun saveAsTemplate(template: SmsCodeRule) {
        viewModelScope.launch {
            val success = withContext(Dispatchers.IO) {
                EntityStoreManager.storeEntityToFile(
                    getApplication(),
                    EntityType.CODE_RULE_TEMPLATE,
                    template,
                    SmsCodeRule::class.java,
                )
            }
            _eventsFlow.emit(RuleEditEvent.TemplateSaved(success))
        }
    }

    fun saveIfValid(codeRule: SmsCodeRule) {
        if (!checkValid(codeRule)) return
        viewModelScope.launch { _eventsFlow.emit(RuleEditEvent.HideSoftInput) }

        mCodeRule = mCodeRule.copy(
            company = codeRule.company,
            codeKeyword = codeRule.codeKeyword,
            codeRegex = codeRule.codeRegex,
        )

        viewModelScope.launch {
            val success = withContext(Dispatchers.IO) {
                val dbManager = DBManager.get(getApplication())
                if (mRuleEditType == Const.EDIT_TYPE_CREATE) {
                    if (dbManager.isExistsSuspend(mCodeRule)) {
                        false
                    } else {
                        val id = dbManager.addSmsCodeRuleSuspend(mCodeRule)
                        mCodeRule = mCodeRule.copy(id = id)
                        true
                    }
                } else {
                    dbManager.updateSmsCodeRuleSuspend(mCodeRule)
                    true
                }
            }
            _codeRuleFlow.value = mCodeRule
            _eventsFlow.emit(RuleEditEvent.CodeRuleSaved(success))
        }
    }

    private fun checkValid(codeRule: SmsCodeRule): Boolean {
        val companyValid = !codeRule.company.isNullOrEmpty()
        val keywordValid = codeRule.codeKeyword.isNotEmpty()
        val codeRegexValid = codeRule.codeRegex.isNotEmpty()

        val result = ValidationResult(companyValid, keywordValid, codeRegexValid)
        viewModelScope.launch {
            if (companyValid && keywordValid && codeRegexValid) {
                _eventsFlow.emit(RuleEditEvent.HideSoftInput)
            }
            _eventsFlow.emit(RuleEditEvent.ValidationError(result))
        }
        return companyValid && keywordValid && codeRegexValid
    }

    data class ValidationResult(val companyValid: Boolean, val keywordValid: Boolean, val codeRegexValid: Boolean)
}

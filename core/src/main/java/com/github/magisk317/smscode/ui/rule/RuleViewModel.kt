package com.github.magisk317.smscode.ui.rule

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.github.magisk317.smscode.forwarder.entity.Rule
import com.github.magisk317.smscode.forwarder.entity.Sender
import com.tianma.xsmscode.data.db.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RuleViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getInstance(application)
    private val ruleDao = db.ruleDao()
    private val senderDao = db.senderDao()

    private val _ruleList = MutableStateFlow<List<Rule>>(emptyList())
    val ruleList: StateFlow<List<Rule>> = _ruleList.asStateFlow()

    private val _senderList = MutableStateFlow<List<Sender>>(emptyList())
    val senderList: StateFlow<List<Sender>> = _senderList.asStateFlow()

    init {
        loadRules()
        loadSenders()
    }

    private var currentSenderId = 0L

    fun loadRules(senderId: Long = 0L) {
        currentSenderId = senderId
        viewModelScope.launch(Dispatchers.IO) {
            val all = ruleDao.getAll()
            _ruleList.value = if (senderId == 0L) all else all.filter { it.senderId == senderId }
        }
    }

    private fun loadSenders() {
        viewModelScope.launch(Dispatchers.IO) {
            _senderList.value = senderDao.getAll()
        }
    }

    fun deleteRule(rule: Rule) {
        viewModelScope.launch(Dispatchers.IO) {
            ruleDao.delete(rule)
            loadRules(currentSenderId)
        }
    }

    fun toggleRuleStatus(rule: Rule, enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            rule.status = if (enabled) 1 else 0
            ruleDao.update(rule)
            loadRules(currentSenderId)
        }
    }

    suspend fun getRule(id: Long): Rule? = withContext(Dispatchers.IO) {
        runCatching { ruleDao.getOne(id) }.getOrNull()
    }

    suspend fun saveRuleSync(rule: Rule) {
        withContext(Dispatchers.IO) {
            if (rule.id == 0L) {
                ruleDao.insert(rule)
            } else {
                ruleDao.update(rule)
            }
        }
    }

    fun saveRule(rule: Rule) {
        viewModelScope.launch(Dispatchers.IO) {
            saveRuleSync(rule)
            loadRules(currentSenderId)
        }
    }
}

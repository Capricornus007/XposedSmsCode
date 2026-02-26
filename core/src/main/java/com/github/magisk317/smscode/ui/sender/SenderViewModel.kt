package com.github.magisk317.smscode.ui.sender

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tianma.xsmscode.data.db.AppDatabase
import com.github.magisk317.smscode.forwarder.entity.Sender
import com.github.magisk317.smscode.forwarder.utils.SenderValidationResult
import com.github.magisk317.smscode.forwarder.utils.SenderValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SenderViewModel(application: Application) : AndroidViewModel(application) {
    private val senderDao = AppDatabase.getInstance(application).senderDao()

    val senderList: StateFlow<List<Sender>> = senderDao.getAllFlow().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList(),
    )
    private val _lastSavedStatus = MutableStateFlow<Int?>(null)
    val lastSavedStatus: StateFlow<Int?> = _lastSavedStatus.asStateFlow()

    fun loadSenders() {
        // no-op: senderList is now reactive from Room Flow.
    }

    fun deleteSender(sender: Sender) {
        viewModelScope.launch(Dispatchers.IO) {
            senderDao.delete(sender)
        }
    }

    fun toggleSenderStatus(sender: Sender, enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val newStatus = if (enabled) 1 else 0
            senderDao.updateStatusByIds(listOf(sender.id), newStatus)
        }
    }

    fun validateSenderForEnable(sender: Sender): SenderValidationResult {
        return SenderValidator.validateForEnable(sender)
    }

    suspend fun getSender(id: Long): Sender? {
        return withContext(Dispatchers.IO) {
            senderDao.getOne(id)
        }
    }

    suspend fun saveSenderSync(sender: Sender) {
        withContext(Dispatchers.IO) {
            if (sender.id == 0L) {
                senderDao.insert(sender)
            } else {
                senderDao.update(sender)
            }
            _lastSavedStatus.value = sender.status
        }
    }

    fun saveSender(sender: Sender) {
        viewModelScope.launch(Dispatchers.IO) {
            saveSenderSync(sender)
        }
    }

    fun clearLastSavedStatus() {
        _lastSavedStatus.value = null
    }
}

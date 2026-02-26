package com.github.magisk317.smscode.ui.sender

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tianma.xsmscode.data.db.AppDatabase
import com.github.magisk317.smscode.forwarder.entity.ForwardCommonConfig
import com.github.magisk317.smscode.forwarder.entity.Sender
import com.github.magisk317.smscode.forwarder.utils.DeviceIdentityUtils
import com.github.magisk317.smscode.forwarder.utils.ForwardCommonConfigStore
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
    private val db = AppDatabase.getInstance(application)
    private val senderDao = db.senderDao()

    private val _forwardCommonConfig = MutableStateFlow(
        ForwardCommonConfig(deviceName = DeviceIdentityUtils.resolveDefaultDeviceName()),
    )
    val forwardCommonConfig: StateFlow<ForwardCommonConfig> = _forwardCommonConfig.asStateFlow()

    val senderList: StateFlow<List<Sender>> = senderDao.getAllFlow().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList(),
    )
    private val _lastSavedStatus = MutableStateFlow<Int?>(null)
    val lastSavedStatus: StateFlow<Int?> = _lastSavedStatus.asStateFlow()

    init {
        refreshForwardCommonConfig()
    }

    fun loadSenders() {
        // no-op: senderList is now reactive from Room Flow.
    }

    fun saveForwardCommonConfig(config: ForwardCommonConfig) {
        val context = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            ForwardCommonConfigStore.save(context, config)
            _forwardCommonConfig.value = ForwardCommonConfigStore.load(context)
        }
    }

    private fun refreshForwardCommonConfig() {
        val context = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            _forwardCommonConfig.value = ForwardCommonConfigStore.load(context)
        }
    }

    /**
     * Force WAL checkpoint so that data written by the App UI process is flushed
     * to the main DB file and becomes visible to the Hook process (com.android.phone).
     */
    private fun walCheckpoint() {
        try {
            db.openHelper.writableDatabase
                .query("PRAGMA wal_checkpoint(TRUNCATE)")
                .close()
        } catch (_: Throwable) {
            // Best-effort: if checkpoint fails, enableMultiInstanceInvalidation
            // should still handle cross-process visibility.
        }
    }

    fun deleteSender(sender: Sender) {
        viewModelScope.launch(Dispatchers.IO) {
            senderDao.delete(sender)
            walCheckpoint()
        }
    }

    fun toggleSenderStatus(sender: Sender, enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val newStatus = if (enabled) 1 else 0
            senderDao.updateStatusByIds(listOf(sender.id), newStatus)
            walCheckpoint()
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
            walCheckpoint()
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

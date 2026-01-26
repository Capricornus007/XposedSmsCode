package com.tianma.xsmscode.data.eventbus

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch

/**
 * Event bus utils
 */
object XEventBus {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val _events = MutableSharedFlow<Any>()
    val events = _events.asSharedFlow()

    fun post(event: Any) {
        scope.launch {
            _events.emit(event)
        }
    }

    suspend inline fun <reified T> observe(): Flow<T> {
        return events.filterIsInstance<T>()
    }
}

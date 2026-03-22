package com.github.magisk317.smscode.ui.app

import kotlinx.coroutines.CoroutineScope

internal object FlavorXposedServiceBridge {
    fun initialize(application: SmsCodeApplication, applicationScope: CoroutineScope) {
        // Legacy builds rely on the classic Xposed entry and do not bind libxposed services.
    }
}

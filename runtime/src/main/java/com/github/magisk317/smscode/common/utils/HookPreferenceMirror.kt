package com.github.magisk317.smscode.common.utils

import android.content.Context
import com.github.magisk317.smscode.runtime.BuildConfig

object HookPreferenceMirror {
    private fun requiresLegacyCompatMirror(): Boolean = BuildConfig.XPOSED_API_FLAVOR == "legacy"

    suspend fun publish(context: Context) {
        if (requiresLegacyCompatMirror()) {
            AppPreferencesDataStore.syncToSharedPrefs(context)
            AppPreferencesDataStore.ensureReadable(context)
        }
        AppPreferencesDataStore.syncToRemotePrefs(context)
    }
}

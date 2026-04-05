package com.github.magisk317.smscode.common.utils

import android.content.Context
import com.github.magisk317.smscode.runtime.BuildConfig
import io.github.magisk317.smscode.runtime.common.diagnostics.ActivationDiagnosticsSnapshot
import io.github.magisk317.smscode.runtime.common.diagnostics.ActivationStatusInputs
import io.github.magisk317.smscode.runtime.common.diagnostics.RuntimeDiagnosticsConfig
import io.github.magisk317.smscode.runtime.common.diagnostics.RuntimeDiagnosticsEnvironment

internal object RuntimeDiagnosticsBridge {
    @Volatile
    private var installed = false

    fun ensureInstalled() {
        if (installed) return
        synchronized(this) {
            if (installed) return
            RuntimeDiagnosticsEnvironment.install(
                RuntimeDiagnosticsConfig(
                    applicationId = BuildConfig.APPLICATION_ID,
                    logTag = BuildConfig.LOG_TAG,
                    exportFilePrefix = "smscode_logs_",
                    stagingDirPrefix = ".tmp_smscode_logs_",
                    runtimeConnectedProvider = ModuleUtils::isRuntimeActivated,
                    activationStatusResolver = ::resolveActivationStatus,
                    routeResolver = { io.github.magisk317.smscode.runtime.common.diagnostics.RuntimeLogStore.ROUTE_APP },
                ),
            )
            installed = true
        }
    }

    private fun resolveActivationStatus(
        context: Context,
        snapshot: ActivationDiagnosticsSnapshot,
        inputs: ActivationStatusInputs,
    ): Boolean {
        return if (BuildConfig.XPOSED_API_FLAVOR == "legacy") {
            ModuleUtils.isModuleActivated(context) || inputs.hasHookHeartbeat
        } else {
            inputs.runtimeConnected || inputs.hasHookHeartbeat
        }
    }
}

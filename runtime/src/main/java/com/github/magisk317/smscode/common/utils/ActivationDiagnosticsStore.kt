package com.github.magisk317.smscode.common.utils

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.github.magisk317.smscode.runtime.BuildConfig
import com.github.magisk317.smscode.common.utils.ModuleUtils
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ActivationDiagnosticsSnapshot(
    val lastServiceBindAtMs: Long = 0L,
    val lastServiceFrameworkName: String = "",
    val lastServiceFrameworkVersion: String = "",
    val lastHookAtMs: Long = 0L,
    val lastHookPackage: String = "",
    val lastHookProcess: String = "",
    val lastHookSource: String = "",
)

object ActivationDiagnosticsStore {
    private const val FILE_NAME = "activation_diagnostics"
    private const val XPOSED_API_FLAVOR_LEGACY = "legacy"
    private const val KEY_LAST_SERVICE_BIND_AT_MS = "last_service_bind_at_ms"
    private const val KEY_LAST_SERVICE_FRAMEWORK_NAME = "last_service_framework_name"
    private const val KEY_LAST_SERVICE_FRAMEWORK_VERSION = "last_service_framework_version"
    private const val KEY_LAST_HOOK_AT_MS = "last_hook_at_ms"
    private const val KEY_LAST_HOOK_PACKAGE = "last_hook_package"
    private const val KEY_LAST_HOOK_PROCESS = "last_hook_process"
    private const val KEY_LAST_HOOK_SOURCE = "last_hook_source"

    private val lock = Any()

    fun snapshot(context: Context): ActivationDiagnosticsSnapshot = synchronized(lock) {
        readSnapshotLocked(context)
    }

    fun hasHookHeartbeatThisBoot(context: Context): Boolean {
        val current = snapshot(context)
        return current.lastHookAtMs >= currentBootStartAtMs()
    }

    fun isRuntimeConnected(): Boolean = ModuleUtils.isRuntimeActivated()

    fun isModuleActivated(context: Context): Boolean {
        if (BuildConfig.XPOSED_API_FLAVOR == XPOSED_API_FLAVOR_LEGACY) {
            return ModuleUtils.isModuleActivated(context) || hasHookHeartbeatThisBoot(context)
        }
        return isRuntimeConnected() || hasHookHeartbeatThisBoot(context)
    }

    fun recordServiceBind(
        context: Context,
        frameworkName: String,
        frameworkVersion: String,
        verboseLogging: Boolean,
    ) {
        val current = synchronized(lock) {
            val snapshot = readSnapshotLocked(context)
            snapshot.copy(
                lastServiceBindAtMs = System.currentTimeMillis(),
                lastServiceFrameworkName = frameworkName,
                lastServiceFrameworkVersion = frameworkVersion,
            ).also { writeSnapshotLocked(context, it) }
        }
        logSnapshotIfVerbose(
            context = context,
            verboseLogging = verboseLogging,
            source = "service_bind",
            snapshot = current,
            runtimeConnected = true,
        )
    }

    fun recordServiceDied(
        context: Context,
        verboseLogging: Boolean,
    ) {
        logSnapshotIfVerbose(
            context = context,
            verboseLogging = verboseLogging,
            source = "service_died",
            snapshot = snapshot(context),
            runtimeConnected = false,
        )
    }

    fun recordHookHeartbeat(
        context: Context,
        packageName: String,
        processName: String,
        source: String,
        verboseLogging: Boolean,
    ) {
        val current = synchronized(lock) {
            val snapshot = readSnapshotLocked(context)
            snapshot.copy(
                lastHookAtMs = System.currentTimeMillis(),
                lastHookPackage = packageName,
                lastHookProcess = processName,
                lastHookSource = source,
            ).also { writeSnapshotLocked(context, it) }
        }
        logSnapshotIfVerbose(
            context = context,
            verboseLogging = verboseLogging,
            source = source,
            snapshot = current,
            runtimeConnected = null,
        )
    }

    private fun logSnapshotIfVerbose(
        context: Context,
        verboseLogging: Boolean,
        source: String,
        snapshot: ActivationDiagnosticsSnapshot,
        runtimeConnected: Boolean?,
    ) {
        if (!verboseLogging) return
        val message = buildString {
            append("Diag activation snapshot: source=")
            append(source)
            append(" service=")
            append(
                when (runtimeConnected) {
                    true -> "connected"
                    false -> "disconnected"
                    null -> "unknown"
                },
            )
            append(" lastService=")
            append(formatTime(snapshot.lastServiceBindAtMs))
            append(" framework=")
            append(
                listOf(
                    snapshot.lastServiceFrameworkName.ifBlank { "unknown" },
                    snapshot.lastServiceFrameworkVersion.ifBlank { "unknown" },
                ).joinToString(" "),
            )
            append(" lastHook=")
            append(snapshot.lastHookPackage.ifBlank { "<none>" })
            append("/")
            append(snapshot.lastHookProcess.ifBlank { "<none>" })
            append(" at=")
            append(formatTime(snapshot.lastHookAtMs))
            append(" hookSource=")
            append(snapshot.lastHookSource.ifBlank { "<none>" })
        }
        RuntimeLogStore.initialize(context, enableDetailedLogs = true)
        RuntimeLogStore.append(
            priority = Log.INFO,
            tag = BuildConfig.LOG_TAG,
            message = message,
            force = true,
        )
        Log.i(BuildConfig.LOG_TAG, message)
        Log.i("LSPosed-Bridge", "${BuildConfig.LOG_TAG}: $message")
    }

    private fun currentBootStartAtMs(): Long {
        return (System.currentTimeMillis() - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
    }

    private fun readSnapshotLocked(context: Context): ActivationDiagnosticsSnapshot {
        val file = getStoreFile(context)
        if (!file.exists()) return ActivationDiagnosticsSnapshot()
        val values = mutableMapOf<String, String>()
        runCatching {
            file.forEachLine { line ->
                val index = line.indexOf('=')
                if (index <= 0) return@forEachLine
                val key = line.substring(0, index)
                val value = line.substring(index + 1)
                values[key] = value
            }
        }
        return ActivationDiagnosticsSnapshot(
            lastServiceBindAtMs = values[KEY_LAST_SERVICE_BIND_AT_MS]?.toLongOrNull() ?: 0L,
            lastServiceFrameworkName = values[KEY_LAST_SERVICE_FRAMEWORK_NAME].orEmpty(),
            lastServiceFrameworkVersion = values[KEY_LAST_SERVICE_FRAMEWORK_VERSION].orEmpty(),
            lastHookAtMs = values[KEY_LAST_HOOK_AT_MS]?.toLongOrNull() ?: 0L,
            lastHookPackage = values[KEY_LAST_HOOK_PACKAGE].orEmpty(),
            lastHookProcess = values[KEY_LAST_HOOK_PROCESS].orEmpty(),
            lastHookSource = values[KEY_LAST_HOOK_SOURCE].orEmpty(),
        )
    }

    private fun writeSnapshotLocked(context: Context, snapshot: ActivationDiagnosticsSnapshot) {
        val file = getStoreFile(context)
        try {
            file.parentFile?.mkdirs()
            file.writeText(
                buildString {
                    appendLine("$KEY_LAST_SERVICE_BIND_AT_MS=${snapshot.lastServiceBindAtMs}")
                    appendLine("$KEY_LAST_SERVICE_FRAMEWORK_NAME=${snapshot.lastServiceFrameworkName}")
                    appendLine("$KEY_LAST_SERVICE_FRAMEWORK_VERSION=${snapshot.lastServiceFrameworkVersion}")
                    appendLine("$KEY_LAST_HOOK_AT_MS=${snapshot.lastHookAtMs}")
                    appendLine("$KEY_LAST_HOOK_PACKAGE=${snapshot.lastHookPackage}")
                    appendLine("$KEY_LAST_HOOK_PROCESS=${snapshot.lastHookProcess}")
                    appendLine("$KEY_LAST_HOOK_SOURCE=${snapshot.lastHookSource}")
                },
            )
        } catch (_: IOException) {
            // ignore
        }
    }

    private fun getStoreFile(context: Context): File {
        return File(StorageUtils.getExternalFilesDir(context), FILE_NAME)
    }

    private fun formatTime(timestampMs: Long): String {
        if (timestampMs <= 0L) return "never"
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(timestampMs))
    }
}

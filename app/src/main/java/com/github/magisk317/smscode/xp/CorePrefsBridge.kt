package com.github.magisk317.smscode.xp

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import com.github.magisk317.smscode.common.constant.PrefConst
import com.github.magisk317.smscode.common.utils.PrefsReader
import io.github.magisk317.smscode.xposed.prefs.CorePrefs
import io.github.magisk317.smscode.xposed.prefs.CorePrefsAccess
import io.github.magisk317.smscode.xposed.runtime.CoreRuntime

object CorePrefsBridge {
    private const val PREFS_NAME = "xposed_prefs"

    fun installRemote(remotePrefsProvider: () -> SharedPreferences?) {
        CorePrefs.install(buildAccess(remotePrefsProvider = remotePrefsProvider, remoteOnly = true))
    }

    fun installLegacyCompat() {
        CorePrefs.install(buildAccess(remotePrefsProvider = null, remoteOnly = false))
    }

    private fun buildAccess(
        remotePrefsProvider: (() -> SharedPreferences?)?,
        remoteOnly: Boolean,
    ): CorePrefsAccess {
        return object : CorePrefsAccess {
            override fun getBoolean(key: String, defaultValue: Boolean): Boolean {
                if (key == PrefConst.KEY_SENSITIVE_DEBUG_LOG_MODE && !PrefsReader.isSensitiveDebugLogSupported()) {
                    return false
                }
                remotePrefsProvider?.invoke()?.let { prefs ->
                    if (prefs.contains(key)) {
                        return prefs.getBoolean(key, defaultValue)
                    }
                }
                if (remoteOnly) return defaultValue
                val context = resolveCompatContext() ?: return defaultValue
                return readBooleanFromCompatFallbacks(context, key, defaultValue)
            }

            override fun getString(key: String, defaultValue: String): String {
                remotePrefsProvider?.invoke()?.let { prefs ->
                    if (prefs.contains(key)) {
                        return prefs.getString(key, defaultValue) ?: defaultValue
                    }
                }
                if (remoteOnly) return defaultValue
                val context = resolveCompatContext() ?: return defaultValue
                return readStringFromCompatFallbacks(context, key, defaultValue)
            }

            override fun getInt(key: String, defaultValue: Int): Int {
                remotePrefsProvider?.invoke()?.let { prefs ->
                    if (prefs.contains(key)) {
                        return when (val value = prefs.all[key]) {
                            is Int -> value
                            is Long -> value.toInt()
                            is String -> value.toIntOrNull() ?: defaultValue
                            else -> prefs.getInt(key, defaultValue)
                        }
                    }
                }
                if (remoteOnly) return defaultValue
                val context = resolveCompatContext() ?: return defaultValue
                return readIntFromCompatFallbacks(context, key, defaultValue)
            }
        }
    }

    private fun resolveCompatContext(): Context? {
        val application = runCatching {
            val activityThreadClass = Class.forName("android.app.ActivityThread")
            val currentApplication = activityThreadClass.getMethod("currentApplication")
            currentApplication.invoke(null) as? Context
        }.getOrNull()
        if (application != null) return application

        return runCatching {
            val activityThreadClass = Class.forName("android.app.ActivityThread")
            val currentThread = activityThreadClass.getMethod("currentActivityThread").invoke(null) ?: return@runCatching null
            val systemContext = currentThread.javaClass.methods.firstOrNull {
                it.name == "getSystemContext" && it.parameterTypes.isEmpty()
            } ?: return@runCatching null
            systemContext.invoke(currentThread) as? Context
        }.getOrNull()
    }

    private fun readBooleanFromCompatFallbacks(context: Context, key: String, defaultValue: Boolean): Boolean {
        val providerUri = buildProviderUri(context, "bool", key, defaultValue.toString())
        runCatching {
            context.contentResolver.query(providerUri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val value = cursor.getString(0)
                    return value == "1" || value.equals("true", ignoreCase = true)
                }
            }
        }
        return runCatching {
            getSharedPrefs(context)?.getBoolean(key, defaultValue) ?: defaultValue
        }.getOrDefault(defaultValue)
    }

    private fun readStringFromCompatFallbacks(context: Context, key: String, defaultValue: String): String {
        val providerUri = buildProviderUri(context, "string", key, defaultValue)
        runCatching {
            context.contentResolver.query(providerUri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    return cursor.getString(0) ?: defaultValue
                }
            }
        }
        return runCatching {
            getSharedPrefs(context)?.getString(key, defaultValue) ?: defaultValue
        }.getOrDefault(defaultValue)
    }

    private fun readIntFromCompatFallbacks(context: Context, key: String, defaultValue: Int): Int {
        val providerUri = buildProviderUri(context, "int", key, defaultValue.toString())
        runCatching {
            context.contentResolver.query(providerUri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    return cursor.getString(0)?.toIntOrNull() ?: defaultValue
                }
            }
        }
        return runCatching {
            when (val value = getSharedPrefs(context)?.all?.get(key)) {
                is Int -> value
                is Long -> value.toInt()
                is String -> value.toIntOrNull() ?: defaultValue
                else -> defaultValue
            }
        }.getOrDefault(defaultValue)
    }

    private fun buildProviderUri(
        context: Context,
        typePath: String,
        key: String,
        defaultValue: String,
    ): Uri {
        val modulePackage = CoreRuntime.access.applicationId.takeIf { it.isNotBlank() } ?: context.packageName
        return Uri.parse("content://$modulePackage.pref.provider/$typePath")
            .buildUpon()
            .appendQueryParameter("key", key)
            .appendQueryParameter("default", defaultValue)
            .build()
    }

    private fun getSharedPrefs(context: Context): SharedPreferences? {
        return runCatching {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }.getOrElse {
            runCatching {
                context.createDeviceProtectedStorageContext()
                    .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            }.getOrNull()
        }
    }
}

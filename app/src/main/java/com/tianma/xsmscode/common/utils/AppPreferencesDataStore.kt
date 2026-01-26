package com.tianma.xsmscode.common.utils

import android.content.Context
import com.tianma.xsmscode.common.constant.PrefConst
import kotlinx.coroutines.flow.first
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

private val Context.appPreferencesDataStore by preferencesDataStore(
    name = "app_preferences"
)

object AppPreferencesDataStore {
    private val backupCompatTipShownKey = booleanPreferencesKey(PrefConst.KEY_BACKUP_COMPAT_TIP_SHOWN)

    suspend fun isBackupCompatTipShown(context: Context): Boolean {
        return context.appPreferencesDataStore.data
            .map { prefs: Preferences -> prefs[backupCompatTipShownKey] ?: false }
            .first()
    }

    suspend fun setBackupCompatTipShown(context: Context, shown: Boolean) {
        context.appPreferencesDataStore.edit { prefs ->
            prefs[backupCompatTipShownKey] = shown
        }
    }

    suspend fun getBoolean(context: Context, key: String, defaultValue: Boolean): Boolean {
        val prefKey = booleanPreferencesKey(key)
        return context.appPreferencesDataStore.data
            .map { prefs: Preferences -> prefs[prefKey] ?: defaultValue }
            .first()
    }

    suspend fun setBoolean(context: Context, key: String, value: Boolean) {
        val prefKey = booleanPreferencesKey(key)
        context.appPreferencesDataStore.edit { prefs ->
            prefs[prefKey] = value
        }
    }

    suspend fun getString(context: Context, key: String, defaultValue: String): String {
        val prefKey = stringPreferencesKey(key)
        return context.appPreferencesDataStore.data
            .map { prefs: Preferences -> prefs[prefKey] ?: defaultValue }
            .first()
    }

    suspend fun setString(context: Context, key: String, value: String) {
        val prefKey = stringPreferencesKey(key)
        context.appPreferencesDataStore.edit { prefs ->
            prefs[prefKey] = value
        }
    }

    suspend fun getInt(context: Context, key: String, defaultValue: Int): Int {
        val prefKey = intPreferencesKey(key)
        return context.appPreferencesDataStore.data
            .map { prefs: Preferences -> prefs[prefKey] ?: defaultValue }
            .first()
    }

    suspend fun setInt(context: Context, key: String, value: Int) {
        val prefKey = intPreferencesKey(key)
        context.appPreferencesDataStore.edit { prefs ->
            prefs[prefKey] = value
        }
    }

    fun getBooleanBlocking(context: Context, key: String, defaultValue: Boolean): Boolean {
        return runBlocking { getBoolean(context, key, defaultValue) }
    }

    fun setBooleanBlocking(context: Context, key: String, value: Boolean) {
        runBlocking { setBoolean(context, key, value) }
    }

    fun getStringBlocking(context: Context, key: String, defaultValue: String): String {
        return runBlocking { getString(context, key, defaultValue) }
    }

    fun setStringBlocking(context: Context, key: String, value: String) {
        runBlocking { setString(context, key, value) }
    }

    fun getIntBlocking(context: Context, key: String, defaultValue: Int): Int {
        return runBlocking { getInt(context, key, defaultValue) }
    }

    fun setIntBlocking(context: Context, key: String, value: Int) {
        runBlocking { setInt(context, key, value) }
    }
}

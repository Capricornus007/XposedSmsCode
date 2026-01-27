package com.tianma.xsmscode.common.utils

import android.content.Context
import com.tianma.xsmscode.common.constant.PrefConst
import com.tianma.xsmscode.common.utils.StorageUtils
import kotlinx.coroutines.flow.first
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.Flow
import java.io.File

private val Context.appPreferencesDataStore by preferencesDataStore(
    name = "app_preferences"
)

object AppPreferencesDataStore {
    private val backupCompatTipShownKey = booleanPreferencesKey(PrefConst.KEY_BACKUP_COMPAT_TIP_SHOWN)
    private const val DATASTORE_FILE_NAME = "app_preferences.preferences_pb"

    private fun getDataStoreFile(context: Context): File {
        return File(context.dataDir, "datastore/$DATASTORE_FILE_NAME")
    }

    private fun ensureDataStoreReadable(context: Context) {
        val file = getDataStoreFile(context)
        StorageUtils.setFileWorldReadable(file, 3)
    }

    fun ensureReadable(context: Context) {
        ensureDataStoreReadable(context)
    }

    suspend fun isBackupCompatTipShown(context: Context): Boolean {
        return context.appPreferencesDataStore.data
            .map { prefs: Preferences -> prefs[backupCompatTipShownKey] ?: false }
            .first()
    }

    suspend fun setBackupCompatTipShown(context: Context, shown: Boolean) {
        context.appPreferencesDataStore.edit { prefs ->
            prefs[backupCompatTipShownKey] = shown
        }
        ensureDataStoreReadable(context)
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
        ensureDataStoreReadable(context)
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
        ensureDataStoreReadable(context)
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
        ensureDataStoreReadable(context)
    }

    fun getBooleanFlow(context: Context, key: String, defaultValue: Boolean): Flow<Boolean> {
        val prefKey = booleanPreferencesKey(key)
        return context.appPreferencesDataStore.data
            .map { prefs: Preferences -> prefs[prefKey] ?: defaultValue }
    }

    fun getStringFlow(context: Context, key: String, defaultValue: String): Flow<String> {
        val prefKey = stringPreferencesKey(key)
        return context.appPreferencesDataStore.data
            .map { prefs: Preferences -> prefs[prefKey] ?: defaultValue }
    }

    fun getIntFlow(context: Context, key: String, defaultValue: Int): Flow<Int> {
        val prefKey = intPreferencesKey(key)
        return context.appPreferencesDataStore.data
            .map { prefs: Preferences -> prefs[prefKey] ?: defaultValue }
    }
}

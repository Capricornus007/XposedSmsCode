package com.tianma.xsmscode.data.prefs

import android.content.Context
import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Binder
import android.os.Process
import com.github.tianma8023.xposed.smscode.BuildConfig
import com.tianma.xsmscode.common.utils.AppPreferencesDataStore
import com.tianma.xsmscode.common.utils.XLog
import kotlinx.coroutines.runBlocking

class PrefsProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<String>?
    ): Int = 0

    override fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?
    ): Cursor? {
        val ctx = context ?: return null
        if (!isCallerAllowed(ctx)) {
            XLog.w("PrefsProvider: deny caller uid=%d", Binder.getCallingUid())
            return null
        }
        val type = sUriMatcher.match(uri)
        val key = uri.getQueryParameter("key") ?: return null
        val defaultValue = uri.getQueryParameter("default")
        val cursor = MatrixCursor(arrayOf(COLUMN_VALUE))

        when (type) {
            TYPE_BOOL -> {
                val def = defaultValue?.toBooleanStrictOrNull() ?: false
                val value = runBlocking { AppPreferencesDataStore.getBoolean(ctx, key, def) }
                cursor.addRow(arrayOf(if (value) "1" else "0"))
            }
            TYPE_STRING -> {
                val def = defaultValue ?: ""
                val value = runBlocking { AppPreferencesDataStore.getString(ctx, key, def) }
                cursor.addRow(arrayOf(value))
            }
            TYPE_INT -> {
                val def = defaultValue?.toIntOrNull() ?: 0
                val value = runBlocking { AppPreferencesDataStore.getInt(ctx, key, def) }
                cursor.addRow(arrayOf(value.toString()))
            }
            else -> return null
        }
        return cursor
    }

    private fun isCallerAllowed(ctx: Context): Boolean {
        val uid = Binder.getCallingUid()
        if (uid == Process.SYSTEM_UID || uid == Process.PHONE_UID) return true
        return uid == ctx.applicationInfo?.uid
    }

    companion object {
        const val AUTHORITY = BuildConfig.APPLICATION_ID + ".pref.provider"
        private const val PATH_BOOL = "bool"
        private const val PATH_STRING = "string"
        private const val PATH_INT = "int"
        private const val TYPE_BOOL = 1
        private const val TYPE_STRING = 2
        private const val TYPE_INT = 3
        private const val COLUMN_VALUE = "value"

        private val sUriMatcher: UriMatcher = UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(AUTHORITY, PATH_BOOL, TYPE_BOOL)
            addURI(AUTHORITY, PATH_STRING, TYPE_STRING)
            addURI(AUTHORITY, PATH_INT, TYPE_INT)
        }

        @JvmField
        val BOOL_URI: Uri = Uri.parse("content://$AUTHORITY/$PATH_BOOL")
        @JvmField
        val STRING_URI: Uri = Uri.parse("content://$AUTHORITY/$PATH_STRING")
        @JvmField
        val INT_URI: Uri = Uri.parse("content://$AUTHORITY/$PATH_INT")
    }
}

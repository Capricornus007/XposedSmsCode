package com.github.magisk317.smscode.migration

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.UriMatcher
import android.content.pm.PackageManager
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Binder
import android.os.ParcelFileDescriptor
import android.os.Process
import com.github.magisk317.smscode.common.utils.JsonUtils
import com.github.magisk317.smscode.common.utils.XLog
import com.github.magisk317.smscode.data.db.AppDatabase
import com.github.magisk317.smscode.data.db.DBManager
import com.github.magisk317.smscode.migration.model.MigrationExportPayload
import com.github.magisk317.smscode.migration.model.MigrationMeta
import com.github.magisk317.smscode.migration.model.toBackupRule
import com.github.magisk317.smscode.migration.model.toBackupSmsRecord
import com.github.magisk317.smscode.migration.model.toMigrationDto
import java.io.File
import java.io.FileNotFoundException

class MigrationProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun getType(uri: Uri): String? = when (sUriMatcher.match(uri)) {
        TYPE_META -> "vnd.android.cursor.item/vnd.$AUTHORITY.meta"
        TYPE_EXPORT -> "application/json"
        else -> null
    }

    override fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?,
    ): Cursor? {
        val ctx = context ?: return null
        enforceCallerAllowed(ctx)
        return when (sUriMatcher.match(uri)) {
            TYPE_META -> buildMetaCursor(ctx)
            else -> null
        }
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val ctx = context ?: throw FileNotFoundException("No context")
        enforceCallerAllowed(ctx)
        if (mode != "r") {
            throw FileNotFoundException("Read only")
        }
        return when (sUriMatcher.match(uri)) {
            TYPE_EXPORT -> {
                val exportFile = writeExportFile(ctx)
                ParcelFileDescriptor.open(exportFile, ParcelFileDescriptor.MODE_READ_ONLY)
            }

            else -> throw FileNotFoundException("Unsupported URI: $uri")
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int = 0

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?): Int = 0

    private fun enforceCallerAllowed(ctx: Context) {
        val uid = Binder.getCallingUid()
        if (uid == Process.myUid()) {
            return
        }
        val pm = ctx.packageManager
        val callerPackages = pm.getPackagesForUid(uid).orEmpty()
        val selfPackage = ctx.packageName
        for (pkg in callerPackages) {
            if (pkg != TARGET_RELAY_PACKAGE) {
                continue
            }
            if (pm.checkSignatures(selfPackage, pkg) == PackageManager.SIGNATURE_MATCH) {
                return
            }
        }
        XLog.w(
            "MigrationProvider denied caller uid=%d packages=%s",
            uid,
            callerPackages.joinToString(","),
        )
        throw SecurityException("Caller is not allowed")
    }

    private fun buildMetaCursor(ctx: Context): Cursor {
        val meta = buildMeta(ctx)
        val columns = arrayOf(
            COLUMN_SCHEMA_VERSION,
            COLUMN_SOURCE_PACKAGE,
            COLUMN_SOURCE_VERSION,
            COLUMN_EXPORTED_AT,
        )
        return MatrixCursor(columns).apply {
            addRow(
                arrayOf<Any?>(
                    meta.schemaVersion,
                    meta.sourcePackage,
                    meta.sourceVersion,
                    meta.exportedAt,
                ),
            )
        }
    }

    private fun buildMeta(ctx: Context): MigrationMeta {
        val exportedAt = System.currentTimeMillis()
        return MigrationMeta(
            schemaVersion = SCHEMA_VERSION,
            sourcePackage = ctx.packageName,
            sourceVersion = resolveSourceVersion(ctx),
            exportedAt = exportedAt,
        )
    }

    private fun writeExportFile(ctx: Context): File {
        val meta = buildMeta(ctx)
        val payload = buildPayload(ctx, meta)
        val exportDir = File(ctx.cacheDir, "migration").apply {
            if (!exists()) {
                mkdirs()
            }
        }
        val exportFile = File(exportDir, "migration-export.json")
        exportFile.outputStream().buffered().use { out ->
            out.write(JsonUtils.toJson(payload).toByteArray(Charsets.UTF_8))
        }
        return exportFile
    }

    private fun buildPayload(ctx: Context, meta: MigrationMeta): MigrationExportPayload {
        val dbManager = DBManager.get(ctx)
        val db = AppDatabase.getInstance(ctx)
        val codeRules = dbManager.queryAllSmsCodeRules().map { it.toBackupRule() }
        val records = dbManager.queryAllSmsMsg().map { it.toBackupSmsRecord() }
        val senders = db.senderDao().getAll().map { it.toMigrationDto() }
        val forwardRules = db.ruleDao().getAll().map { it.toMigrationDto() }
        val forwardFilterRules = db.forwardFilterRuleDao().getAll().map { it.toMigrationDto() }
        val notifyRouteRules = db.notifyRouteRuleDao().getAll().map { it.toMigrationDto() }
        val appInfos = db.appInfoDao().getAll()
        val preferences = readPreferences(ctx)
        return MigrationExportPayload(
            schemaVersion = meta.schemaVersion,
            sourcePackage = meta.sourcePackage,
            sourceVersion = meta.sourceVersion,
            exportedAt = meta.exportedAt,
            preferences = preferences,
            codeRules = codeRules,
            records = records,
            senders = senders,
            forwardRules = forwardRules,
            forwardFilterRules = forwardFilterRules,
            notifyRouteRules = notifyRouteRules,
            appInfos = appInfos,
        )
    }

    private fun readPreferences(ctx: Context): Map<String, String?> {
        val sharedPrefs = ctx.getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE)
        val output = LinkedHashMap<String, String?>()
        for ((key, value) in sharedPrefs.all) {
            if (key.startsWith("internal_")) {
                continue
            }
            output[key] = value?.toString()
        }
        return output
    }

    private fun resolveSourceVersion(ctx: Context): String {
        return runCatching {
            @Suppress("DEPRECATION")
            ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName.orEmpty()
        }.getOrElse {
            XLog.w("MigrationProvider resolve version failed: %s", it.message ?: it.javaClass.simpleName)
            ""
        }
    }

    companion object {
        private const val TARGET_RELAY_PACKAGE = "io.github.magisk317.xinyi.relay"
        private const val SCHEMA_VERSION = 1
        private const val SHARED_PREFS_NAME = "xposed_prefs"

        private const val PATH_META = "meta"
        private const val PATH_EXPORT = "export"
        private const val TYPE_META = 1
        private const val TYPE_EXPORT = 2

        private const val COLUMN_SCHEMA_VERSION = "schemaVersion"
        private const val COLUMN_SOURCE_PACKAGE = "sourcePackage"
        private const val COLUMN_SOURCE_VERSION = "sourceVersion"
        private const val COLUMN_EXPORTED_AT = "exportedAt"

        private const val AUTHORITY = "com.github.tianma8023.xposed.smscode.migration.provider"

        @JvmField
        val META_URI: Uri = Uri.parse("content://$AUTHORITY/$PATH_META")

        @JvmField
        val EXPORT_URI: Uri = Uri.parse("content://$AUTHORITY/$PATH_EXPORT")

        private val sUriMatcher = UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(AUTHORITY, PATH_META, TYPE_META)
            addURI(AUTHORITY, PATH_EXPORT, TYPE_EXPORT)
        }
    }
}

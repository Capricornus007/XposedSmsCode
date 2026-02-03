package com.tianma.xsmscode.feature.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import com.tianma.xsmscode.feature.backup.exception.BackupInvalidException
import com.tianma.xsmscode.feature.backup.exception.VersionInvalidException
import com.tianma.xsmscode.feature.backup.exception.VersionMissedException
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Arrays
import java.util.Date
import java.util.Locale

object BackupManager {

    private const val BACKUP_DIRECTORY = "SmsCode"
    private const val BACKUP_FILE_EXTENSION = ".scebak"
    private const val BACKUP_FILE_NAME_PREFIX = "SmsCode-"

    private const val BACKUP_MIME_TYPE = "application/json"

    @JvmStatic
    fun getBackupDir(context: Context): File {
        val baseDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
        return File(baseDir, BACKUP_DIRECTORY)
    }

    @JvmStatic
    fun getBackupFileExtension(): String = BACKUP_FILE_EXTENSION

    @JvmStatic
    fun getDefaultBackupFilename(context: Context): String {
        val sdf = SimpleDateFormat("yyyyMMdd-HHmm", Locale.getDefault())
        val dateStr = sdf.format(Date())
        val backupDir = getBackupDir(context)
        val basename = BACKUP_FILE_NAME_PREFIX + dateStr
        var filename = basename + BACKUP_FILE_EXTENSION
        var i = 2
        while (File(backupDir, filename).exists()) {
            filename = "$basename-$i$BACKUP_FILE_EXTENSION"
            i++
        }
        return filename
    }

    @JvmStatic
    fun getBackupFiles(context: Context): Array<File>? {
        val backupDir = getBackupDir(context)
        if (!backupDir.exists()) return null
        val files = backupDir.listFiles { _, name -> name.endsWith(BACKUP_FILE_EXTENSION) }

        if (files != null) {
            Arrays.sort(files) { f1: File, f2: File ->
                val s1 = f1.name
                val s2 = f2.name
                val extLength = BACKUP_FILE_EXTENSION.length
                val n1 = s1.substring(0, s1.length - extLength)
                val n2 = s2.substring(0, s2.length - extLength)
                n1.compareTo(n2)
            }
        }
        return files
    }

    @JvmStatic
    fun exportRuleList(file: File, ruleList: List<BackupRule>, appVersion: String): ExportResult {
        val parentFile = file.parentFile
        if (parentFile != null && !parentFile.exists()) {
            parentFile.mkdirs()
        }

        try {
            RuleExporter(file).use { exporter ->
                exporter.doExport(ruleList, appVersion)
                return ExportResult.SUCCESS
            }
        } catch (e: IOException) {
            Log.e("BackupManager", "Export SmsCode rules failed", e)
            return ExportResult.FAILED
        }
    }

    @JvmStatic
    fun exportBackup(
        context: Context,
        uri: Uri,
        ruleList: List<BackupRule>,
        preferences: Map<String, String?>?,
        records: List<BackupSmsRecord>?,
        appVersion: String
    ): ExportResult {
        try {
            RuleExporter(context.contentResolver.openOutputStream(uri)).use { exporter ->
                exporter.doExport(ruleList, appVersion, preferences, records)
                return ExportResult.SUCCESS
            }
        } catch (e: IOException) {
            Log.e("BackupManager", "Export SmsCode backup failed", e)
            return ExportResult.FAILED
        }
    }

    @JvmStatic
    fun exportRuleList(context: Context, uri: Uri, ruleList: List<BackupRule>, appVersion: String): ExportResult {
        return exportBackup(context, uri, ruleList, null, null, appVersion)
    }

    /**
     * 获取导出规则列表的 SAF (Storage Access Framework) 的 Intent
     */
    @JvmStatic
    fun getExportRuleListSAFIntent(context: Context): Intent {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.type = BACKUP_MIME_TYPE
        intent.putExtra(Intent.EXTRA_TITLE, getDefaultBackupFilename(context))

        return intent
    }

    @JvmStatic
    fun importRuleList(context: Context, uri: Uri, currentAppVersion: String): BackupImportResult {
        var ruleImporter: RuleImporter? = null
        try {
            ruleImporter = RuleImporter(context.contentResolver.openInputStream(uri))
            val payload = ruleImporter.parsePayload()
            val schemaVersion = payload.schemaVersion
            if (schemaVersion > BackupConst.BACKUP_VERSION) {
                return BackupImportResult(ImportResult.VERSION_TOO_NEW)
            }
            if (schemaVersion < BackupConst.BACKUP_VERSION) {
                return BackupImportResult(ImportResult.VERSION_TOO_OLD)
            }
            val warning = resolveWarning(payload.appVersion, currentAppVersion)
            return BackupImportResult(
                ImportResult.SUCCESS,
                payload.rules,
                payload.preferences,
                payload.records,
                warning
            )
        } catch (e: IOException) {
            Log.e("BackupManager", "Error occurs in importRuleList", e)
            return BackupImportResult(ImportResult.READ_FAILED)
        } catch (e: VersionMissedException) {
            Log.e("BackupManager", "Error occurs in importRuleList", e)
            return BackupImportResult(ImportResult.VERSION_MISSED)
        } catch (e: VersionInvalidException) {
            Log.e("BackupManager", "Error occurs in importRuleList", e)
            return BackupImportResult(ImportResult.VERSION_UNKNOWN)
        } catch (e: BackupInvalidException) {
            Log.e("BackupManager", "Error occurs in importRuleList", e)
            return BackupImportResult(ImportResult.BACKUP_INVALID)
        } finally {
            if (ruleImporter != null) {
                ruleImporter.close()
            }
        }
    }

    /**
     * 获取导入规则列表的 SAF (Storage Access Framework) 的 Intent
     */
    @JvmStatic
    fun getImportRuleListSAFIntent(context: Context): Intent {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.type = BACKUP_MIME_TYPE
        intent.putExtra(Intent.EXTRA_TITLE, getDefaultBackupFilename(context))

        return intent
    }

    @JvmStatic
    fun shareBackupFile(context: Context, file: File) {
        val intent = Intent(Intent.ACTION_SEND)

        val authority = context.packageName + ".files"
        val uri = FileProvider.getUriForFile(context, authority, file)
        intent.putExtra(Intent.EXTRA_STREAM, uri)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        intent.type = BACKUP_MIME_TYPE
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        context.startActivity(Intent.createChooser(intent, null))
    }

    private fun resolveWarning(backupAppVersion: String, currentAppVersion: String): ImportWarning? {
        val backupMajor = backupAppVersion.split(".").firstOrNull()?.toIntOrNull()
        val currentMajor = currentAppVersion.split(".").firstOrNull()?.toIntOrNull()
        if (backupMajor == null || currentMajor == null) return null
        return if (backupMajor != currentMajor) ImportWarning.APP_VERSION_MISMATCH else null
    }
}

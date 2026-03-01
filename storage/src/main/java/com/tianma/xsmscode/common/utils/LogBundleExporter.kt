package com.tianma.xsmscode.common.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object LogBundleExporter {
    private const val ZIP_MIME_TYPE = "application/zip"
    private val LSPOSED_LOG_DIRS = listOf(
        "/data/adb/lspd/log",
        "/data/adb/lspd/log.old",
    )

    data class ExportResult(
        val file: File?,
        val details: String,
    )

    data class ClearResult(
        val success: Boolean,
        val details: String,
    )

    /**
     * Build a zip bundle containing app logs + LSPosed logs.
     * Returns [ExportResult.file] as null when build fails.
     */
    fun buildLogBundle(context: Context): ExportResult {
        val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())
        val docsDir = StorageUtils.getPublicDocumentsDir(context)
        val exportDir = File(docsDir, "logs").apply { mkdirs() }
        val stagingDir = File(exportDir, ".tmp_logs_$timestamp").apply {
            if (exists()) deleteRecursively()
            mkdirs()
        }
        val details = mutableListOf<String>()

        try {
            val appLogSrc = StorageUtils.getLogDir(context)
            if (appLogSrc != null && appLogSrc.exists()) {
                copyDirectory(appLogSrc, File(stagingDir, "app/log"))
                details += "app log: ${appLogSrc.absolutePath}"
            } else {
                details += "app log missing"
            }

            val crashLogSrc = StorageUtils.getCrashLogDir(context)
            if (crashLogSrc != null && crashLogSrc.exists()) {
                copyDirectory(crashLogSrc, File(stagingDir, "app/crash"))
                details += "crash log: ${crashLogSrc.absolutePath}"
            } else {
                details += "crash log missing"
            }

            val lsposedCopied = copyLsposedLogs(stagingDir, details)
            if (!lsposedCopied) {
                details += "lsposed log missing or unreadable"
            }

            File(stagingDir, "summary.txt").writeText(
                buildString {
                    appendLine("Export Time: $timestamp")
                    appendLine("Package: ${context.packageName}")
                    details.forEach { appendLine("- $it") }
                },
            )

            val zipFile = File(exportDir, "logs_$timestamp.zip")
            zipDirectory(stagingDir, zipFile)
            StorageUtils.setFileWorldReadable(zipFile, 2)
            return ExportResult(zipFile, details.joinToString("; "))
        } catch (t: Throwable) {
            XLog.e("buildLogBundle failed", t)
            return ExportResult(null, t.message ?: t.javaClass.simpleName)
        } finally {
            runCatching { stagingDir.deleteRecursively() }
        }
    }

    fun shareLogBundle(context: Context, file: File) {
        val authority = context.packageName + ".files"
        val uri: Uri = FileProvider.getUriForFile(context, authority, file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = ZIP_MIME_TYPE
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /**
     * Clear app log related directories (log/crash/exported logs).
     * Keeps directory nodes and removes files/sub-directories inside.
     */
    fun clearLogFolders(context: Context): ClearResult {
        val details = mutableListOf<String>()
        var success = true

        RuntimeLogStore.clear()

        val targets = listOf(
            "log" to StorageUtils.getLogDir(context),
            "crash" to StorageUtils.getCrashLogDir(context),
            "documents/logs" to File(StorageUtils.getPublicDocumentsDir(context), "logs"),
        )

        targets.forEach { (name, dir) ->
            if (dir == null) {
                success = false
                details += "$name unavailable"
                return@forEach
            }
            val ok = clearDirectoryContents(dir)
            if (ok) {
                details += "$name cleared"
            } else {
                success = false
                details += "$name clear failed"
            }
        }

        return ClearResult(success = success, details = details.joinToString("; "))
    }

    private fun copyLsposedLogs(stagingDir: File, details: MutableList<String>): Boolean {
        val lsposedTargetRoot = File(stagingDir, "lsposed")
        var copied = false

        LSPOSED_LOG_DIRS.forEach { path ->
            val src = File(path)
            if (src.exists() && src.canRead()) {
                val target = File(lsposedTargetRoot, src.name)
                copyDirectory(src, target)
                details += "lsposed direct: $path"
                copied = true
            }
        }
        if (copied) return true

        val targetPath = lsposedTargetRoot.absolutePath
        val shellCmd = buildString {
            append("mkdir -p '$targetPath'; ")
            LSPOSED_LOG_DIRS.forEach { path ->
                append("if [ -d '$path' ]; then cp -a '$path' '$targetPath/'; fi; ")
            }
        }
        val suResult = runSuCommand(shellCmd)
        if (suResult.exitCode == 0) {
            val hasAny = lsposedTargetRoot.exists() &&
                (lsposedTargetRoot.listFiles()?.isNotEmpty() == true)
            if (hasAny) {
                details += "lsposed copied via su"
                return true
            }
        }
        details += "lsposed su failed: ${suResult.stderr.ifBlank { suResult.stdout }.ifBlank { "unknown" }}"
        return false
    }

    private data class ShellResult(
        val exitCode: Int,
        val stdout: String,
        val stderr: String,
    )

    private fun runSuCommand(command: String): ShellResult = try {
        val process = ProcessBuilder("su", "-c", command).start()
        val stdout = process.inputStream.bufferedReader().use { it.readText() }
        val stderr = process.errorStream.bufferedReader().use { it.readText() }
        val exitCode = process.waitFor()
        ShellResult(exitCode, stdout, stderr)
    } catch (t: Throwable) {
        ShellResult(-1, "", t.message ?: t.javaClass.simpleName)
    }

    private fun copyDirectory(source: File, target: File) {
        source.walkTopDown().forEach { file ->
            val relative = file.relativeTo(source).path
            val dest = if (relative.isEmpty()) target else File(target, relative)
            if (file.isDirectory) {
                if (!dest.exists()) dest.mkdirs()
            } else {
                dest.parentFile?.mkdirs()
                file.copyTo(dest, overwrite = true)
            }
        }
    }

    private fun zipDirectory(sourceDir: File, outputZip: File) {
        FileOutputStream(outputZip).use { fos ->
            ZipOutputStream(fos).use { zos ->
                sourceDir.walkTopDown()
                    .filter { it.isFile }
                    .forEach { file ->
                        val entryName = file.relativeTo(sourceDir).invariantSeparatorsPath
                        zos.putNextEntry(ZipEntry(entryName))
                        file.inputStream().use { input -> input.copyTo(zos) }
                        zos.closeEntry()
                    }
            }
        }
    }

    private fun clearDirectoryContents(dir: File): Boolean {
        return runCatching {
            if (!dir.exists()) {
                dir.mkdirs()
                return@runCatching true
            }
            var ok = true
            dir.listFiles().orEmpty().forEach { child ->
                if (!child.deleteRecursively()) {
                    ok = false
                }
            }
            if (!dir.exists() && !dir.mkdirs()) ok = false
            ok
        }.getOrDefault(false)
    }
}

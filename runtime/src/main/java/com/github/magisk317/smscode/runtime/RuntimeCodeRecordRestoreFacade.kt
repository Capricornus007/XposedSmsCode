package com.github.magisk317.smscode.runtime

import android.content.Context
import com.github.magisk317.smscode.common.constant.PrefConst
import com.github.magisk317.smscode.common.utils.XLog
import com.github.magisk317.smscode.data.db.DBManager
import com.github.magisk317.smscode.data.db.entity.SmsMsg
import io.github.magisk317.smscode.runtime.common.utils.JsonUtils
import io.github.magisk317.smscode.runtime.common.utils.StorageUtils
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import io.github.magisk317.xposed.logging.MagiskOtel

object RuntimeCodeRecordRestoreFacade : com.github.magisk317.smscode.runtime.bridge.HookCodeRecordAccess {
    private const val RECORD_FILE_PREFIX = "CodeRecord_"

    override fun exportToFile(context: Context, smsMsg: SmsMsg): Boolean {
        val startedAt = System.nanoTime()
        var caught: Throwable? = null
        val ok = runCatching {
            val filename = RECORD_FILE_PREFIX + smsMsg.date
            val recordFile = File(StorageUtils.getFilesDir(context), filename)
            OutputStreamWriter(FileOutputStream(recordFile), StandardCharsets.UTF_8).use { writer ->
                JsonUtils.toJson(smsMsg, writer, true)
            }
            true
        }.onFailure { e ->
            caught = e
            XLog.e("Export code record to file failed", e)
        }.getOrDefault(false)
        emitRecord(
            result = if (ok) "ok" else "error",
            reason = if (ok) "export_file" else "export_file_failed",
            statusOk = ok,
            durationMs = ((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L),
            extra = if (!ok && caught != null) {
                val c = caught
                mapOf(
                    "error_class" to safeErrorClass(c),
                    "error_msg" to safeErrorMessage(c),
                )
            } else {
                emptyMap()
            },
        )
        return ok
    }

    fun importToDatabase(context: Context): Boolean {
        val startedAt = System.nanoTime()
        var importedCount = 0
        var caught: Throwable? = null
        val ok = runCatching {
            val recordFiles = getRecordFiles(context)
            val smsMsgList = mutableListOf<SmsMsg>()
            recordFiles?.forEach { recordFile ->
                val smsMsg = loadFromFile(recordFile)
                if (smsMsg != null) {
                    smsMsgList.add(smsMsg)
                    recordFile.delete()
                }
            }

            if (smsMsgList.isNotEmpty()) {
                val dbManager = DBManager.get(context)
                dbManager.addSmsMsgList(smsMsgList)
                importedCount = smsMsgList.size
                XLog.d("Import code records to database succeed")

                val allMsgList = dbManager.queryAllSmsMsg()
                if (allMsgList.size > PrefConst.MAX_SMS_RECORDS_COUNT_DEFAULT) {
                    val outdatedMsgList = allMsgList.subList(PrefConst.MAX_SMS_RECORDS_COUNT_DEFAULT, allMsgList.size)
                    dbManager.removeSmsMsgList(outdatedMsgList)
                    XLog.d("Remove outdated code records succeed")
                }
            }
            true
        }.onFailure { e ->
            caught = e
            XLog.e("Import code records to database failed.", e)
        }.getOrDefault(false)
        emitRecord(
            result = if (ok) "ok" else "error",
            reason = if (ok) "import_db" else "import_db_failed",
            statusOk = ok,
            durationMs = ((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L),
            extra = mapOf("imported_count" to importedCount.toString()).let { base ->
                if (!ok && caught != null) {
                    val c = caught
                    base + mapOf(
                        "error_class" to safeErrorClass(c),
                        "error_msg" to safeErrorMessage(c),
                    )
                } else base
            },
        )
        return ok
    }

    private fun emitRecord(
        result: String,
        reason: String,
        statusOk: Boolean = true,
        durationMs: Long = 0L,
        extra: Map<String, String> = emptyMap(),
    ) {
        val attrs = linkedMapOf(
            "result" to result,
            "duration_ms" to durationMs.toString(),
            "process" to "app",
            "stage" to "code_record_restore",
            "reason" to reason,
        )
        attrs.putAll(extra)
        MagiskOtel.event(name = "sms.record", attributes = attrs, statusOk = statusOk)
    }

    fun getRecordFiles(context: Context): Array<File>? {
        val filesDir = StorageUtils.getFilesDir(context)
        return filesDir.listFiles { _, name -> name.startsWith(RECORD_FILE_PREFIX) }
    }

    private fun loadFromFile(recordFile: File): SmsMsg? {
        return runCatching {
            InputStreamReader(FileInputStream(recordFile), StandardCharsets.UTF_8).use { reader ->
                JsonUtils.entityFromJson(reader, SmsMsg::class.java, true)
            }
        }.onFailure { XLog.e("Load code record from file failed", it) }
            .getOrNull()
    }

    /**
     * Throwable class name for telemetry. Framework exceptions (IOException,
     * FileNotFoundException, SecurityException, ...) keep their real name at
     * runtime; the kit's consumer-rules keep Throwable names from R8 in release
     * builds, so this stays meaningful after obfuscation. Not the `reason`
     * attribute, so it passes the hygiene gate untouched.
     */
    private fun safeErrorClass(t: Throwable): String {
        val name = t.javaClass.simpleName
        return name.ifBlank { t.javaClass.name }
    }

    /**
     * Best-effort error message for telemetry. Collapses newlines and caps length
     * so a verbose exception does not bloat the span; the kit truncates again at
     * the attribute byte limit as a safety net.
     */
    private fun safeErrorMessage(t: Throwable): String {
        val raw = (t.message ?: t.javaClass.name).replace('\n', ' ').replace('\r', ' ')
        return if (raw.length > 200) raw.substring(0, 200) else raw
    }
}

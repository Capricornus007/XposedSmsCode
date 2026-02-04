package com.tianma.xsmscode.feature.backup

import com.tianma.xsmscode.common.utils.JsonUtils
import com.tianma.xsmscode.feature.backup.exception.BackupInvalidException
import com.tianma.xsmscode.feature.backup.exception.VersionInvalidException
import com.tianma.xsmscode.feature.backup.exception.VersionMissedException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

/**
 * SmsCode rule importer
 */
class RuleImporter(private val mJsonStream: InputStream?) : Closeable {

    constructor(file: File?) : this(FileInputStream(file))

    /**
     * parse import data from backup file.
     */
    @Throws(BackupInvalidException::class)
    fun parsePayload(): BackupParseResult {
        val jsonText = try {
            InputStreamReader(mJsonStream, StandardCharsets.UTF_8).use { it.readText() }
        } catch (ex: Exception) {
            throw BackupInvalidException(ex)
        }

        try {
            val jsonElement = JsonUtils.json.parseToJsonElement(jsonText)
            val jsonObject = jsonElement as? JsonObject ?: throw BackupInvalidException()

            val schemaVersion = readSchemaVersion(jsonObject)
            val appVersion = readAppVersion(jsonObject)
            val rules = if (schemaVersion >= BackupConst.BACKUP_VERSION) {
                readRuleList(jsonObject)
            } else {
                if (schemaVersion == 1) {
                    readRuleList(jsonObject)
                } else {
                    emptyList()
                }
            }
            val preferences = if (schemaVersion >= 2) readPreferences(jsonObject) else null
            val records = if (schemaVersion >= 2) readRecords(jsonObject) else null

            return BackupParseResult(schemaVersion, appVersion, rules, preferences, records)
        } catch (ex: SerializationException) {
            throw BackupInvalidException(ex)
        } catch (ex: VersionInvalidException) {
            throw ex
        } catch (ex: VersionMissedException) {
            throw ex
        } catch (ex: Exception) {
            throw BackupInvalidException(ex)
        }
    }

    @Throws(BackupInvalidException::class)
    private fun readRuleList(jsonObject: JsonObject): List<BackupRule> {
        val ruleArray = jsonObject[BackupConst.KEY_RULES]?.jsonArray ?: return emptyList()
        val ruleList = ArrayList<BackupRule>()
        for (ruleJson in ruleArray) {
            ruleList.add(readRule(ruleJson.jsonObject))
        }
        return ruleList
    }

    @Throws(BackupInvalidException::class)
    private fun readRule(ruleObject: JsonObject): BackupRule = try {
        val company = ruleObject[BackupConst.KEY_COMPANY]?.jsonPrimitive?.content
        val codeKeyword = ruleObject[BackupConst.KEY_CODE_KEYWORD]?.jsonPrimitive?.content ?: ""
        val codeRegex = ruleObject[BackupConst.KEY_CODE_REGEX]?.jsonPrimitive?.content ?: ""

        BackupRule(company = company, codeKeyword = codeKeyword, codeRegex = codeRegex)
    } catch (e: Exception) {
        throw BackupInvalidException(e)
    }

    private fun readSchemaVersion(jsonObject: JsonObject): Int {
        val schemaElement = jsonObject[BackupConst.KEY_SCHEMA_VERSION]
        val versionElement = jsonObject[BackupConst.KEY_VERSION]
        val resolved = schemaElement ?: versionElement
            ?: throw VersionMissedException("Backup version property missed")
        return resolved.jsonPrimitive.intOrNull
            ?: throw VersionInvalidException("Invalid backup version")
    }

    private fun readAppVersion(jsonObject: JsonObject): String =
        jsonObject[BackupConst.KEY_APP_VERSION]?.jsonPrimitive?.content ?: ""

    private fun readPreferences(jsonObject: JsonObject): Map<String, String?>? = try {
        val prefObject = jsonObject[BackupConst.KEY_PREFERENCES]?.jsonObject
        prefObject?.let { obj ->
            val map = HashMap<String, String?>()
            for ((key, element) in obj) {
                if (element.jsonPrimitive.isString) {
                    map[key] = element.jsonPrimitive.content
                } else {
                    // Convert other types to string for simplicity, or handle nulls
                    map[key] = element.jsonPrimitive.contentOrNull
                }
            }
            map
        }
    } catch (ignored: Exception) {
        null
    }

    private fun readRecords(jsonObject: JsonObject): List<BackupSmsRecord>? = try {
        val recordArray = jsonObject[BackupConst.KEY_RECORDS]?.jsonArray
        recordArray?.map { element ->
            val obj = element.jsonObject
            val datePrimitive = obj["date"]?.jsonPrimitive
            BackupSmsRecord(
                sender = obj["sender"]?.jsonPrimitive?.contentOrNull,
                body = obj["body"]?.jsonPrimitive?.contentOrNull,
                date = datePrimitive?.longOrNull
                    ?: datePrimitive?.contentOrNull?.toLongOrNull()
                    ?: 0L,
                company = obj["company"]?.jsonPrimitive?.contentOrNull,
                smsCode = obj["code"]?.jsonPrimitive?.contentOrNull,
                packageName = obj["packageName"]?.jsonPrimitive?.contentOrNull,
            )
        }
    } catch (ignored: Exception) {
        null
    }

    override fun close() {
        if (mJsonStream != null) {
            try {
                mJsonStream.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }
}

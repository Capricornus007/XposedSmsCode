package com.github.magisk317.smscode.common.utils

import android.content.Context
import com.github.magisk317.smscode.data.db.DBProvider
import com.github.magisk317.smscode.data.db.entity.SmsCodeRule
import com.github.magisk317.smscode.feature.store.EntityStoreManager
import com.github.magisk317.smscode.feature.store.EntityType
import io.github.magisk317.smscode.domain.model.AppLabelResolver
import io.github.magisk317.smscode.domain.model.SmsCodeRuleSpec
import com.github.magisk317.smscode.common.utils.XLog

object SmsCodeUtils {
    private const val COLUMN_COMPANY = "company"
    private const val COLUMN_KEYWORD = "code_keyword"
    private const val COLUMN_REGEX = "code_regex"

    private suspend fun loadCodeKeywordsBySP(context: Context): String? = PrefsReader.getSMSCodeKeywords(context)

    suspend fun parseSmsCodeIfExists(context: Context, content: String): String {
        return io.github.magisk317.smscode.domain.utils.SmsCodeUtils.parseSmsCodeIfExists(
            content = content,
            keywordsRegex = loadCodeKeywordsBySP(context).orEmpty(),
            rules = queryAllSmsCodeRules(context).map { it.toSpec() },
        )
    }

    @JvmStatic
    fun parseCompany(content: String): String =
        io.github.magisk317.smscode.domain.utils.SmsCodeUtils.parseCompany(content)

    @JvmStatic
    fun parseCompanyCandidates(content: String): List<String> =
        io.github.magisk317.smscode.domain.utils.SmsCodeUtils.parseCompanyCandidates(content)

    fun findPackageNameByLabel(context: Context, label: String?): String? {
        return io.github.magisk317.smscode.domain.utils.SmsCodeUtils.findPackageNameByLabel(
            label = label,
            resolver = AppLabelResolver { target ->
                resolvePackageNameByLabel(context, target)
            },
        )
    }

    private fun resolvePackageNameByLabel(context: Context, label: String): String? {
        return try {
            val pm = context.packageManager
            val apps = pm.getInstalledApplications(android.content.pm.PackageManager.MATCH_ALL)
            for (app in apps) {
                if (pm.getApplicationLabel(app).toString().equals(label, ignoreCase = true)) {
                    return app.packageName
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun queryAllSmsCodeRules(context: Context): List<SmsCodeRule> {
        var rules: List<SmsCodeRule>
        try {
            val smsCodeRuleUri = DBProvider.smsCodeRuleContentUri(context)
            val projection = arrayOf(COLUMN_COMPANY, COLUMN_KEYWORD, COLUMN_REGEX)
            val cursor = context.contentResolver.query(smsCodeRuleUri, projection, null, null, null)
            if (cursor != null) {
                val resultRules = mutableListOf<SmsCodeRule>()
                while (cursor.moveToNext()) {
                    resultRules.add(
                        SmsCodeRule(
                            company = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_COMPANY)),
                            codeKeyword = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_KEYWORD)),
                            codeRegex = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_REGEX)),
                        ),
                    )
                }
                cursor.close()
                rules = if (resultRules.isNotEmpty()) {
                    XLog.d("Load SmsCode rules succeed by content provider")
                    resultRules
                } else {
                    EntityStoreManager.loadEntitiesFromFile(
                        context,
                        EntityType.CODE_RULES,
                        SmsCodeRule::class.java,
                    ).also {
                        XLog.w("Load SmsCode rules by file: provider returned empty result")
                    }
                }
            } else {
                throw IllegalStateException("Cursor is null for URI: $smsCodeRuleUri")
            }
        } catch (_: Throwable) {
            rules = EntityStoreManager.loadEntitiesFromFile(
                context,
                EntityType.CODE_RULES,
                SmsCodeRule::class.java,
            )
            XLog.d("Load SmsCode rules by file")
        }
        return rules
    }

    private fun SmsCodeRule.toSpec(): SmsCodeRuleSpec =
        SmsCodeRuleSpec(
            company = company,
            codeKeyword = codeKeyword,
            codeRegex = codeRegex,
        )
}

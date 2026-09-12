package io.github.capricornus007.smscode.feature.backup

import kotlinx.serialization.Serializable

@Serializable
data class BackupRule(val company: String? = null, val codeKeyword: String = "", val codeRegex: String = "")

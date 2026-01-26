package com.tianma.xsmscode.feature.backup

import kotlinx.serialization.Serializable

@Serializable
data class BackupRule(
    val company: String? = null,
    val codeKeyword: String = "",
    val codeRegex: String = ""
)

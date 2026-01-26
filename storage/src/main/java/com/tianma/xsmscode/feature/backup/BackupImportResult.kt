package com.tianma.xsmscode.feature.backup

data class BackupImportResult(
    val result: ImportResult,
    val rules: List<BackupRule> = emptyList(),
    val warning: ImportWarning? = null
)

package com.tianma.xsmscode.feature.backup

data class BackupParseResult(val schemaVersion: Int, val appVersion: String, val rules: List<BackupRule>)

package com.tianma.xsmscode.feature.backup

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class BackupSmsRecord(
    @SerialName("sender")
    val sender: String? = null,
    @SerialName("body")
    val body: String? = null,
    @SerialName("date")
    val date: Long = 0,
    @SerialName("company")
    val company: String? = null,
    @SerialName("code")
    val smsCode: String? = null,
    @SerialName("packageName")
    val packageName: String? = null,
)

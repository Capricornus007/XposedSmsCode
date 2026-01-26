package com.tianma.xsmscode.data.http.entity

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GithubRelease(
    @SerialName("tag_name")
    val tagName: String? = null,
    @SerialName("name")
    val name: String? = null,
    @SerialName("body")
    val body: String? = null
)

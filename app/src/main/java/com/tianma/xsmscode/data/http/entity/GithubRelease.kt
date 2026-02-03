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
    val body: String? = null,
    @SerialName("assets")
    val assets: List<GithubReleaseAsset>? = null,
)

@Serializable
data class GithubReleaseAsset(
    @SerialName("name")
    val name: String? = null,
    @SerialName("browser_download_url")
    val browserDownloadUrl: String? = null,
    @SerialName("content_type")
    val contentType: String? = null,
)

package com.tianma.xsmscode.data.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import kotlin.math.max

data class GithubReleaseInfo(
    val versionName: String,
    val htmlUrl: String,
)

object GithubUpdateChecker {

    private const val DEFAULT_RELEASE_HTML_URL =
        "https://github.com/magisk317/XposedSmsCode/releases/latest"

    private const val LATEST_RELEASE_API =
        "https://smscode.usdt.edu.kg/repos/magisk317/XposedSmsCode/releases/latest"

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    suspend fun fetchLatestRelease(): GithubReleaseInfo? = fetchLatestReleaseWithRequester { apiUrl ->
        requestReleaseJson(apiUrl)
    }

    suspend fun fetchLatestReleaseWithRequester(
        requestReleaseJson: suspend (String) -> String?,
    ): GithubReleaseInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val body = requestReleaseJson(LATEST_RELEASE_API) ?: return@runCatching null
            parseLatestReleaseJson(body)
        }.getOrNull()
    }

    private fun requestReleaseJson(apiUrl: String): String? {
        val request = Request.Builder()
            .url(apiUrl)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "XposedSmsCode")
            .get()
            .build()

        return runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                response.body.string()
            }
        }.getOrNull()
    }

    fun isNewer(currentVersion: String, latestVersion: String): Boolean =
        compareVersions(currentVersion, latestVersion) < 0

    fun parseLatestReleaseJson(body: String): GithubReleaseInfo? {
        val root = runCatching { Json.parseToJsonElement(body) }.getOrNull() ?: return null
        if (root !is JsonObject) return null

        val rawTag = root.stringOrBlank("tag_name")
        val normalizedVersion = rawTag.trim().removePrefix("v").removePrefix("V")
        if (normalizedVersion.isBlank()) return null
        val htmlUrl = root.stringOrBlank("html_url").ifBlank { DEFAULT_RELEASE_HTML_URL }
        return GithubReleaseInfo(normalizedVersion, htmlUrl)
    }

    private fun JsonObject.stringOrBlank(key: String): String =
        runCatching { (this[key] ?: return "").toUnquotedString() }.getOrDefault("")

    private fun JsonElement.toUnquotedString(): String {
        val raw = toString()
        return if (raw.length >= 2 && raw.startsWith("\"") && raw.endsWith("\"")) {
            raw.substring(1, raw.length - 1)
        } else {
            raw
        }
    }

    private fun compareVersions(currentVersion: String, latestVersion: String): Int {
        val currentParts = extractVersionParts(currentVersion)
        val latestParts = extractVersionParts(latestVersion)
        val length = max(currentParts.size, latestParts.size)
        for (index in 0 until length) {
            val current = if (index < currentParts.size) currentParts[index] else 0
            val latest = if (index < latestParts.size) latestParts[index] else 0
            if (current < latest) return -1
            if (current > latest) return 1
        }
        return 0
    }

    private fun extractVersionParts(version: String): List<Int> =
        VERSION_PART_REGEX.findAll(version)
            .mapNotNull { part -> part.value.toIntOrNull() }
            .toList()

    private val VERSION_PART_REGEX = Regex("\\d+")
}

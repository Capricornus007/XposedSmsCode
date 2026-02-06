package com.tianma.xsmscode.data.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.math.max

data class GithubReleaseInfo(
    val versionName: String,
    val htmlUrl: String,
)

object GithubUpdateChecker {

    private const val LATEST_RELEASE_API =
        "https://smscode.usdt.edu.kg/repos/magisk317/XposedSmsCode/releases/latest"

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    suspend fun fetchLatestRelease(): GithubReleaseInfo? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(LATEST_RELEASE_API)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "XposedSmsCode")
            .get()
            .build()

        runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val body = response.body.string()
                val json = JSONObject(body)
                val rawTag = json.optString("tag_name").orEmpty()
                val normalizedVersion = rawTag.trim().removePrefix("v").removePrefix("V")
                if (normalizedVersion.isBlank()) return@use null
                val htmlUrl = json.optString("html_url").ifBlank {
                    "https://github.com/magisk317/XposedSmsCode/releases/latest"
                }
                GithubReleaseInfo(normalizedVersion, htmlUrl)
            }
        }.getOrNull()
    }

    fun isNewer(currentVersion: String, latestVersion: String): Boolean =
        compareVersions(currentVersion, latestVersion) < 0

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

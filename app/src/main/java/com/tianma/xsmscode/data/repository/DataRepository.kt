package com.tianma.xsmscode.data.repository

import com.tianma.xsmscode.data.db.entity.ApkVersion
import com.tianma.xsmscode.data.http.ApiConst
import com.tianma.xsmscode.data.http.NetworkResult
import com.tianma.xsmscode.data.http.service.GithubService
import com.tianma.xsmscode.data.http.service.ServiceGenerator
import com.tianma.xsmscode.data.http.toNetworkError
import java.util.Locale

object DataRepository {

    private fun isInChina(): Boolean {
        val locale = Locale.getDefault()
        val language = locale.language
        return "zh".equals(language, ignoreCase = true)
    }

    suspend fun getLatestVersion(): NetworkResult<ApkVersion> {
        val isInChina = isInChina()

        val githubService = ServiceGenerator.getInstance()
            .createService(ApiConst.GITHUB_BASE_URL, GithubService::class.java)

        return try {
            NetworkResult.Success(getFromGithub(githubService, isInChina))
        } catch (e: Exception) {
            e.toNetworkError().let { NetworkResult.Error(it) }
        }
    }

    private suspend fun getFromGithub(service: GithubService, isInChina: Boolean): ApkVersion {
        val release = service.getLatestRelease(ApiConst.GITHUB_USERNAME, ApiConst.GITHUB_REPO_NAME)
        val regex = "<br/>|<br>"
        val body = release.body ?: ""
        val arr = body.split(regex.toRegex()).toTypedArray()
        val versionInfo: String = if (arr.size >= 2) {
            if (isInChina) arr[1].trim() else arr[0].trim()
        } else {
            body.replace(regex.toRegex(), "")
        }
        val downloadUrl = release.assets
            ?.mapNotNull { it.browserDownloadUrl?.let { url -> it.name to url } }
            ?.let { assets ->
                assets.firstOrNull { (name, _) ->
                    name?.endsWith(".apk", ignoreCase = true) == true &&
                        name.contains("universal", ignoreCase = true)
                } ?: assets.firstOrNull { (name, _) ->
                    name?.endsWith(".apk", ignoreCase = true) == true
                }
            }?.second

        return ApkVersion(release.name ?: "", versionInfo, downloadUrl)
    }
}

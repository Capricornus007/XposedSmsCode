package com.tianma.xsmscode.data.update

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException

class GithubUpdateCheckerTest {

    @Test
    fun parseLatestReleaseJson_parsesVersionAndUrl() {
        val json = """{"tag_name":"v3.1.1","html_url":"https://github.com/magisk317/XposedSmsCode/releases/tag/v3.1.1"}"""

        val release = GithubUpdateChecker.parseLatestReleaseJson(json)

        assertNotNull(release)
        assertEquals("3.1.1", release?.versionName)
        assertEquals(
            "https://github.com/magisk317/XposedSmsCode/releases/tag/v3.1.1",
            release?.htmlUrl,
        )
    }

    @Test
    fun parseLatestReleaseJson_usesFallbackUrlWhenMissing() {
        val json = """{"tag_name":"V3.1.1"}"""

        val release = GithubUpdateChecker.parseLatestReleaseJson(json)

        assertNotNull(release)
        assertEquals("3.1.1", release?.versionName)
        assertEquals("https://github.com/magisk317/XposedSmsCode/releases/latest", release?.htmlUrl)
    }

    @Test
    fun parseLatestReleaseJson_returnsNullWhenTagMissing() {
        val json = """{"name":"release-without-tag"}"""

        val release = GithubUpdateChecker.parseLatestReleaseJson(json)

        assertNull(release)
    }

    @Test
    fun isNewer_comparesSemanticLikeVersions() {
        assertTrue(GithubUpdateChecker.isNewer("3.1.0", "3.1.1"))
        assertFalse(GithubUpdateChecker.isNewer("3.1.1", "3.1.1"))
        assertFalse(GithubUpdateChecker.isNewer("3.1.2", "3.1.1"))
        assertTrue(GithubUpdateChecker.isNewer("3.1", "3.1.1"))
        assertTrue(GithubUpdateChecker.isNewer("v3.1.0", "V3.2.0-beta1"))
    }

    @Test
    fun fetchLatestReleaseWithRequester_returnsNullOnRequesterFailure() {
        val release = kotlinx.coroutines.runBlocking {
            GithubUpdateChecker.fetchLatestReleaseWithRequester {
                throw IOException("network down")
            }
        }
        assertNull(release)
    }

    @Test
    fun fetchLatestReleaseWithRequester_returnsNullOnMissingTag() {
        val release = kotlinx.coroutines.runBlocking {
            GithubUpdateChecker.fetchLatestReleaseWithRequester {
                """{"name":"release-without-tag"}"""
            }
        }
        assertNull(release)
    }

    @Test
    fun fetchLatestReleaseWithRequester_returnsParsedReleaseOnSuccess() {
        val release = kotlinx.coroutines.runBlocking {
            GithubUpdateChecker.fetchLatestReleaseWithRequester {
                """{"tag_name":"v9.9.9","html_url":"https://github.com/magisk317/XposedSmsCode/releases/tag/v9.9.9"}"""
            }
        }
        assertNotNull(release)
        assertEquals("9.9.9", release?.versionName)
    }
}

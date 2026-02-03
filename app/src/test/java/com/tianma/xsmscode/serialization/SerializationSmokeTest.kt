package com.tianma.xsmscode.serialization

import com.tianma.xsmscode.common.utils.JsonUtils
import com.tianma.xsmscode.data.http.entity.GithubRelease
import com.tianma.xsmscode.feature.backup.BackupPayload
import com.tianma.xsmscode.feature.backup.BackupRule
import com.tianma.xsmscode.feature.backup.RuleExporter
import com.tianma.xsmscode.feature.backup.RuleImporter
import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class SerializationSmokeTest {

    @Test
    fun backupExportRoundTrip() {
        val rules = listOf(BackupRule(company = "ACME", codeKeyword = "code", codeRegex = "\\d{6}"))
        val output = ByteArrayOutputStream()

        RuleExporter(output).use { exporter ->
            exporter.doExport(rules, "3.0.0")
        }

        val payload = JsonUtils.json.decodeFromString<BackupPayload>(output.toString(Charsets.UTF_8.name()))
        assertEquals(1, payload.version)
        assertEquals(1, payload.schemaVersion)
        assertEquals(1, payload.rules.size)
        assertEquals("ACME", payload.rules.first().company)
    }

    @Test
    fun backupImportParsesRules() {
        val payload = BackupPayload(
            rules = listOf(BackupRule(company = "ACME", codeKeyword = "code", codeRegex = "\\d{6}"))
        )
        val json = JsonUtils.json.encodeToString(BackupPayload.serializer(), payload)
        val input = ByteArrayInputStream(json.toByteArray(Charsets.UTF_8))

        RuleImporter(input).use { importer ->
            val payload = importer.parsePayload()
            assertEquals(1, payload.schemaVersion)
            assertEquals(1, payload.rules.size)
            assertEquals("ACME", payload.rules.first().company)
        }
    }

    @Test
    fun githubReleaseParsing() {
        val json = """{"tag_name":"v1.2.3","name":"Release","body":"Notes"}"""
        val release = JsonUtils.json.decodeFromString<GithubRelease>(json)
        assertNotNull(release)
        assertEquals("v1.2.3", release.tagName)
    }
}

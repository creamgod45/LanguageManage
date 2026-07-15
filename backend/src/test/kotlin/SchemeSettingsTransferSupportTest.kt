package cg.creamgod45

import cg.creamgod45.localization.LanguageSchemeDto
import cg.creamgod45.localization.SchemeSettingsTransferDto
import cg.creamgod45.localization.UsageScanSettingsDto
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SchemeSettingsTransferSupportTest {
    private val temp = Files.createTempDirectory("language-manager-scheme-transfer-test")

    @AfterTest
    fun cleanup() {
        temp.toFile().deleteRecursively()
    }

    @Test
    fun `exports project files as portable paths and imports them safely`() {
        val languageFile =
            temp.resolve("lang/en/auth.php").apply {
                parent.createDirectories()
                writeText("<?php return ['failed' => 'Invalid'];")
            }
        val scheme =
            LanguageSchemeDto(
                id = "scheme",
                name = "Laravel",
                files = listOf(languageFile.toString()),
                updatedAtEpochMs = 1,
                usageScanSettings = UsageScanSettingsDto(basePath = temp.toString()),
            )

        val content = SchemeSettingsTransferSupport.export(listOf(scheme), temp.toString())
        val preview = SchemeSettingsTransferSupport.preview(content, temp.toString())
        val imported = SchemeSettingsTransferSupport.resolve(content, temp.toString()).single()

        assertTrue("lang/en/auth.php" in content)
        assertTrue(preview.canImport)
        assertTrue(
            preview.schemes
                .single()
                .files
                .single()
                .recognized,
        )
        assertEquals(languageFile.toRealPath().toString(), imported.files.single())
        assertEquals(temp.toRealPath().toString(), imported.usageScanSettings.basePath)
    }

    @Test
    fun `preview reports missing files and import refuses them`() {
        val content = """{"formatVersion":1,"schemes":[{"name":"Missing","files":["lang/en/missing.php"]}]}"""

        val preview = SchemeSettingsTransferSupport.preview(content, temp.toString())

        assertFalse(preview.canImport)
        assertFalse(
            preview.schemes
                .single()
                .files
                .single()
                .available,
        )
        assertFailsWith<IllegalArgumentException> { SchemeSettingsTransferSupport.resolve(content, temp.toString()) }
    }

    @Test
    fun `rejects unsupported versions and parent traversal`() {
        val wrongVersion = Json.encodeToString(SchemeSettingsTransferDto(formatVersion = 99))
        val traversal = """{"formatVersion":1,"schemes":[{"name":"Unsafe","files":["../secret.php"]}]}"""

        assertFailsWith<IllegalArgumentException> { SchemeSettingsTransferSupport.preview(wrongVersion, temp.toString()) }
        assertFalse(SchemeSettingsTransferSupport.preview(traversal, temp.toString()).canImport)
        assertFailsWith<IllegalArgumentException> { SchemeSettingsTransferSupport.resolve(traversal, temp.toString()) }
    }
}

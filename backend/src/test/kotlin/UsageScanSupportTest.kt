package cg.creamgod45

import cg.creamgod45.localization.LanguageEntryDto
import cg.creamgod45.localization.UsageScanSettingsDto
import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class UsageScanSupportTest {
    private val temp = Files.createTempDirectory("language-manager-usage-test")

    @AfterTest
    fun cleanup() {
        temp.toFile().deleteRecursively()
    }

    @Test
    fun `custom regex counts matching lines and respects relative exclusions`() {
        val source = temp.resolve("src/app.php").apply {
            parent.createDirectories()
            writeText("""
                tr("auth.failed"); tr("auth.failed");
                tr("auth.failed");
                tr("status.ready");
            """.trimIndent())
        }
        temp.resolve("vendor/package.php").apply {
            parent.createDirectories()
            writeText("tr(\"auth.failed\");")
        }
        temp.resolve("tests/fixtures/sample.ts").apply {
            parent.createDirectories()
            writeText("tr(\"auth.failed\");")
        }
        val languageFile = temp.resolve("lang/messages.php").apply {
            parent.createDirectories()
            writeText("tr(\"auth.failed\");")
        }
        val entries = listOf(
            entry("auth", "failed"),
            entry("status", "ready"),
        )
        val settings = UsageScanSettingsDto(
            basePath = temp.toString(),
            regexPatterns = listOf("""tr\("(?<key>[^"]+)"\)"""),
            excludedDirectories = listOf("vendor", "tests/fixtures"),
        )

        val counts = UsageScanSupport.counts(temp, entries, listOf(languageFile.toString()), settings)

        assertEquals(2, counts[entries[0].id], source.toString())
        assertEquals(1, counts[entries[1].id])
    }

    @Test
    fun `normalizes settings and rejects unsafe values`() {
        val normalized = UsageScanSupport.normalize(
            UsageScanSettingsDto(
                basePath = "  $temp  ",
                regexPatterns = listOf(" (auth\\.[a-z]+) ", "(auth\\.[a-z]+)"),
                excludedDirectories = listOf(" vendor ", "tests\\fixtures", "vendor"),
            ),
        )

        assertEquals(temp.toRealPath().toString(), normalized.basePath)
        assertEquals(listOf("(auth\\.[a-z]+)"), normalized.regexPatterns)
        assertEquals(listOf("vendor", "tests/fixtures"), normalized.excludedDirectories)
        assertFailsWith<IllegalArgumentException> {
            UsageScanSupport.normalize(UsageScanSettingsDto(basePath = "ldap://attacker", regexPatterns = listOf("x")))
        }
        assertFailsWith<IllegalArgumentException> {
            UsageScanSupport.normalize(UsageScanSettingsDto(regexPatterns = listOf("[")))
        }
        assertFailsWith<IllegalArgumentException> {
            UsageScanSupport.normalize(UsageScanSettingsDto(regexPatterns = listOf("x"), excludedDirectories = listOf("../secret")))
        }
    }

    @Test
    fun `regex may use first capture group or whole match`() {
        temp.resolve("src/example.ts").apply {
            parent.createDirectories()
            writeText("lookup: auth.failed\nstatus.ready\n")
        }
        val entries = listOf(entry("auth", "failed"), entry("status", "ready"))
        val settings = UsageScanSettingsDto(
            regexPatterns = listOf("""lookup:\s*(auth\.[a-z]+)""", """status\.ready"""),
            excludedDirectories = emptyList(),
        )

        val counts = UsageScanSupport.counts(temp, entries, emptyList(), settings)

        assertEquals(1, counts[entries[0].id])
        assertEquals(1, counts[entries[1].id])
    }

    private fun entry(namespace: String, key: String) = LanguageEntryDto(
        id = "$namespace.$key",
        schemeId = "scheme",
        filePath = temp.resolve("lang/messages.php").toString(),
        locale = "en",
        namespace = namespace,
        key = key,
        value = key,
    )
}

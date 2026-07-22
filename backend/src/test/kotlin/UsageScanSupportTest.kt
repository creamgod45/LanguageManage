package cg.creamgod45

import cg.creamgod45.localization.LanguageEntryDto
import cg.creamgod45.localization.UsageScanSettingsDto
import java.nio.file.Files
import kotlinx.coroutines.CancellationException
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
    fun `custom regex counts every occurrence and respects relative exclusions`() {
        val source =
            temp.resolve("src/app.php").apply {
                parent.createDirectories()
                writeText(
                    """
                    tr("auth.failed"); tr("auth.failed");
                    tr("auth.failed");
                    tr("status.ready");
                    """.trimIndent(),
                )
            }
        temp.resolve("vendor/package.php").apply {
            parent.createDirectories()
            writeText("tr(\"auth.failed\");")
        }
        temp.resolve("tests/fixtures/sample.ts").apply {
            parent.createDirectories()
            writeText("tr(\"auth.failed\");")
        }
        val languageFile =
            temp.resolve("lang/messages.php").apply {
                parent.createDirectories()
                writeText("tr(\"auth.failed\");")
            }
        val entries =
            listOf(
                entry("auth", "failed"),
                entry("status", "ready"),
            )
        val settings =
            UsageScanSettingsDto(
                basePath = temp.toString(),
                regexPatterns = listOf("""tr\("(?<key>[^"]+)"\)"""),
                excludedDirectories = listOf("vendor", "tests/fixtures"),
            )

        val counts = UsageScanSupport.counts(temp, entries, listOf(languageFile.toString()), settings)

        assertEquals(3, counts[entries[0].id], source.toString())
        assertEquals(1, counts[entries[1].id])
    }

    @Test
    fun `multiple regex formats accumulate without double counting the same captured occurrence`() {
        temp.resolve("src/example.php").apply {
            parent.createDirectories()
            writeText("tr(\"auth.failed\"); __(\"auth.failed\"); tr(\"auth.failed\");")
        }
        val entries = listOf(entry("auth", "failed"))
        val settings =
            UsageScanSettingsDto(
                regexPatterns =
                    listOf(
                        """tr\(\"(?<key>[^\"]+)\"\)""",
                        """__\(\"(?<key>[^\"]+)\"\)""",
                        """\"(?<key>auth\.[^\"]+)\"""",
                    ),
                excludedDirectories = emptyList(),
            )

        val counts = UsageScanSupport.counts(temp, entries, emptyList(), settings)

        assertEquals(3, counts[entries[0].id])
    }

    @Test
    fun `Laravel key-only regex counts calls with uncertain package and group prefixes`() {
        temp.resolve("src/laravel.php").apply {
            parent.createDirectories()
            writeText(
                """
                __('filament::components/button.messages.uploading_file');
                __('components/filament.someLangKey1');
                """.trimIndent(),
            )
        }
        val entries =
            listOf(
                entry("components.button", "messages.uploading_file"),
                entry("components.filament", "someLangKey1"),
            )
        val settings =
            UsageScanSettingsDto(
                regexPatterns =
                    listOf(
                        """(?:__|trans|trans_choice)\(\s*(?<quote>[\"'])(?:(?:[^\"'\r\n:]{1,128})::)?[^\"'\r\n.]{1,128}\.(?<key>[^\"'\r\n]{1,256}?)\k<quote>""",
                    ),
                excludedDirectories = emptyList(),
            )

        val counts = UsageScanSupport.counts(temp, entries, emptyList(), settings)

        assertEquals(1, counts[entries[0].id])
        assertEquals(1, counts[entries[1].id])
    }

    @Test
    fun `normalizes settings and rejects unsafe values`() {
        val normalized =
            UsageScanSupport.normalize(
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
        assertFailsWith<IllegalArgumentException> {
            UsageScanSupport.normalize(UsageScanSettingsDto(maxLanguageFileKb = 0))
        }
        assertFailsWith<IllegalArgumentException> {
            UsageScanSupport.normalize(UsageScanSettingsDto(maxEntriesPerFile = 10, maxEntriesPerScheme = 9))
        }
    }

    @Test
    fun `regex may use first capture group or whole match`() {
        temp.resolve("src/example.ts").apply {
            parent.createDirectories()
            writeText("lookup: auth.failed\nstatus.ready\n")
        }
        val entries = listOf(entry("auth", "failed"), entry("status", "ready"))
        val settings =
            UsageScanSettingsDto(
                regexPatterns = listOf("""lookup:\s*(auth\.[a-z]+)""", """status\.ready"""),
                excludedDirectories = emptyList(),
            )

        val counts = UsageScanSupport.counts(temp, entries, emptyList(), settings)

        assertEquals(1, counts[entries[0].id])
        assertEquals(1, counts[entries[1].id])
    }

    @Test
    fun `scan follows scheme regex without extension size line or multiline filters`() {
        temp.resolve("src/template.svelte").apply {
            parent.createDirectories()
            writeText("tr(\"auth.failed\")")
        }
        temp.resolve("src/extensionless").writeText("tr(\"auth.failed\")")
        temp.resolve("src/large.custom-data").writeText("x".repeat(600_000) + "tr(\"auth.failed\")")
        temp.resolve("src/multiline.unrecognized").writeText("begin\nauth.failed\nend")
        val entry = entry("auth", "failed")
        val settings =
            UsageScanSettingsDto(
                regexPatterns =
                    listOf(
                        """tr\(\"(?<key>[^\"]+)\"\)""",
                        """begin\s+(?<key>auth\.failed)\s+end""",
                    ),
                excludedDirectories = emptyList(),
            )

        val counts = UsageScanSupport.counts(temp, listOf(entry), emptyList(), settings)

        assertEquals(4, counts[entry.id])
    }

    @Test
    fun `usage scan cooperatively stops when its task is cancelled`() {
        repeat(20) { index -> temp.resolve("source-$index.txt").writeText("tr(\"auth.failed\")") }
        var checkpoints = 0

        assertFailsWith<CancellationException> {
            UsageScanSupport.counts(
                temp,
                listOf(entry("auth", "failed")),
                emptyList(),
                UsageScanSettingsDto(regexPatterns = listOf("""tr\(\"(?<key>[^\"]+)\"\)"""), excludedDirectories = emptyList()),
            ) {
                if (++checkpoints > 8) throw CancellationException("test cancellation")
            }
        }
    }

    private fun entry(
        namespace: String,
        key: String,
    ) = LanguageEntryDto(
        id = "$namespace.$key",
        schemeId = "scheme",
        filePath = temp.resolve("lang/messages.php").toString(),
        locale = "en",
        namespace = namespace,
        key = key,
        value = key,
    )
}

package cg.creamgod45

import cg.creamgod45.localization.LanguageEntryDto
import cg.creamgod45.localization.MAX_USAGE_EXCLUSIONS
import cg.creamgod45.localization.UsageScanSettingsDto
import java.nio.file.Files
import kotlinx.coroutines.CancellationException
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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
        assertEquals(
            1_000,
            UsageScanSupport.normalize(
                UsageScanSettingsDto(regexPatterns = listOf("x"), excludedDirectories = List(1_000) { "folder-$it" }),
            ).excludedDirectories.size,
        )
        assertFailsWith<IllegalArgumentException> {
            UsageScanSupport.normalize(
                UsageScanSettingsDto(regexPatterns = listOf("x"), excludedDirectories = List(1_001) { "folder-$it" }),
            )
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

    @Test
    fun `scan records cached source offsets and resolves line and column only on demand`() {
        val content = "tr(\"auth.failed\")\n  tr(\"auth.failed\")\n"
        val source =
            temp.resolve("src/usage.php").apply {
                parent.createDirectories()
                writeText(content)
            }
        val entry = entry("auth", "failed")
        val settings =
            UsageScanSettingsDto(
                regexPatterns = listOf("""tr\(\"(?<key>[^\"]+)\"\)"""),
                excludedDirectories = emptyList(),
            )

        val result = UsageScanSupport.scan(temp, listOf(entry), emptyList(), settings)

        assertEquals(2, result.counts[entry.id])
        assertFalse(result.locationsTruncated)
        assertEquals(listOf(content.indexOf("auth.failed"), content.lastIndexOf("auth.failed")), result.locations.map { it.offset }.sorted())
        assertTrue(result.locations.all { it.line == 0 && it.column == 0 })
        val second = result.locations.maxBy { it.offset }
        assertEquals(2 to 7, UsageLocationSupport.sourceLineColumn(source, second.offset))
        assertEquals(Files.getLastModifiedTime(source).toMillis(), second.sourceModifiedAtEpochMs)
    }

    @Test
    fun `source file planning count follows exclusions and skips managed language files`() {
        val source = temp.resolve("src/app.txt").apply { parent.createDirectories(); writeText("x") }
        val language = temp.resolve("lang/en.json").apply { parent.createDirectories(); writeText("{}") }
        temp.resolve("vendor/ignored.txt").apply { parent.createDirectories(); writeText("x") }
        val settings = UsageScanSettingsDto(regexPatterns = listOf("x"), excludedDirectories = listOf("vendor"))

        assertEquals(1, UsageScanSupport.sourceFileCount(temp, listOf(language.toString()), settings), source.toString())
    }

    // Case C — every selected folder is BELOW the scan root and one is already excluded (explicit
    // relative path). Merging must keep the already-excluded entry once and still add the new one,
    // never aborting the batch.
    @Test
    fun `already-excluded folder below the scan root does not block newly added folders`() {
        val current =
            UsageScanSettingsDto(
                regexPatterns = listOf("x"),
                excludedDirectories = listOf("vendor", "src/generated"),
            )

        val merge = UsageScanSupport.mergeExclusions(current, listOf("src/generated", "src/reports"))

        assertEquals(listOf("src/reports"), merge.added)
        assertEquals(listOf("vendor", "src/generated", "src/reports"), merge.settings.excludedDirectories)
    }

    // Case D — re-selecting only folders that are already excluded adds nothing (the caller then
    // reports "already excluded") and must not throw or duplicate entries.
    @Test
    fun `re-adding only already-excluded folders adds nothing and does not throw`() {
        val current =
            UsageScanSettingsDto(regexPatterns = listOf("x"), excludedDirectories = listOf("src/generated"))

        val merge = UsageScanSupport.mergeExclusions(current, listOf("src/generated", "src/generated"))

        assertTrue(merge.added.isEmpty())
        assertEquals(listOf("src/generated"), merge.settings.excludedDirectories)
    }

    // Case E — a folder already excluded by a default NAME rule ("build" matches any folder named
    // build) is selected as a specific path ("app/build") together with a new sibling. The explicit
    // path is a genuinely new entry, the sibling is added, and nothing is dropped or throws.
    @Test
    fun `folder already excluded by a name rule still records its explicit path without blocking siblings`() {
        val current = UsageScanSettingsDto(regexPatterns = listOf("x"), excludedDirectories = listOf("build"))

        val merge = UsageScanSupport.mergeExclusions(current, listOf("app/build", "app/keep"))

        assertEquals(listOf("app/build", "app/keep"), merge.added)
        assertEquals(listOf("build", "app/build", "app/keep"), merge.settings.excludedDirectories)
    }

    // Case F — the batch would push the list past the 1,000-entry cap. The folders that still fit
    // are added; the overflow is skipped and reported instead of the whole batch being rejected.
    @Test
    fun `additions beyond the exclusion cap are skipped while the ones that fit are added`() {
        val current =
            UsageScanSettingsDto(
                regexPatterns = listOf("x"),
                excludedDirectories = (0 until MAX_USAGE_EXCLUSIONS - 1).map { "existing/$it" },
            )

        val merge = UsageScanSupport.mergeExclusions(current, listOf("new/a", "new/b", "new/c"))

        assertEquals(listOf("new/a"), merge.added)
        assertEquals(listOf("new/b", "new/c"), merge.skipped)
        assertEquals(MAX_USAGE_EXCLUSIONS, merge.settings.excludedDirectories.size)
    }

    // Case G — an over-long relative path fails per-entry validation. It is skipped and reported;
    // the valid sibling in the same batch is still added.
    @Test
    fun `an over-long exclusion path is skipped without blocking a valid sibling`() {
        val tooLong = "a".repeat(201)
        val current = UsageScanSettingsDto(regexPatterns = listOf("x"), excludedDirectories = emptyList())

        val merge = UsageScanSupport.mergeExclusions(current, listOf(tooLong, "keep"))

        assertEquals(listOf("keep"), merge.added)
        assertEquals(listOf(tooLong), merge.skipped)
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

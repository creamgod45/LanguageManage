package cg.creamgod45

import cg.creamgod45.localization.DEFAULT_USAGE_REGEX_PATTERNS
import cg.creamgod45.localization.EditorKeyScanStatus
import cg.creamgod45.localization.EditorKeyTargetDto
import cg.creamgod45.localization.LanguageEntryDto
import cg.creamgod45.localization.UsageScanSettingsDto
import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EditorKeyScanSupportTest {
    private val temp = Files.createTempDirectory("language-manager-editor-key-test").toRealPath()

    @AfterTest
    fun cleanup() {
        temp.toFile().deleteRecursively()
    }

    private fun entry(
        id: String,
        namespace: String,
        key: String,
        locale: String = "en",
    ) = LanguageEntryDto(id, "scheme", "/lang/$locale/$namespace.php", locale, namespace, key, "$key-$locale")

    @Test
    fun `named key group gives the highlighted range and resolves namespace dot key`() {
        val text = """echo __("messages.welcome"); echo __("messages.missing");"""
        val entries = listOf(entry("1", "messages", "welcome"), entry("2", "messages", "welcome", "zh_TW"))

        val result = EditorKeyScanSupport.scan(text, DEFAULT_USAGE_REGEX_PATTERNS.map(::Regex), entries)

        assertEquals(EditorKeyScanStatus.OK, result.status)
        assertFalse(result.truncated)
        assertEquals(listOf("messages.welcome", "messages.missing"), result.matches.map { it.candidate })
        val welcome = result.matches.first()
        assertEquals("messages.welcome", text.substring(welcome.startOffset, welcome.endOffsetExclusive))
        assertEquals(listOf(EditorKeyTargetDto("messages", "welcome")), welcome.targets)
        val missing = result.matches.last()
        assertTrue(missing.targets.isEmpty())
        assertEquals("messages" to "missing", missing.suggestedNamespace to missing.suggestedKey)
    }

    @Test
    fun `first capture group is used when the regex has no key group and duplicates across patterns collapse`() {
        val text = "t('auth.failed') t('auth.failed')"
        val patterns = listOf(Regex("""t\('([^']+)'\)"""), Regex("""t\('(?<key>[^']+)'\)"""))

        val result = EditorKeyScanSupport.scan(text, patterns, listOf(entry("1", "", "auth.failed")))

        assertEquals(listOf(3, 20), result.matches.map { it.startOffset })
        assertTrue(result.matches.all { it.targets == listOf(EditorKeyTargetDto("", "auth.failed")) })
    }

    @Test
    fun `slash namespaces and package prefixes resolve like the usage scan`() {
        val entries = listOf(entry("1", "admin.users", "title"), entry("2", "messages", "hello"))
        val text = """__("admin/users.title") __("package::messages.hello") __("title")"""

        val matches = EditorKeyScanSupport.scan(text, DEFAULT_USAGE_REGEX_PATTERNS.map(::Regex), entries).matches

        assertEquals(EditorKeyTargetDto("admin.users", "title"), matches[0].targets.single())
        assertEquals(EditorKeyTargetDto("messages", "hello"), matches[1].targets.single())
        assertEquals(EditorKeyTargetDto("admin.users", "title"), matches[2].targets.single())
    }

    @Test
    fun `unresolved captures suggest the longest known namespace`() {
        val namespaces = listOf("", "admin", "admin.users")

        assertEquals("admin.users" to "name", EditorKeyScanSupport.suggestTarget("admin.users.name", namespaces))
        assertEquals("admin.users" to "name", EditorKeyScanSupport.suggestTarget("admin/users.name", namespaces))
        assertEquals("admin" to "save", EditorKeyScanSupport.suggestTarget("vendor::admin.save", namespaces))
        assertEquals("" to "Plain text key", EditorKeyScanSupport.suggestTarget("Plain text key", namespaces))
        assertEquals("" to "admin.", EditorKeyScanSupport.suggestTarget("admin.", namespaces))
    }

    @Test
    fun `match count is capped and reported as truncated`() {
        val text = (1..50).joinToString(" ") { """t("k$it")""" }

        val result = EditorKeyScanSupport.scan(text, DEFAULT_USAGE_REGEX_PATTERNS.map(::Regex), emptyList(), maxMatches = 10)

        assertEquals(10, result.matches.size)
        assertTrue(result.truncated)
    }

    @Test
    fun `a backtracking user regex stops at the time budget instead of hanging`() {
        var now = 0L
        val text = "a".repeat(5_000) + "!"
        // Every read advances the fake clock, so the budget runs out on the first deadline check.
        val result =
            EditorKeyScanSupport.scan(
                text,
                listOf(Regex("""(a+)+$""")),
                emptyList(),
                timeBudgetMs = 1,
                nanoTime = { now.also { now += 1_000_000 } },
            )

        assertTrue(result.truncated)
        assertTrue(result.matches.isEmpty())
    }

    @Test
    fun `source file status follows base path exclusions and language files`() {
        val settings = UsageScanSettingsDto(excludedDirectories = listOf("vendor", "resources/legacy", "app/Generated.php"))

        fun file(path: String) =
            temp.resolve(path).apply {
                parent.createDirectories()
                writeText("")
            }
        val languageFile = file("lang/en/messages.php")
        val outside = Files.createTempFile("language-manager-outside", ".php").toRealPath()
        try {
            fun status(path: java.nio.file.Path) = UsageScanSupport.sourceFileStatus(temp, path, listOf(languageFile.toString()), settings)

            assertEquals(EditorKeyScanStatus.OK, status(file("app/Http/Controller.php")))
            assertEquals(EditorKeyScanStatus.EXCLUDED, status(file("vendor/pkg/src/Thing.php")))
            assertEquals(EditorKeyScanStatus.EXCLUDED, status(file("modules/a/vendor/Thing.php")))
            assertEquals(EditorKeyScanStatus.EXCLUDED, status(file("resources/legacy/views/home.blade.php")))
            assertEquals(EditorKeyScanStatus.OK, status(file("resources/views/home.blade.php")))
            assertEquals(EditorKeyScanStatus.EXCLUDED, status(file("app/Generated.php")))
            assertEquals(EditorKeyScanStatus.LANGUAGE_FILE, status(languageFile))
            assertEquals(EditorKeyScanStatus.OUTSIDE_BASE_PATH, status(outside))
        } finally {
            Files.deleteIfExists(outside)
        }
    }
}

package cg.creamgod45

import cg.creamgod45.localization.EntrySearch
import cg.creamgod45.localization.InlineTranslationEdit
import cg.creamgod45.localization.InlineTranslationEditResult
import cg.creamgod45.localization.LanguageEntryDto
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Regression for #34: a base bundle and an explicit `_en` bundle must stay separate through an edit. */
class ResourceBundleFallbackEditTest {
    private lateinit var root: Path
    private lateinit var base: Path
    private lateinit var english: Path
    private lateinit var chinese: Path

    @BeforeTest
    fun setUp() {
        root = Files.createTempDirectory("language-manager-fallback-edit").toAbsolutePath().normalize()
        base = root.resolve("messages.properties").apply { writeText("# fallback\ngreeting=Hello fallback\n") }
        english = root.resolve("messages_en.properties").apply { writeText("# english\ngreeting=Hello\n") }
        chinese = root.resolve("messages_zh_TW.properties").apply { writeText("greeting=哈囉\n") }
    }

    @AfterTest
    fun tearDown() {
        root.toFile().deleteRecursively()
    }

    @Test
    fun `base and explicit English bundles join as separate editable columns`() {
        val row = EntrySearch.join(entries(documents())).single()

        assertEquals(listOf("en", "fallback", "zh_TW"), row.translations.map { it.locale }.sorted())
        assertEquals("Hello fallback", row.values("fallback"))
        assertEquals("Hello", row.values("en"))
        assertTrue(InlineTranslationEdit.isEditable(row, "fallback"))
        assertTrue(InlineTranslationEdit.isEditable(row, "en"))
    }

    @Test
    fun `editing the English column writes only the explicit English file`() {
        val baseBefore = base.readText()
        val chineseBefore = chinese.readText()

        edit("en", "Hi")

        assertEquals(baseBefore, base.readText())
        assertEquals(chineseBefore, chinese.readText())
        assertEquals("Hi", LanguageFileCodec.parse(english, "scheme").values["greeting"])
        assertEachLocaleOnce(mapOf("fallback" to "Hello fallback", "en" to "Hi", "zh_TW" to "哈囉"))
    }

    @Test
    fun `editing the fallback column writes only the base file`() {
        val englishBefore = english.readText()

        edit("fallback", "Default greeting")

        assertEquals(englishBefore, english.readText())
        assertEquals("Default greeting", LanguageFileCodec.parse(base, "scheme").values["greeting"])
        assertEachLocaleOnce(mapOf("fallback" to "Default greeting", "en" to "Hello", "zh_TW" to "哈囉"))
    }

    private fun edit(
        locale: String,
        value: String,
    ) {
        val documents = documents()
        val entries = entries(documents)
        val row = EntrySearch.join(entries).single()
        val mutation = assertIs<InlineTranslationEditResult.Save>(InlineTranslationEdit.resolve(row, locale, value, entries)).mutation

        val changed = EntryMutationSupport.apply(documents, entries, listOf(mutation), String::trim)

        assertEquals(listOf(mutation.filePath), changed.map { it.path.toString() })
        changed.forEach { document ->
            SafeLanguageFileAccess.atomicWrite(document.path, LanguageFileCodec.renderPreservingFormat(document, document.path.readText()))
        }
    }

    private fun assertEachLocaleOnce(expected: Map<String, String>) {
        val row = EntrySearch.join(entries(documents())).single()
        assertEquals(expected.keys.sorted(), row.translations.map { it.locale }.sorted())
        expected.forEach { (locale, value) -> assertEquals(value, row.values(locale)) }
    }

    private fun documents() = listOf(base, english, chinese).map { LanguageFileCodec.parse(it, "scheme") }

    private fun entries(documents: List<ParsedLanguageFile>) =
        documents.flatMap { document ->
            document.values.map { (key, value) ->
                LanguageEntryDto("${document.path}#$key", "scheme", document.path.toString(), document.locale, document.namespace, key, value)
            }
        }
}

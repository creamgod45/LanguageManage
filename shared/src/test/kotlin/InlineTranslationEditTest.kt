package cg.creamgod45.localization

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class InlineTranslationEditTest {
    private fun entry(
        locale: String,
        key: String,
        value: String,
        namespace: String = "messages",
        filePath: String = "$locale/$namespace.json",
        id: String = "$locale.$namespace.$key",
    ) = LanguageEntryDto(id, "scheme", filePath, locale, namespace, key, value)

    private fun row(vararg translations: LanguageEntryDto) =
        JoinedTranslationRow(translations.first().namespace, translations.first().key, translations.toList())

    @Test
    fun `single line and missing values are editable in place`() {
        val row = row(entry("en", "title", "Title"))

        assertTrue(InlineTranslationEdit.isEditable(row, "en"))
        assertTrue(InlineTranslationEdit.isEditable(row, "ja"))
    }

    @Test
    fun `long and multi line values are editable in place`() {
        // Long translations (for example concatenated PHP strings or heredocs) often contain line breaks.
        val multiLine = row(entry("en", "body", "line 1\nline 2"))
        val crlf = row(entry("en", "body", "line 1\r\nline 2"))
        val long = row(entry("en", "body", "word ".repeat(500)))

        assertTrue(InlineTranslationEdit.isEditable(multiLine, "en"))
        assertTrue(InlineTranslationEdit.isEditable(crlf, "en"))
        assertTrue(InlineTranslationEdit.isEditable(long, "en"))
    }

    @Test
    fun `duplicated locale values require Edit Selected`() {
        val duplicated =
            row(
                entry("en", "title", "A", filePath = "a/en.json", id = "a"),
                entry("en", "title", "B", filePath = "b/en.json", id = "b"),
            )

        assertFalse(InlineTranslationEdit.isEditable(duplicated, "en"))
    }

    @Test
    fun `unchanged value or empty value for missing translation is ignored`() {
        val row = row(entry("en", "title", "Title"))

        assertEquals(InlineTranslationEditResult.NoChange, InlineTranslationEdit.resolve(row, "en", "Title", row.translations))
        assertEquals(InlineTranslationEditResult.NoChange, InlineTranslationEdit.resolve(row, "ja", "", row.translations))
    }

    @Test
    fun `existing translation is updated in its own file and keeps its id`() {
        val existing = entry("en", "title", "Title")
        val row = row(existing)

        val result = assertIs<InlineTranslationEditResult.Save>(InlineTranslationEdit.resolve(row, "en", "New title", listOf(existing)))

        assertEquals(EntryMutationDto(existing.id, existing.filePath, "en", "messages", "title", "New title"), result.mutation)
    }

    @Test
    fun `clearing an existing translation saves an empty value instead of deleting`() {
        val existing = entry("en", "title", "Title")

        val result = assertIs<InlineTranslationEditResult.Save>(InlineTranslationEdit.resolve(row(existing), "en", "", listOf(existing)))

        assertEquals("", result.mutation.value)
        assertEquals(existing.id, result.mutation.id)
    }

    @Test
    fun `missing translation prefers a file of the same namespace and locale`() {
        val source = entry("en", "title", "Title")
        val otherNamespace = entry("ja", "x", "X", namespace = "other", filePath = "ja/other.json")
        val sameNamespace = entry("ja", "y", "Y", filePath = "ja/messages.json")

        val result =
            assertIs<InlineTranslationEditResult.Save>(
                InlineTranslationEdit.resolve(row(source), "ja", "タイトル", listOf(source, otherNamespace, sameNamespace)),
            )

        assertEquals(EntryMutationDto(null, "ja/messages.json", "ja", "messages", "title", "タイトル"), result.mutation)
    }

    @Test
    fun `missing translation falls back to any file of the locale`() {
        val source = entry("en", "title", "Title")
        val otherNamespace = entry("ja", "x", "X", namespace = "other", filePath = "ja/other.json")

        val result =
            assertIs<InlineTranslationEditResult.Save>(
                InlineTranslationEdit.resolve(row(source), "ja", "タイトル", listOf(source, otherNamespace)),
            )

        assertEquals("ja/other.json", result.mutation.filePath)
    }

    @Test
    fun `locale without any scheme file is reported`() {
        val source = entry("en", "title", "Title")

        assertEquals(
            InlineTranslationEditResult.MissingLocaleFile("ko"),
            InlineTranslationEdit.resolve(row(source), "ko", "제목", listOf(source)),
        )
    }
}

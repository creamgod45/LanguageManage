package cg.creamgod45.localization

import kotlin.test.Test
import kotlin.test.assertEquals

class EntrySearchTest {
    private fun entry(locale: String, namespace: String, key: String, value: String, usageCount: Int = 0) =
        LanguageEntryDto("$locale.$namespace.$key", "s", "$locale.json", locale, namespace, key, value, usageCount)

    @Test
    fun `filters joined rows by missing translations and zero usage`() {
        val complete = listOf(
            entry("en", "auth", "ready", "Ready", usageCount = 2),
            entry("zh_TW", "auth", "ready", "就緒", usageCount = 2),
        )
        val missingLocale = listOf(entry("en", "auth", "failed", "Failed", usageCount = 1))
        val blankValue = listOf(
            entry("en", "auth", "offline", "Offline"),
            entry("zh_TW", "auth", "offline", ""),
        )
        val rows = EntrySearch.join(complete + missingLocale + blankValue)
        val locales = setOf("en", "zh_TW")

        assertEquals(setOf("failed", "offline"), EntrySearch.filterRows(rows, locales, TranslationRowFilter.MISSING_TRANSLATION).map { it.key }.toSet())
        assertEquals(listOf("offline"), EntrySearch.filterRows(rows, locales, TranslationRowFilter.ZERO_USAGE).map { it.key })
        assertEquals(rows, EntrySearch.filterRows(rows, locales, TranslationRowFilter.ALL))
    }
    private val entries = listOf(
        LanguageEntryDto("1", "s", "en.json", "en", "auth", "failed", "Login failed"),
        LanguageEntryDto("2", "s", "zh.json", "zh", "auth", "failed", "登入失敗"),
        LanguageEntryDto("3", "s", "en.json", "en", "common", "save", "Save"),
    )

    @Test fun `fuzzy search scans key value namespace locale and path`() {
        assertEquals(2, EntrySearch.filter(entries, "fail", SearchMode.FUZZY, null).size)
        assertEquals(1, EntrySearch.filter(entries, "zh.json", SearchMode.FUZZY, null).size)
    }

    @Test fun `exact search and locale filter combine`() {
        assertEquals(listOf("2"), EntrySearch.filter(entries, "auth.failed", SearchMode.EXACT, "zh").map { it.id })
        assertEquals(2, EntrySearch.filter(entries, "", SearchMode.FUZZY, "en").size)
    }

    @Test fun `joins locales into one row per namespace and key`() {
        val joined = EntrySearch.join(entries)
        assertEquals(2, joined.size)
        val auth = joined.single { it.key == "failed" }
        assertEquals("Login failed", auth.values("en"))
        assertEquals("登入失敗", auth.values("zh"))
        assertEquals(2, auth.translations.size)
    }

    @Test fun `pagination never returns more than one hundred joined rows`() {
        val many = List(205) { index ->
            LanguageEntryDto("$index", "s", "en.json", "en", "common", "key_$index", "value_$index")
        }
        val joined = EntrySearch.join(many)
        assertEquals(100, EntrySearch.paginate(joined, 0).rows.size)
        assertEquals(100, EntrySearch.paginate(joined, 1).rows.size)
        val last = EntrySearch.paginate(joined, 2)
        assertEquals(5, last.rows.size)
        assertEquals(3, last.pageCount)
        assertEquals(205, last.totalRows)
    }

    @Test fun `bulk deletion reports joined row count while deleting every locale entry`() {
        val joined = EntrySearch.join(entries)
        val selection = EntrySearch.deletionFor(listOf(joined[0], joined[0], joined[1]))

        assertEquals(2, selection.rowCount)
        assertEquals(setOf("1", "2", "3"), selection.entryIds.toSet())
    }
}

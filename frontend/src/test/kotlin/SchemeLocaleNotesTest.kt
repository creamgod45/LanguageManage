package cg.creamgod45.localization.ui

import cg.creamgod45.localization.MAX_LOCALE_NOTE_CHARS
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SchemeLocaleNotesTest {
    @Test
    fun `rows list loaded locales and locales that only have a note`() {
        val rows = localeNoteRows(listOf("zh_TW", "fallback", "en"), mapOf("fallback" to "English (default)", "ja" to "Polite"))

        assertEquals(
            listOf("en" to "", "fallback" to "English (default)", "ja" to "Polite", "zh_TW" to ""),
            rows,
        )
    }

    @Test
    fun `edited notes are trimmed and blank notes are removed`() {
        val notes = editedLocaleNotes(listOf("en" to "  US English ", "fallback" to "   ", "ja" to ""))

        assertEquals(mapOf("en" to "US English"), notes)
    }

    @Test
    fun `notes reject oversized text and control characters`() {
        assertTrue(isValidLocaleNote("a".repeat(MAX_LOCALE_NOTE_CHARS)))
        assertTrue(isValidLocaleNote("  padded  "))
        assertFalse(isValidLocaleNote("a".repeat(MAX_LOCALE_NOTE_CHARS + 1)))
        assertFalse(isValidLocaleNote("line\nbreak"))
    }
}

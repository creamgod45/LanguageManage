package cg.creamgod45.localization.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class TranslationPageRetentionTest {
    @Test
    fun `reloads and mutations retain the current page for the same scheme`() {
        assertEquals(7, retainedTranslationPage(7, "scheme-a", "scheme-a"))
    }

    @Test
    fun `switching schemes resets the translation page`() {
        assertEquals(0, retainedTranslationPage(7, "scheme-a", "scheme-b"))
    }
}

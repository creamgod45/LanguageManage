package cg.creamgod45

import cg.creamgod45.localization.LanguageEntryDto
import kotlin.test.Test
import kotlin.test.assertTrue

class LocalizationAnalysisTest {
    private fun entry(id: String, locale: String, key: String, value: String, usage: Int = 1) =
        LanguageEntryDto(id, "s", "$locale.json", locale, "messages", key, value, usage)

    @Test
    fun `detects empty duplicate missing translations and unused entries`() {
        val issues = LocalizationAnalysis.analyze("s", listOf(
            entry("1", "en", "hello", "Hello", 0),
            entry("2", "zh", "hello", "Hello"),
            entry("3", "en", "only_en", ""),
            entry("4", "en", "hello", "Again"),
        ))
        val codes = issues.map { it.code }.toSet()
        assertTrue(setOf("MISSING_VALUE", "DUPLICATE_KEY", "DUPLICATE_VALUE", "MISSING_TRANSLATION", "UNUSED_KEY").all { it in codes })
    }
}

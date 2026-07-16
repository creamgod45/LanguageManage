package cg.creamgod45

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RegexPresetUiTest {
    @Test
    fun `recommended framework patterns are valid and retain key capture`() {
        assertTrue(
            RegexPresetUi.presets
                .map {
                    it.name
                }.containsAll(
                    listOf(
                        "Laravel",
                        "Symfony",
                        "webman",
                        "Laminas / Zend",
                        "CodeIgniter",
                        "CakePHP",
                        "Yii",
                        "Phalcon",
                        "FuelPHP",
                        "Slim / Pixie / custom",
                        "Spring MessageSource",
                        "ResourceBundle",
                        "IntelliJ Platform Plugin",
                    ),
                ),
        )
        val patterns = RegexPresetUi.presets.flatMap { it.patterns }
        assertEquals(patterns.size, patterns.distinct().size)
        patterns.forEach { pattern ->
            assertTrue(pattern.length <= 512)
            assertTrue("(?<key>" in pattern)
            Regex(pattern)
        }
    }
}

package cg.creamgod45.localization.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import javax.swing.SwingUtilities
import javax.swing.UIManager

class LocaleCodeSuggestionsTest {
    @Test
    fun `catalog contains ISO languages and common BCP 47 variants`() {
        val codes = LocaleCodeCatalog.suggestions.map { it.code }.toSet()

        assertTrue("es" in codes)
        assertTrue("th" in codes)
        assertTrue("es-419" in codes)
        assertTrue("zh-Hant" in codes)
    }

    @Test
    fun `editable value keeps custom locale codes and extracts selected suggestions`() {
        assertEquals("custom_lang", LocaleCodeCatalog.extractCode("custom_lang"))
        assertEquals("es-MX", LocaleCodeCatalog.extractCode("es-MX — español (México)"))
    }

    @Test
    fun `matching accepts code or localized language name`() {
        assertTrue(LocaleCodeCatalog.matching("th").any { it.code == "th" })
        val spanish = LocaleCodeCatalog.suggestions.first { it.code == "es" }
        assertTrue(LocaleCodeCatalog.matching(spanish.displayName).any { it.code == "es" })
    }

    @Test
    fun `locale field preserves typed text until user explicitly chooses a suggestion`() {
        SwingUtilities.invokeAndWait {
            val previousLookAndFeel = UIManager.getLookAndFeel()?.javaClass?.name
            try {
                UIManager.setLookAndFeel("com.intellij.ide.ui.laf.darcula.DarculaLaf")
                val field = LocaleCodeField()
                field.text = "es-M"
                field.textField.document.remove(3, 1)

                assertEquals("es-", field.text)
                assertEquals("es-", field.localeCode)
                assertTrue(field.preferredSize.width > 0)
                assertTrue(field.minimumSize.height > 0)
            } finally {
                previousLookAndFeel?.let(UIManager::setLookAndFeel)
            }
        }
    }
}

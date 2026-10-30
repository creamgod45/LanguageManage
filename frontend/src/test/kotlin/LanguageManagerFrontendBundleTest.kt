package cg.creamgod45

import cg.creamgod45.settings.DisplayLanguage
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class LanguageManagerFrontendBundleTest {
    private fun load(name: String) =
        Properties().apply {
            LanguageManagerFrontendBundleTest::class.java.classLoader
                .getResourceAsStream("messages/$name")!!
                .use(::load)
        }

    @Test
    fun `all localized frontend bundles have identical keys`() {
        val english = load("LanguageManagerFrontendBundle.properties")
        listOf("zh_TW", "zh_CN", "ja", "ko", "es", "th", "de").forEach { locale ->
            val localized = load("LanguageManagerFrontendBundle_$locale.properties")
            assertEquals(english.keys, localized.keys, locale)
            assertEquals(emptyList(), localized.filterValues { it.toString().isBlank() }.keys.toList(), "$locale blank values")
            assertEquals("Rust", localized.getProperty("settings.regex.preset.rust.group"), locale)
            assertEquals("Rust i18n", localized.getProperty("settings.regex.preset.rust.i18n"), locale)
            assertNotEquals(english.getProperty("tab.issues"), localized.getProperty("tab.issues"), locale)
            assertNotEquals(english.getProperty("action.dropdown"), localized.getProperty("action.dropdown"), locale)
            // Experimental editor key hover (issue #38).
            listOf(
                "editor.hover.action.create",
                "editor.hover.action.edit",
                "editor.hover.action.open.table",
                "settings.experimental.editor.hover",
            ).forEach { key -> assertNotEquals(english.getProperty(key), localized.getProperty(key), "$locale $key") }
            assertEquals(english.getProperty("editor.hover.row.identity"), localized.getProperty("editor.hover.row.identity"), locale)
        }
    }

    @Test
    fun `usage regex placeholder is a directly usable double quote example`() {
        val english = load("LanguageManagerFrontendBundle.properties")

        assertEquals(
            """(?:backendMessage|message)\(\s*"(?<key>[^"\r\n]{1,256})"\s*\)""",
            english.getProperty("settings.regex.placeholder"),
        )
    }

    @Test
    fun `explicit display language overrides automatic IDE locale`() {
        val english = LanguageManagerBundle.messageForLanguage(DisplayLanguage.ENGLISH, "tab.issues")
        val traditionalChinese = LanguageManagerBundle.messageForLanguage(DisplayLanguage.TRADITIONAL_CHINESE, "tab.issues")
        val japanese = LanguageManagerBundle.messageForLanguage(DisplayLanguage.JAPANESE, "tab.issues")
        val spanish = LanguageManagerBundle.messageForLanguage(DisplayLanguage.SPANISH, "tab.issues")
        val thai = LanguageManagerBundle.messageForLanguage(DisplayLanguage.THAI, "tab.issues")
        val german = LanguageManagerBundle.messageForLanguage(DisplayLanguage.GERMAN, "tab.issues")

        assertEquals("Issues and Suggestions", english)
        assertNotEquals(english, traditionalChinese)
        assertNotEquals(english, japanese)
        assertNotEquals(english, spanish)
        assertNotEquals(english, thai)
        assertNotEquals(traditionalChinese, japanese)
        assertNotEquals(spanish, thai)
        assertNotEquals(english, german)
        assertEquals("Deutsch", LanguageManagerBundle.messageForLanguage(DisplayLanguage.GERMAN, "settings.language.german"))
    }
}

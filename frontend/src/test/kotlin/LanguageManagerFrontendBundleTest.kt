package cg.creamgod45

import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class LanguageManagerFrontendBundleTest {
    private fun load(name: String) = Properties().apply {
        LanguageManagerFrontendBundleTest::class.java.classLoader.getResourceAsStream("messages/$name")!!.use(::load)
    }

    @Test
    fun `all localized frontend bundles have identical keys`() {
        val english = load("LanguageManagerFrontendBundle.properties")
        listOf("zh_TW", "zh_CN", "ja", "ko").forEach { locale ->
            val localized = load("LanguageManagerFrontendBundle_${locale}.properties")
            assertEquals(english.keys, localized.keys, locale)
            assertNotEquals(english.getProperty("tab.issues"), localized.getProperty("tab.issues"), locale)
            assertNotEquals(english.getProperty("action.dropdown"), localized.getProperty("action.dropdown"), locale)
        }
    }
}

package cg.creamgod45

import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class LanguageManagerBackendBundleTest {
    private fun load(name: String) = Properties().apply {
        LanguageManagerBackendBundleTest::class.java.classLoader.getResourceAsStream("messages/$name")!!.use(::load)
    }

    @Test
    fun `all localized backend bundles have identical keys`() {
        val english = load("LanguageManagerBackendBundle.properties")
        listOf("zh_TW", "zh_CN", "ja", "ko").forEach { locale ->
            val localized = load("LanguageManagerBackendBundle_${locale}.properties")
            assertEquals(english.keys, localized.keys, locale)
            assertNotEquals(english.getProperty("preview.changed"), localized.getProperty("preview.changed"), locale)
        }
    }
}

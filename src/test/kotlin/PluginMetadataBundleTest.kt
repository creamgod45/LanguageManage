package cg.creamgod45

import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class PluginMetadataBundleTest {
    private fun load(name: String) =
        Properties().apply {
            PluginMetadataBundleTest::class.java.classLoader
                .getResourceAsStream("messages/$name")!!
                .use(::load)
        }

    @Test
    fun `plugin metadata bundles have identical non-blank keys in every UI language`() {
        val base = load("LanguageManagerBundle.properties")
        listOf("zh_TW", "zh_CN", "ja", "ko", "es", "th", "de").forEach { locale ->
            val localized = load("LanguageManagerBundle_$locale.properties")
            assertEquals(base.keys, localized.keys, locale)
            assertTrue(localized.values.none { it.toString().isBlank() }, "$locale blank values")
            assertNotEquals(base.getProperty("plugin.description"), localized.getProperty("plugin.description"), locale)
        }
    }
}

package cg.creamgod45.localization.ui

import cg.creamgod45.localization.LanguageEntryDto
import cg.creamgod45.localization.LanguageSchemeDto
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class TranslationEditorSupportTest {
    @Test
    fun `derives one Laravel target for every locale folder`() {
        val root = Path.of("workspace", "lang").toAbsolutePath()
        val files = listOf("en", "zh_CN", "zh_TW").map { root.resolve(it).resolve("auth.php").toString() }
        val scheme = LanguageSchemeDto("scheme", "Laravel", files, 1)
        val entries =
            listOf(
                LanguageEntryDto("en", "scheme", files[0], "en", "auth", "failed", "Invalid"),
                LanguageEntryDto("zh", "scheme", files[1], "zh_CN", "auth", "failed", "无效"),
            )

        val targets = TranslationEditorSupport.targets(scheme, entries)

        assertEquals(listOf("en", "zh_CN", "zh_TW"), targets.map { it.locale })
        assertEquals(setOf("auth"), targets.map { it.namespace }.toSet())
    }

    @Test
    fun `infers resource bundle locale and namespace for empty files`() {
        val root = Path.of("workspace", "messages").toAbsolutePath()
        val files =
            listOf(
                root.resolve("LanguageManagerBundle.properties").toString(),
                root.resolve("LanguageManagerBundle_ja.properties").toString(),
            )
        val scheme = LanguageSchemeDto("scheme", "Bundle", files, 1)

        val targets = TranslationEditorSupport.targets(scheme, emptyList())

        assertEquals(listOf("en", "ja"), targets.map { it.locale })
        assertEquals(setOf("LanguageManagerBundle"), targets.map { it.namespace }.toSet())
    }
}

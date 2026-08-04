package cg.creamgod45

import cg.creamgod45.localization.ReplacementTemplateRuleDto
import cg.creamgod45.localization.UsageScanSettingsDto
import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SelectionReplacementSupportTest {
    @Test
    fun `overlapping suffix conditions use insertion order`() {
        val root = Files.createTempDirectory("selection-replacement-order")
        val file = root.resolve("view.blade.php").apply { writeText("Welcome") }
        val phpFirst = listOf(
            ReplacementTemplateRuleDto("__('%key%')", ".php"),
            ReplacementTemplateRuleDto("@lang('%key%')", ".blade.php"),
        )
        val bladeFirst = phpFirst.reversed()

        assertEquals("__('home.welcome')", SelectionReplacementSupport.previewFile(root, emptyList(), "Welcome", "home.welcome", phpFirst, file.toString()).afterContent)
        assertEquals("@lang('home.welcome')", SelectionReplacementSupport.previewFile(root, emptyList(), "Welcome", "home.welcome", bladeFirst, file.toString()).afterContent)
    }

    @Test
    fun `scan excludes language files directories binary and oversized content`() {
        val root = Files.createTempDirectory("selection-replacement-scan")
        val language = root.resolve("lang/en.php").apply { parent.createDirectories(); writeText("Welcome") }
        root.resolve("src/page.php").apply { parent.createDirectories(); writeText("Welcome Welcome") }
        root.resolve("vendor/package.php").apply { parent.createDirectories(); writeText("Welcome") }
        root.resolve("src/binary.php").writeBytes(byteArrayOf(0, 1, 2, 3))
        root.resolve("src/large.php").writeBytes(ByteArray(5 * 1024 * 1024 + 1) { 'W'.code.toByte() })

        val result = SelectionReplacementSupport.scan(
            root,
            listOf(language.toString()),
            UsageScanSettingsDto(excludedDirectories = listOf("vendor")),
            "Welcome",
            listOf(ReplacementTemplateRuleDto("__('%key%')", ".php")),
        )

        assertEquals(1, result.files.size)
        assertTrue(result.files.single().filePath.endsWith("src${java.io.File.separator}page.php"))
        assertEquals(2, result.files.single().occurrenceCount)
        assertFalse(result.truncated)
    }

    @Test
    fun `template input is data and must contain exactly one placeholder`() {
        assertTrue(runCatching { SelectionReplacementSupport.validateRules(listOf(ReplacementTemplateRuleDto("run('%key%')", ".kt"))) }.isSuccess)
        assertTrue(runCatching { SelectionReplacementSupport.validateRules(listOf(ReplacementTemplateRuleDto("run()", ".kt"))) }.isFailure)
        assertTrue(runCatching { SelectionReplacementSupport.validateRules(listOf(ReplacementTemplateRuleDto("%key% + %key%", ".kt"))) }.isFailure)
        assertTrue(runCatching { SelectionReplacementSupport.validateRules(listOf(ReplacementTemplateRuleDto("run('%key%')", "../../kt"))) }.isFailure)
    }
}

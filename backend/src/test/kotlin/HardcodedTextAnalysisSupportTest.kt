package cg.creamgod45

import cg.creamgod45.localization.HardcodedAnalysisStage
import cg.creamgod45.localization.UsageScanSettingsDto
import java.io.RandomAccessFile
import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HardcodedTextAnalysisSupportTest {
    @Test
    fun `finds every hardcoded location but ignores configured translation calls`() {
        val root = createTempDirectory("hardcoded-analysis")
        val source = root.resolve("src/App.php")
        source.parent.createDirectories()
        source.writeText(
            """
            <?php
            ${'$'}title = "Hello world";
            ${'$'}known = translate("messages.saved");
            ${'$'}button = 'Submit';
            ${'$'}again = "Hello world";
            """.trimIndent(),
        )
        val progress = mutableListOf<HardcodedAnalysisStage>()

        val result =
            HardcodedTextAnalysisSupport().analyze(
                "scheme",
                root,
                emptyList(),
                emptyList(),
                UsageScanSettingsDto(basePath = root.toString(), regexPatterns = listOf("translate\\([^)]*\\)"), excludedDirectories = emptyList()),
                force = true,
                progress = { progress += it.stage },
            )

        assertEquals(listOf("Hello world", "Submit", "Hello world"), result.items.map { it.text })
        assertEquals(listOf(2, 4, 5), result.items.map { it.line })
        assertEquals(3, result.statistics.candidateLocations)
        assertEquals(2, result.statistics.uniqueTexts)
        assertEquals(1, result.statistics.matchedFiles)
        assertTrue(HardcodedAnalysisStage.DISCOVERING in progress)
        assertTrue(HardcodedAnalysisStage.SCANNING in progress)
    }

    @Test
    fun `honors file and folder exclusions without filtering by extension`() {
        val root = createTempDirectory("hardcoded-exclusions")
        root.resolve("src").createDirectories()
        root.resolve("vendor").createDirectories()
        root.resolve("src/keep.custom").writeText("value = \"Visible label\"")
        root.resolve("src/skip.php").writeText("value = \"Hidden file label\"")
        root.resolve("vendor/lib.php").writeText("value = \"Hidden folder label\"")

        val result =
            HardcodedTextAnalysisSupport().analyze(
                "scheme",
                root,
                emptyList(),
                emptyList(),
                UsageScanSettingsDto(
                    basePath = root.toString(),
                    regexPatterns = listOf("translate\\([^)]*\\)"),
                    excludedDirectories = listOf("vendor", "src/skip.php"),
                ),
                force = true,
            )

        assertEquals(listOf("Visible label"), result.items.map { it.text })
        assertTrue(result.items.single().filePath.endsWith("keep.custom"))
    }

    @Test
    fun `reuses unchanged file results during a forced refresh`() {
        val root = createTempDirectory("hardcoded-cache")
        val source = root.resolve("code.txt")
        source.writeText("value = \"Cached label\"")
        val support = HardcodedTextAnalysisSupport()
        val settings = UsageScanSettingsDto(basePath = root.toString(), regexPatterns = listOf("translate\\([^)]*\\)"), excludedDirectories = emptyList())

        support.analyze("scheme", root, emptyList(), emptyList(), settings, force = true)
        val refreshed = support.analyze("scheme", root, emptyList(), emptyList(), settings, force = true)

        assertEquals(1, refreshed.statistics.cachedFiles)
        assertEquals("Cached label", refreshed.items.single().text)
        Files.deleteIfExists(source)
    }

    @Test
    fun `skips oversized source independently without reading it into memory`() {
        val root = createTempDirectory("hardcoded-oversized")
        val oversized = root.resolve("large.source")
        RandomAccessFile(oversized.toFile(), "rw").use { it.setLength(5L * 1024 * 1024 + 1) }
        root.resolve("normal.source").writeText("value = \"Normal label\"")

        val result =
            HardcodedTextAnalysisSupport().analyze(
                "scheme",
                root,
                emptyList(),
                emptyList(),
                UsageScanSettingsDto(basePath = root.toString(), regexPatterns = listOf("translate\\([^)]*\\)"), excludedDirectories = emptyList()),
                force = true,
            )

        assertEquals(1, result.statistics.skippedFiles)
        assertEquals(listOf("Normal label"), result.items.map { it.text })
    }
}

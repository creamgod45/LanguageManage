package cg.creamgod45.localization

import java.nio.file.Files
import kotlin.io.path.writeBytes
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFailsWith

class LanguageLoadBudgetTest {
    private val temp = Files.createTempDirectory("language-manager-load-budget-test")

    @AfterTest
    fun cleanup() {
        temp.toFile().deleteRecursively()
    }

    @Test
    fun `rejects an oversized language file before its content is read`() {
        val oversized = temp.resolve("large.json").apply { writeBytes(ByteArray(2_048)) }
        val budget = LanguageLoadBudget(UsageScanSettingsDto(maxLanguageFileKb = 1))

        assertFailsWith<IllegalArgumentException> { budget.acceptFile(oversized) }
    }

    @Test
    fun `rejects cumulative scheme bytes across unclassified large files`() {
        val first = temp.resolve("first.json").apply { writeBytes(ByteArray(700 * 1024)) }
        val second = temp.resolve("second.json").apply { writeBytes(ByteArray(700 * 1024)) }
        val budget =
            LanguageLoadBudget(
                UsageScanSettingsDto(maxLanguageFileKb = 1_024, maxLanguageSchemeMb = 1),
            )

        budget.acceptFile(first)
        assertFailsWith<IllegalArgumentException> { budget.acceptFile(second) }
    }

    @Test
    fun `rejects per-file and cumulative entry counts`() {
        val file = temp.resolve("messages.json").apply { writeBytes(byteArrayOf()) }

        assertFailsWith<IllegalArgumentException> {
            LanguageLoadBudget(UsageScanSettingsDto(maxEntriesPerFile = 2)).acceptEntries(file, 3)
        }

        val cumulative = UsageScanSettingsDto(maxEntriesPerFile = 3, maxEntriesPerScheme = 4)
        LanguageLoadBudget(cumulative).apply {
            acceptEntries(file, 3)
            assertFailsWith<IllegalArgumentException> { acceptEntries(file, 2) }
        }
    }
}

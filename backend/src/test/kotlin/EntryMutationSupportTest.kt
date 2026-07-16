package cg.creamgod45

import cg.creamgod45.localization.EntryMutationDto
import cg.creamgod45.localization.LanguageEntryDto
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class EntryMutationSupportTest {
    @Test
    fun `applies existing and missing locale values in one batch`() {
        val root = Files.createTempDirectory("language-manager-multi-edit")
        try {
            val enPath = root.resolve("en.json").toAbsolutePath().normalize()
            val zhPath = root.resolve("zh_TW.json").toAbsolutePath().normalize()
            val en = ParsedLanguageFile(enPath, "en", "", linkedMapOf("welcome" to "Welcome"))
            val zh = ParsedLanguageFile(zhPath, "zh_TW", "", linkedMapOf())
            val existing =
                LanguageEntryDto("en-id", "scheme", enPath.toString(), "en", "", "welcome", "Welcome")

            val changed =
                EntryMutationSupport.apply(
                    listOf(en, zh),
                    listOf(existing),
                    listOf(
                        EntryMutationDto("en-id", enPath.toString(), "en", "", "welcome", "Hello"),
                        EntryMutationDto(null, zhPath.toString(), "zh_TW", "", "welcome", "歡迎"),
                    ),
                    String::trim,
                )

            assertEquals(2, changed.size)
            assertEquals("Hello", en.values["welcome"])
            assertEquals("歡迎", zh.values["welcome"])
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun `rejects duplicate targets before applying the batch`() {
        val path = Files.createTempFile("language-manager-duplicate-target", ".json").toAbsolutePath().normalize()
        try {
            val document = ParsedLanguageFile(path, "en", "", linkedMapOf())
            val mutation = EntryMutationDto(null, path.toString(), "en", "", "welcome", "Welcome")

            assertFailsWith<IllegalArgumentException> {
                EntryMutationSupport.apply(listOf(document), emptyList(), listOf(mutation, mutation), String::trim)
            }
            assertEquals(emptyMap(), document.values)
        } finally {
            Files.deleteIfExists(path)
        }
    }
}

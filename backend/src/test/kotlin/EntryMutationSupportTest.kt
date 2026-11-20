package cg.creamgod45

import cg.creamgod45.localization.EntryMutationDto
import cg.creamgod45.localization.LanguageEntryDto
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

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
    fun `accepts a scheme file configured through a symlinked directory`() {
        val root = Files.createTempDirectory("language-manager-symlink").toRealPath()
        try {
            val realDir = Files.createDirectories(root.resolve("volume/lang/en"))
            val realFile = realDir.resolve("budget.php").apply { writeText("<?php\nreturn [];\n") }
            val link = root.resolve("home-link")
            // Some environments (e.g. Windows without Developer Mode) cannot create symlinks; nothing to verify there.
            if (!tryCreateSymbolicLink(link, root.resolve("volume"))) return
            val configuredPath = link.resolve("lang/en/budget.php").toString()
            val document = LanguageFileCodec.parse(SafeLanguageFileAccess.validate(configuredPath), "scheme")
            assertEquals(realFile, document.path)

            val changed =
                EntryMutationSupport.apply(
                    listOf(document),
                    emptyList(),
                    listOf(EntryMutationDto(null, configuredPath, "en", "budget", "test", "test")),
                    String::trim,
                )

            assertEquals(listOf(document), changed)
            assertEquals("test", document.values["test"])
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun `rejects duplicate targets that reach the same file through a symlink`() {
        val root = Files.createTempDirectory("language-manager-symlink-duplicate").toRealPath()
        try {
            val realFile = Files.createDirectories(root.resolve("volume")).resolve("en.json").apply { writeText("{}") }
            val link = root.resolve("home-link")
            if (!tryCreateSymbolicLink(link, root.resolve("volume"))) return
            val document = ParsedLanguageFile(realFile, "en", "", linkedMapOf())

            assertFailsWith<IllegalArgumentException> {
                EntryMutationSupport.apply(
                    listOf(document),
                    emptyList(),
                    listOf(
                        EntryMutationDto(null, realFile.toString(), "en", "", "welcome", "Welcome"),
                        EntryMutationDto(null, link.resolve("en.json").toString(), "en", "", "welcome", "Hello"),
                    ),
                    String::trim,
                )
            }
            assertEquals(emptyMap(), document.values)
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    private fun tryCreateSymbolicLink(
        link: Path,
        target: Path,
    ): Boolean =
        try {
            Files.createSymbolicLink(link, target)
            true
        } catch (_: UnsupportedOperationException) {
            false
        } catch (_: IOException) {
            false
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

    @Test
    fun `adds a missing locale row with the array shape learned from another locale`() {
        val root = Files.createTempDirectory("language-manager-array-missing")
        try {
            val enPath = root.resolve("en.json").apply { writeText("""{"records":[{"label":"First"},{"label":"Second"}]}""") }
            val zhPath = root.resolve("zh_TW.json").apply { writeText("""{"records":[{"label":"第一個"}]}""") }
            val en = LanguageFileCodec.parse(enPath, "scheme")
            val zh = LanguageFileCodec.parse(zhPath, "scheme")

            EntryMutationSupport.apply(
                listOf(en, zh),
                emptyList(),
                listOf(EntryMutationDto(null, zhPath.toString(), "zh_TW", "", "records.1.label", "第二個")),
                String::trim,
            )
            LanguageFileCodec.write(zh)
            val reread = LanguageFileCodec.parse(zhPath, "scheme")

            assertTrue(reread.issues.isEmpty())
            assertEquals("第二個", reread.values["records.1.label"])
            assertTrue(listOf("records") in reread.jsonArrayPaths)
        } finally {
            root.toFile().deleteRecursively()
        }
    }
}

package cg.creamgod45

import cg.creamgod45.localization.UsageLocationDto
import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class UsageSourceRenameSupportTest {
    private val temp = Files.createTempDirectory("language-manager-usage-rename-test")

    @AfterTest
    fun cleanup() {
        temp.toFile().deleteRecursively()
    }

    @Test
    fun `renames full and key-only captures once across locale duplicates`() {
        val source =
            temp.resolve("src/example.php").apply {
                parent.createDirectories()
                writeText("""__('auth.failed'); __('failed');""")
            }
        val content = Files.readString(source)
        val fullOffset = content.indexOf("auth.failed")
        val nestedKeyOffset = fullOffset + "auth.".length
        val keyOnlyOffset = content.lastIndexOf("failed")
        val modifiedAt = Files.getLastModifiedTime(source).toMillis()
        val entryIds = (0 until 12).map { "locale-$it" }
        val locations =
            entryIds.flatMap { id ->
                listOf(
                    location(id, source, fullOffset, modifiedAt),
                    location(id, source, nestedKeyOffset, modifiedAt),
                    location(id, source, keyOnlyOffset, modifiedAt),
                )
            }

        val preview =
            UsageSourceRenameSupport.buildPreview(
                root = temp,
                locations = locations,
                allowedEntryIds = entryIds.toSet(),
                namespace = "auth",
                oldKey = "failed",
                newKey = "invalid_credentials",
            )

        assertEquals(1, preview.size)
        assertEquals(
            """__('auth.invalid_credentials'); __('invalid_credentials');""",
            preview.single().afterContent,
        )
        assertTrue(preview.single().editable)
        assertEquals(content, Files.readString(source), "Preview must not write the source file")
    }

    @Test
    fun `rejects a source file changed after usage scanning`() {
        val source = temp.resolve("source.kt").apply { writeText("""tr("old")""") }
        val offset = Files.readString(source).indexOf("old")
        val staleModifiedAt = Files.getLastModifiedTime(source).toMillis() - 1

        assertFailsWith<IllegalArgumentException> {
            UsageSourceRenameSupport.buildPreview(
                temp,
                listOf(location("entry", source, offset, staleModifiedAt)),
                setOf("entry"),
                "",
                "old",
                "new",
            )
        }
    }

    @Test
    fun `rejects cached paths outside the scheme root`() {
        val outside = Files.createTempFile("language-manager-outside", ".kt")
        try {
            outside.writeText("""tr("old")""")
            val modifiedAt = Files.getLastModifiedTime(outside).toMillis()

            assertFailsWith<IllegalArgumentException> {
                UsageSourceRenameSupport.buildPreview(
                    temp,
                    listOf(location("entry", outside, 4, modifiedAt)),
                    setOf("entry"),
                    "",
                    "old",
                    "new",
                )
            }
        } finally {
            Files.deleteIfExists(outside)
        }
    }

    private fun location(
        entryId: String,
        path: java.nio.file.Path,
        offset: Int,
        modifiedAt: Long,
    ) = UsageLocationDto(
        entryId = entryId,
        filePath = path.toString(),
        offset = offset,
        sourceModifiedAtEpochMs = modifiedAt,
        occurrenceCount = 1,
    )
}

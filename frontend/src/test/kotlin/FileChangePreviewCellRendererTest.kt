package cg.creamgod45.localization.ui

import cg.creamgod45.LanguageManagerBundle
import cg.creamgod45.localization.FileChangePreviewDto
import cg.creamgod45.settings.DisplayLanguage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FileChangePreviewCellRendererTest {
    private fun file(
        path: String,
        editable: Boolean,
    ) = FileChangePreviewDto(
        filePath = path,
        beforeContent = "",
        afterContent = "",
        beforeSha256 = "",
        editable = editable,
    )

    @Test
    fun `dropdown surfaces editability per file when the rename sync flow enables it`() {
        val tag = { editable: Boolean -> if (editable) "EDIT" else "READONLY" }

        val (sourcePath, sourceTag) = fileChangePreviewSegments(true, file("app/Foo.php", editable = true), tag)
        val (langPath, langTag) = fileChangePreviewSegments(true, file("lang/en/foo.php", editable = false), tag)

        assertEquals("app/Foo.php", sourcePath)
        assertEquals("EDIT", sourceTag)
        assertEquals("lang/en/foo.php", langPath)
        assertEquals("READONLY", langTag)
    }

    @Test
    fun `read-only preview flows keep the plain path without an editability tag`() {
        val (path, tag) = fileChangePreviewSegments(false, file("lang/en/foo.php", editable = false)) { "unused" }

        assertEquals("lang/en/foo.php", path)
        assertNull(tag)
    }

    @Test
    fun `oversized markdown diffs drop rich highlighting to avoid the folding CPU spike`() {
        val big = LARGE_MARKDOWN_DIFF_CHARS + 1

        assertTrue(shouldDowngradeDiffHighlighting("Markdown", "md", beforeChars = big, afterChars = 0))
        assertTrue(shouldDowngradeDiffHighlighting("Markdown", "md", beforeChars = 0, afterChars = big))
        // Extension alone is enough when the Markdown plugin is absent and the type resolves to plain text.
        assertTrue(shouldDowngradeDiffHighlighting("PLAIN_TEXT", "md", beforeChars = big, afterChars = big))
    }

    @Test
    fun `small markdown and other file types keep normal highlighting`() {
        assertFalse(shouldDowngradeDiffHighlighting("Markdown", "md", beforeChars = 10, afterChars = 10))
        assertFalse(
            shouldDowngradeDiffHighlighting("PHP", "php", beforeChars = LARGE_MARKDOWN_DIFF_CHARS + 100, afterChars = 0),
        )
        assertFalse(shouldDowngradeDiffHighlighting("JSON", "json", beforeChars = 1_000_000, afterChars = 1_000_000))
    }

    @Test
    fun `editable and read-only tags resolve to distinct localized strings`() {
        val editable = LanguageManagerBundle.messageForLanguage(DisplayLanguage.ENGLISH, "diff.file.tag.editable")
        val readonly = LanguageManagerBundle.messageForLanguage(DisplayLanguage.ENGLISH, "diff.file.tag.readonly")

        assertEquals("editable", editable)
        assertEquals("read-only", readonly)
        assertNotEquals(editable, readonly)
    }
}

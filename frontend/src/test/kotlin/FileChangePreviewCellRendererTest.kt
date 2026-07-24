package cg.creamgod45.localization.ui

import cg.creamgod45.LanguageManagerBundle
import cg.creamgod45.localization.FileChangePreviewDto
import cg.creamgod45.settings.DisplayLanguage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

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
    fun `editable and read-only tags resolve to distinct localized strings`() {
        val editable = LanguageManagerBundle.messageForLanguage(DisplayLanguage.ENGLISH, "diff.file.tag.editable")
        val readonly = LanguageManagerBundle.messageForLanguage(DisplayLanguage.ENGLISH, "diff.file.tag.readonly")

        assertEquals("editable", editable)
        assertEquals("read-only", readonly)
        assertNotEquals(editable, readonly)
    }
}

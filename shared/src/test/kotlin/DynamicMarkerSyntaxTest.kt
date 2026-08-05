package cg.creamgod45.localization

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DynamicMarkerSyntaxTest {
    @Test
    fun `parses multiple repeatable groups only from comments`() {
        val source =
            """
            // @languageManager(method: dynamic, enum: custinfo,packinfo, error_code: invalid,expired)
            val text = "@languageManager(method: dynamic, enum: ignored)"
            """.trimIndent()

        val marker = DynamicMarkerSyntax.findAll(source).single()

        assertEquals(
            listOf(
                DynamicSourceGroupDto("enum", listOf("custinfo", "packinfo")),
                DynamicSourceGroupDto("error_code", listOf("invalid", "expired")),
            ),
            marker.groups,
        )
        assertNull(DynamicMarkerSyntax.findAt(source, source.indexOf("ignored")))
    }

    @Test
    fun `finds marker at caret or overlapping selection and renders an editable replacement`() {
        val source = "// @languageManager(method: dynamic, enum: first,second)"
        val caret = source.indexOf("first")
        val marker = DynamicMarkerSyntax.findAt(source, caret)!!

        assertEquals(marker, DynamicMarkerSyntax.findAt(source, marker.startOffset - 1, marker.startOffset + 3))
        assertEquals(
            "@languageManager(method: dynamic, enum: first,second)",
            DynamicMarkerSyntax.render(marker.groups),
        )
    }
}

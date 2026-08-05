package cg.creamgod45.localization.ui

import cg.creamgod45.localization.DynamicSourceGroupDto
import cg.creamgod45.localization.DynamicSourceRuleDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DynamicSourceInlaySupportTest {
    @Test
    fun `maps one based line and column without crossing line boundaries`() {
        val text = "first\r\nsecond\nthird"
        assertEquals(7, dynamicSourceOffset(text, 2, 1))
        assertEquals(13, dynamicSourceOffset(text, 2, 7))
        assertNull(dynamicSourceOffset(text, 2, 8))
        assertNull(dynamicSourceOffset(text, 4, 1))
    }

    @Test
    fun `rejects non positive positions`() {
        assertNull(dynamicSourceOffset("text", 0, 1))
        assertNull(dynamicSourceOffset("text", 1, 0))
    }

    @Test
    fun `counts distinct keys and limits tooltip details`() {
        val rule = DynamicSourceRuleDto(
            id = "rule",
            filePath = "example.php",
            line = 1,
            column = 1,
            groups = listOf(
                DynamicSourceGroupDto("first", listOf("one", "two")),
                DynamicSourceGroupDto("second", listOf("two", "three") + List(500) { "long-key-$it" }),
            ),
        )
        assertEquals(503, dynamicSourceKeyCount(rule))
        assertTrue(dynamicSourceTooltipDetails(rule).length <= 2_000)
    }
}

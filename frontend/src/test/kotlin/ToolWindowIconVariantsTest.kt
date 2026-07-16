package cg.creamgod45

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ToolWindowIconVariantsTest {
    @Test
    fun `sidebar icon variants keep theme colors and scale dimensions`() {
        val variants =
            mapOf(
                "icons/toolWindow.svg" to (16 to "#6C707E"),
                "icons/toolWindow_dark.svg" to (16 to "#CED0D6"),
                "icons/toolWindow@20x20.svg" to (20 to "#6C707E"),
                "icons/toolWindow@20x20_dark.svg" to (20 to "#CED0D6"),
            )

        variants.forEach { (resource, expected) ->
            val stream = assertNotNull(javaClass.classLoader.getResourceAsStream(resource), "Missing $resource")
            val svg = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }

            assertContains(svg, "width=\"${expected.first}\"")
            assertContains(svg, "height=\"${expected.first}\"")
            assertContains(svg, "fill=\"${expected.second}\"")
            assertEquals(1, Regex("<svg\\b").findAll(svg).count(), "Invalid SVG root in $resource")
        }
    }
}

package cg.creamgod45

import com.intellij.openapi.util.IconLoader
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

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
            val bytes = stream.use { it.readBytes() }
            val svg = bytes.toString(Charsets.UTF_8)

            assertTrue(bytes.size <= 3 * 1024, "$resource should remain a compact native vector")
            assertContains(svg, "width=\"${expected.first}\"")
            assertContains(svg, "height=\"${expected.first}\"")
            assertContains(svg, "viewBox=\"0 0 16 16\"")
            assertContains(svg, "fill=\"${expected.second}\"")
            assertEquals(1, Regex("<svg\\b").findAll(svg).count(), "Invalid SVG root in $resource")
            assertFalse(Regex("<(?:image|script|text)\\b", RegexOption.IGNORE_CASE).containsMatchIn(svg))
            assertFalse(Regex("(?:href|data:|javascript:)", RegexOption.IGNORE_CASE).containsMatchIn(svg))

            val rendered = IconLoader.getIcon("/$resource", javaClass)
            // Headless test JVMs without the SVG rasterizer return a 1x1 deferred placeholder, so only assert the
            // rendered dimensions when the icon subsystem actually rasterized the vector. The declared width/height
            // are already verified from the SVG source above, so this stays deterministic across environments.
            if (rendered.iconWidth > 1) {
                assertEquals(expected.first, rendered.iconWidth, "$resource rendered width")
                assertEquals(expected.first, rendered.iconHeight, "$resource rendered height")
            }
        }
    }
}

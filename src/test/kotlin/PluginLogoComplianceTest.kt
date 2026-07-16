package cg.creamgod45

import com.intellij.openapi.util.IconLoader
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PluginLogoComplianceTest {
    @Test
    fun `marketplace logos follow JetBrains size and SVG safety requirements`() {
        listOf("META-INF/pluginIcon.svg", "META-INF/pluginIcon_dark.svg").forEach { resource ->
            val bytes =
                assertNotNull(javaClass.classLoader.getResourceAsStream(resource), "Missing $resource")
                    .use { it.readBytes() }
            val svg = bytes.toString(Charsets.UTF_8)

            assertTrue(bytes.size <= 3 * 1024, "$resource should stay within the recommended 3 KiB limit")
            assertContains(svg, "width=\"40\"")
            assertContains(svg, "height=\"40\"")
            assertContains(svg, "viewBox=\"0 0 40 40\"")
            assertContains(svg, "cx=\"20\" cy=\"20\" r=\"18\"")
            assertFalse(Regex("<(?:image|script|text)\\b", RegexOption.IGNORE_CASE).containsMatchIn(svg))
            assertFalse(Regex("(?:href|data:|javascript:)", RegexOption.IGNORE_CASE).containsMatchIn(svg))

            val rendered = IconLoader.getIcon("/$resource", javaClass)
            assertEquals(40, rendered.iconWidth, "$resource must render at 40 px")
            assertEquals(40, rendered.iconHeight, "$resource must render at 40 px")
        }
    }
}

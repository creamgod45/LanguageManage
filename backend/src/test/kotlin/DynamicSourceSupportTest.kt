package cg.creamgod45

import cg.creamgod45.localization.DynamicSourceGroupDto
import cg.creamgod45.localization.DynamicSourceRuleDto
import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DynamicSourceSupportTest {
    private val temp = Files.createTempDirectory("language-manager-dynamic-source-test")

    @AfterTest
    fun cleanup() {
        temp.toFile().deleteRecursively()
    }

    @Test
    fun `comment markers expose keys from repeatable named groups`() {
        val content =
            """
            // @languageManager(method: dynamic, enum: custinfo,packinfo, error_code: invalid,expired)
            val ignored = "@languageManager(method: dynamic, enum: must.not.count)"
            # @languageManager(method: dynamic, enum: status.ready)
            """.trimIndent()

        val occurrences = DynamicSourceSupport.markerOccurrences(content)

        assertEquals(listOf("custinfo", "packinfo", "invalid", "expired", "status.ready"), occurrences.map { it.key })
    }

    @Test
    fun `configured rule is normalized under root and resolves lazy source offset`() {
        val source = temp.resolve("src/service.php").apply {
            parent.createDirectories()
            writeText("first line\n  selected value\n")
        }
        val rule =
            DynamicSourceRuleDto(
                id = "rule-1",
                filePath = source.toString(),
                line = 2,
                column = 3,
                groups = listOf(DynamicSourceGroupDto("enum", listOf(" auth.failed ", "auth.failed"))),
            )

        val normalized = DynamicSourceSupport.normalizeRules(listOf(rule), temp).single()
        val occurrence = DynamicSourceSupport.ruleOccurrences(listOf(normalized), temp).single()

        assertEquals(listOf("auth.failed"), normalized.groups.single().keys)
        assertEquals(source.toRealPath(), occurrence.first)
        assertEquals("auth.failed", occurrence.second.key)
        assertEquals("first line\n".length + 2, occurrence.second.offset)
    }

    @Test
    fun `configured rule rejects files outside scheme usage root`() {
        val outside = Files.createTempFile("language-manager-outside", ".php")
        try {
            val rule = DynamicSourceRuleDto("rule", outside.toString(), 1, 1, groups = listOf(DynamicSourceGroupDto("enum", listOf("auth.failed"))))
            assertFailsWith<IllegalArgumentException> { DynamicSourceSupport.normalizeRules(listOf(rule), temp) }
        } finally {
            assertTrue(Files.deleteIfExists(outside))
        }
    }

    @Test
    fun `standalone marker conversion removes its complete comment line`() {
        val source =
            """
            before
            // @languageManager(method: dynamic, enum: auth.failed,status.ready)
            after
            """.trimIndent()
        val marker = cg.creamgod45.localization.DynamicMarkerSyntax.findAll(source).single()

        assertEquals("before\nafter", DynamicSourceSupport.removeMarker(source, marker.startOffset, marker.endOffsetExclusive))
    }

    @Test
    fun `embedded marker conversion preserves surrounding comment text`() {
        val source = "// keep @languageManager(method: dynamic, enum: auth.failed) note"
        val marker = cg.creamgod45.localization.DynamicMarkerSyntax.findAll(source).single()

        assertEquals("// keep  note", DynamicSourceSupport.removeMarker(source, marker.startOffset, marker.endOffsetExclusive))
    }

    @Test
    fun `non invasive rule renders a file appropriate marker at its source line`() {
        assertEquals(
            "    {{-- @languageManager(method: dynamic, enum: auth.failed) --}}\n",
            DynamicSourceSupport.markerCommentLine(
                "view.blade.php",
                listOf(DynamicSourceGroupDto("enum", listOf("auth.failed"))),
                "    ",
            ),
        )
        assertEquals(8, DynamicSourceSupport.lineStartOffset("one\ntwo\nthree", 3))
    }
}

package cg.creamgod45

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LocaleMetadataSupportTest {
    @Test
    fun `normalizes bounded locale notes and removes blank values`() {
        assertEquals("Mexican Spanish, formal tone", LocaleMetadataSupport.normalizeNote("  Mexican Spanish, formal tone  "))
        assertEquals(mapOf("es-MX" to "formal"), LocaleMetadataSupport.normalizeNotes(mapOf("es-MX" to " formal ", "th" to " ")))
    }

    @Test
    fun `rejects invalid locale metadata`() {
        assertFailsWith<IllegalArgumentException> { LocaleMetadataSupport.normalizeNote("bad\u0000note") }
        assertFailsWith<IllegalArgumentException> { LocaleMetadataSupport.normalizeNote("x".repeat(501)) }
        assertFailsWith<IllegalArgumentException> { LocaleMetadataSupport.normalizeNotes(mapOf("../es" to "Spanish")) }
    }
}

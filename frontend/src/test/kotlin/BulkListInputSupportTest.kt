package cg.creamgod45.localization.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class BulkListInputSupportTest {
    @Test
    fun `splits comma and line separated exclusions and removes blanks and duplicates`() {
        assertEquals(
            listOf("vendor", "storage/cache", ".generated"),
            parseBulkListValues(" vendor, storage/cache\r\n.generated\n vendor ,, "),
        )
    }
}

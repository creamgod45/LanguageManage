package cg.creamgod45.localization.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class BulkDynamicKeysSupportTest {
    @Test
    fun `newline input trims removes blanks and preserves first occurrence order`() {
        assertEquals(
            listOf("auth.failed", "profile.name"),
            splitBulkDynamicKeys(" auth.failed\r\n\r\nprofile.name\nauth.failed ", null),
        )
    }

    @Test
    fun `custom separator is treated as literal text rather than regex`() {
        assertEquals(
            listOf("first", "second", "third"),
            splitBulkDynamicKeys("first.*second.*third", ".*"),
        )
    }
}

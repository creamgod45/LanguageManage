package cg.creamgod45.localization.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class LaravelTranslationReferenceTest {
    @Test
    fun `converts internal nested namespace to Laravel group path`() {
        assertEquals("admin/customer.item", laravelTranslationReference("admin.customer", "item"))
        assertEquals("Exact JSON key", laravelTranslationReference("", "Exact JSON key"))
    }
}

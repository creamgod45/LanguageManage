package cg.creamgod45.localization.ui

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DynamicSourceGutterLifecycleTest {
    @Test
    fun `does not observe documents without an active scheme`() {
        assertFalse(shouldObserveDynamicSourceDocument(hasActiveScheme = false, intentionPreviewActive = false))
    }

    @Test
    fun `does not observe intention preview documents`() {
        assertFalse(shouldObserveDynamicSourceDocument(hasActiveScheme = true, intentionPreviewActive = true))
    }

    @Test
    fun `observes ordinary documents only while a scheme is active`() {
        assertTrue(shouldObserveDynamicSourceDocument(hasActiveScheme = true, intentionPreviewActive = false))
    }
}

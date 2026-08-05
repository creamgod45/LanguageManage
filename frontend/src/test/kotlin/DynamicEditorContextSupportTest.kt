package cg.creamgod45.localization.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class DynamicEditorContextSupportTest {
    @Test
    fun `uses caret offset when no text is selected`() {
        assertEquals(42, editorContextOffset(false, 0, 42))
    }

    @Test
    fun `keeps selection start when selected text supplies initial keys`() {
        assertEquals(12, editorContextOffset(true, 12, 30))
    }
}

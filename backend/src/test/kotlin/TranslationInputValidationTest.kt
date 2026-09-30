package cg.creamgod45

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TranslationInputValidationTest {
    @Test
    fun `sentence translation keys are accepted`() {
        assertEquals(
            "Not powered on or not detected",
            TranslationInputValidation.key("Not powered on or not detected"),
        )
        assertEquals("設備未開啟／未偵測", TranslationInputValidation.key("設備未開啟／未偵測"))
    }

    @Test
    fun `outer whitespace is kept as typed because it may be intentional`() {
        assertEquals("  Status: offline?  ", TranslationInputValidation.key("  Status: offline?  "))
        assertEquals(" app.name", TranslationInputValidation.key(" app.name"))
        assertEquals("　app.name　", TranslationInputValidation.key("　app.name　"))
    }

    @Test
    fun `blank control and overlong keys are rejected`() {
        assertFailsWith<IllegalArgumentException> { TranslationInputValidation.key("   ") }
        assertFailsWith<IllegalArgumentException> { TranslationInputValidation.key("　　") }
        assertFailsWith<IllegalArgumentException> { TranslationInputValidation.key("unsafe\nkey") }
        assertFailsWith<IllegalArgumentException> { TranslationInputValidation.key("k".repeat(257)) }
    }
}

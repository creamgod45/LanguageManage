package cg.creamgod45.localization.ui

import cg.creamgod45.localization.HardcodedTextCandidateDto
import cg.creamgod45.localization.HardcodedTextConfidence
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AnalysisValuePatternFilterTest {
    @Test
    fun `recognizes each selectable naming convention independently`() {
        assertTrue(AnalysisValuePatternFilter.ENGLISH_WORD.matches("Submit"))
        assertTrue(AnalysisValuePatternFilter.CAMEL_CASE.matches("submitForm2"))
        assertTrue(AnalysisValuePatternFilter.PASCAL_CASE.matches("SubmitForm2"))
        assertTrue(AnalysisValuePatternFilter.SNAKE_CASE.matches("submit_form_2"))
        assertTrue(AnalysisValuePatternFilter.MACRO_CASE.matches("SUBMIT_FORM_2"))
        assertTrue(AnalysisValuePatternFilter.KEBAB_CASE.matches("submit-form-2"))
        assertTrue(AnalysisValuePatternFilter.DOT_CASE.matches("submit.form.2"))
        assertFalse(AnalysisValuePatternFilter.ENGLISH_WORD.matches("submitForm"))
        assertFalse(AnalysisValuePatternFilter.ENGLISH_WORD.matches("SubmitForm"))
    }

    @Test
    fun `multiple selections exclude the union but preserve sentences`() {
        val filters = setOf(AnalysisValuePatternFilter.CAMEL_CASE, AnalysisValuePatternFilter.MACRO_CASE)

        assertTrue(excludedByValuePatterns("submitForm", filters))
        assertTrue(excludedByValuePatterns("SUBMIT_FORM", filters))
        assertFalse(excludedByValuePatterns("Submit form", filters))
        assertFalse(excludedByValuePatterns("submit_form", filters))
    }

    @Test
    fun `empty selection excludes nothing`() {
        assertFalse(excludedByValuePatterns("submitForm", emptySet()))
    }

    @Test
    fun `export and table filtering can apply search confidence and naming exclusions together`() {
        val items =
            listOf(
                HardcodedTextCandidateDto("src/form.php", 1, 1, "Submit form", HardcodedTextConfidence.HIGH),
                HardcodedTextCandidateDto("src/form.php", 2, 1, "submitForm", HardcodedTextConfidence.HIGH),
                HardcodedTextCandidateDto("src/other.php", 3, 1, "Submit form later", HardcodedTextConfidence.LOW),
            )

        val filtered =
            filterAnalysisItems(
                items,
                query = "form",
                confidence = HardcodedTextConfidence.HIGH,
                excludedPatterns = setOf(AnalysisValuePatternFilter.CAMEL_CASE),
            )

        assertTrue(filtered.size == 1)
        assertTrue(filtered.single().text == "Submit form")
    }
}

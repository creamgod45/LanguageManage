package cg.creamgod45.localization.ui

import cg.creamgod45.localization.IssueSeverity
import cg.creamgod45.localization.LanguageIssueDto
import kotlin.test.Test
import kotlin.test.assertEquals

class IssueVisibilityTest {
    private val issues =
        listOf(
            issue("DUPLICATE_VALUE"),
            issue("UNUSED_KEY"),
            issue("MISSING_TRANSLATION"),
        )

    @Test
    fun `issue preferences hide only requested suggestions`() {
        assertEquals(listOf("UNUSED_KEY", "MISSING_TRANSLATION"), visibleIssues(issues, true, false).map { it.code })
        assertEquals(listOf("DUPLICATE_VALUE", "MISSING_TRANSLATION"), visibleIssues(issues, false, true).map { it.code })
        assertEquals(listOf("MISSING_TRANSLATION"), visibleIssues(issues, true, true).map { it.code })
        assertEquals(issues, visibleIssues(issues, false, false))
    }

    private fun issue(code: String) = LanguageIssueDto("scheme", severity = IssueSeverity.INFO, code = code, message = code)
}

package cg.creamgod45.localization.ui

import cg.creamgod45.localization.LanguageIssueDto

internal fun visibleIssues(
    issues: List<LanguageIssueDto>,
    ignoreDuplicateValues: Boolean,
    ignoreUnusedKeys: Boolean,
): List<LanguageIssueDto> =
    issues.filterNot { issue ->
        ignoreDuplicateValues && issue.code == "DUPLICATE_VALUE" ||
            ignoreUnusedKeys && issue.code == "UNUSED_KEY"
    }

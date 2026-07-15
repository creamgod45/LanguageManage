package cg.creamgod45

import cg.creamgod45.localization.*
import cg.creamgod45.LanguageManagerBackendBundle.message as backendMessage

internal object LocalizationAnalysis {
    fun analyze(
        schemeId: String,
        entries: List<LanguageEntryDto>,
    ): List<LanguageIssueDto> {
        val issues = mutableListOf<LanguageIssueDto>()
        entries.filter { it.value.isBlank() }.forEach {
            issues +=
                LanguageIssueDto(
                    schemeId,
                    it.filePath,
                    it.key,
                    IssueSeverity.ERROR,
                    "MISSING_VALUE",
                    backendMessage("analysis.missing.value", it.locale, it.namespace, it.key),
                    true,
                )
        }
        entries.groupBy { Triple(it.locale, it.namespace, it.key) }.filterValues { it.size > 1 }.forEach { (key, duplicates) ->
            issues +=
                LanguageIssueDto(
                    schemeId,
                    duplicates.first().filePath,
                    key.third,
                    IssueSeverity.ERROR,
                    "DUPLICATE_KEY",
                    backendMessage("analysis.duplicate.key", key.first, key.second, key.third),
                )
        }
        entries.filter { it.value.isNotBlank() }.groupBy { it.value }.filterValues { it.size > 1 }.forEach { (value, duplicates) ->
            issues +=
                LanguageIssueDto(
                    schemeId,
                    duplicates.first().filePath,
                    duplicates.first().key,
                    IssueSeverity.WARNING,
                    "DUPLICATE_VALUE",
                    backendMessage("analysis.duplicate.value", duplicates.size, value.take(80)),
                )
        }
        val locales = entries.map { it.locale }.toSet()
        entries.groupBy { it.namespace to it.key }.forEach { (key, translated) ->
            val missing = locales - translated.map { it.locale }.toSet()
            if (missing.isNotEmpty()) {
                issues +=
                    LanguageIssueDto(
                        schemeId,
                        translated.first().filePath,
                        key.second,
                        IssueSeverity.WARNING,
                        "MISSING_TRANSLATION",
                        backendMessage("analysis.missing.translation", key.first, key.second, missing.sorted().joinToString()),
                    )
            }
        }
        entries.filter { it.usageCount == 0 }.forEach {
            issues +=
                LanguageIssueDto(
                    schemeId,
                    it.filePath,
                    it.key,
                    IssueSeverity.INFO,
                    "UNUSED_KEY",
                    backendMessage("analysis.unused.key", it.namespace, it.key),
                )
        }
        return issues
    }
}

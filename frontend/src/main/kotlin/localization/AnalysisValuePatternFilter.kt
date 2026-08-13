package cg.creamgod45.localization.ui

import cg.creamgod45.localization.HardcodedTextCandidateDto
import cg.creamgod45.localization.HardcodedTextConfidence

internal enum class AnalysisValuePatternFilter {
    ENGLISH_WORD,
    CAMEL_CASE,
    PASCAL_CASE,
    SNAKE_CASE,
    MACRO_CASE,
    KEBAB_CASE,
    DOT_CASE,
    ;

    fun matches(value: String): Boolean =
        when (this) {
            ENGLISH_WORD ->
                value.matches(Regex("(?:[a-z]+|[A-Z][a-z]+|[A-Z]+)")) &&
                    !CAMEL_CASE.matches(value) && !PASCAL_CASE.matches(value)
            CAMEL_CASE -> value.matches(Regex("[a-z]+(?:[A-Z][A-Za-z0-9]*)+"))
            PASCAL_CASE -> value.matches(Regex("[A-Z][A-Za-z0-9]*[A-Z][A-Za-z0-9]*"))
            SNAKE_CASE -> value.matches(Regex("[a-z][a-z0-9]*(?:_[a-z0-9]+)+"))
            MACRO_CASE -> value.matches(Regex("[A-Z][A-Z0-9]*(?:_[A-Z0-9]+)+"))
            KEBAB_CASE -> value.matches(Regex("[a-z][a-z0-9]*(?:-[a-z0-9]+)+"))
            DOT_CASE -> value.matches(Regex("[a-z][a-z0-9]*(?:\\.[a-z0-9]+)+"))
        }
}

internal fun excludedByValuePatterns(
    value: String,
    filters: Set<AnalysisValuePatternFilter>,
): Boolean = filters.any { it.matches(value) }

internal fun filterAnalysisItems(
    items: List<HardcodedTextCandidateDto>,
    query: String,
    confidence: HardcodedTextConfidence?,
    excludedPatterns: Set<AnalysisValuePatternFilter>,
): List<HardcodedTextCandidateDto> =
    items.filter { item ->
        (query.isBlank() || item.text.contains(query.trim(), true) || item.filePath.contains(query.trim(), true)) &&
            (confidence == null || item.confidence == confidence) &&
            !excludedByValuePatterns(item.text, excludedPatterns)
    }

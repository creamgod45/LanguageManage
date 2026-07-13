package cg.creamgod45.localization

import kotlinx.serialization.Serializable

@Serializable
enum class SearchMode { FUZZY, EXACT }

@Serializable
enum class IssueSeverity { INFO, WARNING, ERROR }

@Serializable
data class LanguageSchemeDto(
    val id: String,
    val name: String,
    val files: List<String>,
    val updatedAtEpochMs: Long,
)

@Serializable
data class LanguageEntryDto(
    val id: String,
    val schemeId: String,
    val filePath: String,
    val locale: String,
    val namespace: String,
    val key: String,
    val value: String,
    val usageCount: Int = 0,
)

@Serializable
data class LanguageIssueDto(
    val schemeId: String,
    val filePath: String = "",
    val key: String = "",
    val severity: IssueSeverity,
    val code: String,
    val message: String,
    val repairable: Boolean = false,
)

@Serializable
data class LocalizationStateDto(
    val schemes: List<LanguageSchemeDto> = emptyList(),
    val activeSchemeId: String? = null,
    val entries: List<LanguageEntryDto> = emptyList(),
    val issues: List<LanguageIssueDto> = emptyList(),
    val busy: Boolean = false,
    val errorMessage: String? = null,
)

@Serializable
data class EntryMutationDto(
    val id: String? = null,
    val filePath: String,
    val locale: String,
    val namespace: String,
    val key: String,
    val value: String,
)

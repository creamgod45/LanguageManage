package cg.creamgod45.localization

import kotlinx.serialization.Serializable

@Serializable
enum class SearchMode { FUZZY, EXACT }

enum class TranslationRowFilter { ALL, MISSING_TRANSLATION, ZERO_USAGE }

@Serializable
enum class AiProviderType { OPENAI_COMPATIBLE, ANTHROPIC }

@Serializable
enum class IssueSeverity { INFO, WARNING, ERROR }

val DEFAULT_USAGE_REGEX_PATTERNS: List<String> =
    listOf(
        """\(\s*(?<quote>["'])(?<key>[^\r\n]{1,256}?)\k<quote>\s*\)""",
    )

val DEFAULT_USAGE_EXCLUDED_DIRECTORIES: List<String> =
    listOf(
        ".git",
        ".github",
        "docs",
        "vendor",
        "storage",
        "database",
        "gradle",
        ".gradle",
        "build",
        "out",
        "dist",
        "target",
        "node_modules",
        ".idea",
        ".run",
        ".vscode",
        ".fleet",
        ".vs",
        ".settings",
        ".metadata",
        "nbproject",
        ".env",
        ".claude",
        ".codex",
        ".gemini",
        ".agents",
        ".ai",
    )

const val DEFAULT_MAX_LANGUAGE_FILE_KB = 2_048
const val DEFAULT_MAX_LANGUAGE_SCHEME_MB = 20
const val DEFAULT_MAX_ENTRIES_PER_FILE = 20_000
const val DEFAULT_MAX_ENTRIES_PER_SCHEME = 100_000
const val HARD_MAX_LANGUAGE_FILE_KB = 10_240
const val HARD_MAX_LANGUAGE_SCHEME_MB = 100
const val HARD_MAX_ENTRIES_PER_FILE = 100_000
const val HARD_MAX_ENTRIES_PER_SCHEME = 250_000

@Serializable
data class UsageScanSettingsDto(
    val basePath: String = "",
    val regexPatterns: List<String> = DEFAULT_USAGE_REGEX_PATTERNS,
    val excludedDirectories: List<String> = DEFAULT_USAGE_EXCLUDED_DIRECTORIES,
    val maxLanguageFileKb: Int = DEFAULT_MAX_LANGUAGE_FILE_KB,
    val maxLanguageSchemeMb: Int = DEFAULT_MAX_LANGUAGE_SCHEME_MB,
    val maxEntriesPerFile: Int = DEFAULT_MAX_ENTRIES_PER_FILE,
    val maxEntriesPerScheme: Int = DEFAULT_MAX_ENTRIES_PER_SCHEME,
)

@Serializable
data class LanguageSchemeDto(
    val id: String,
    val name: String,
    val files: List<String>,
    val updatedAtEpochMs: Long,
    val usageScanSettings: UsageScanSettingsDto = UsageScanSettingsDto(),
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

@Serializable
data class AiTranslationItemDto(
    val id: String,
    val namespace: String,
    val key: String,
    val sourceValue: String,
)

@Serializable
data class AiTranslationRequestDto(
    val provider: AiProviderType,
    val endpoint: String,
    val model: String,
    val apiToken: String,
    val sourceLocale: String,
    val targetLocale: String,
    val items: List<AiTranslationItemDto>,
    val previousSuggestions: List<AiTranslationSuggestionDto> = emptyList(),
    val userFeedback: String = "",
    val temperature: Double? = null,
)

@Serializable
data class AiTranslationSuggestionDto(
    val id: String,
    val translatedValue: String,
)

@Serializable
data class AiTranslationResultDto(
    val suggestions: List<AiTranslationSuggestionDto>,
)

@Serializable
data class ChangePreviewRequestDto(
    val normalizeAll: Boolean = false,
    val repairEntryIds: List<String> = emptyList(),
    val deleteEntryIds: List<String> = emptyList(),
)

@Serializable
data class FileChangePreviewDto(
    val filePath: String,
    val beforeContent: String,
    val afterContent: String,
    val beforeSha256: String,
)

@Serializable
data class ChangePreviewDto(
    val files: List<FileChangePreviewDto> = emptyList(),
)

@Serializable
data class LocaleVersionRequestDto(
    val sourceLocale: String,
    val targetLocale: String,
)

@Serializable
data class LanguageFileCandidateDto(
    val filePath: String,
    val format: String,
    val locale: String = "",
    val namespace: String = "",
    val entryCount: Int = 0,
    val recognized: Boolean = false,
    val errorMessage: String? = null,
)

@Serializable
data class FolderDiscoveryDto(
    val folderPath: String,
    val files: List<LanguageFileCandidateDto> = emptyList(),
    val truncated: Boolean = false,
    val folderPaths: List<String> = listOf(folderPath),
)

@Serializable
data class PortableLanguageSchemeDto(
    val name: String,
    val files: List<String>,
    val usageScanSettings: UsageScanSettingsDto = UsageScanSettingsDto(),
)

@Serializable
data class SchemeSettingsTransferDto(
    val formatVersion: Int = 1,
    val schemes: List<PortableLanguageSchemeDto> = emptyList(),
)

@Serializable
data class SchemeImportFilePreviewDto(
    val configuredPath: String,
    val resolvedPath: String,
    val available: Boolean,
    val recognized: Boolean,
    val detail: String = "",
)

@Serializable
data class SchemeImportItemPreviewDto(
    val name: String,
    val files: List<SchemeImportFilePreviewDto>,
)

@Serializable
data class SchemeImportPreviewDto(
    val formatVersion: Int = 0,
    val basePath: String = "",
    val schemes: List<SchemeImportItemPreviewDto> = emptyList(),
) {
    val canImport: Boolean get() =
        schemes.isNotEmpty() &&
            schemes.all { scheme -> scheme.files.isNotEmpty() && scheme.files.all { it.available } }
}

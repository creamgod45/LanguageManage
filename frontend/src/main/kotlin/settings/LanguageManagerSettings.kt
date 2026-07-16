package cg.creamgod45.settings

import cg.creamgod45.localization.DEFAULT_USAGE_EXCLUDED_DIRECTORIES
import cg.creamgod45.localization.AiProviderType
import cg.creamgod45.localization.DEFAULT_USAGE_REGEX_PATTERNS
import cg.creamgod45.localization.DEFAULT_MAX_ENTRIES_PER_FILE
import cg.creamgod45.localization.DEFAULT_MAX_ENTRIES_PER_SCHEME
import cg.creamgod45.localization.DEFAULT_MAX_LANGUAGE_FILE_KB
import cg.creamgod45.localization.DEFAULT_MAX_LANGUAGE_SCHEME_MB
import cg.creamgod45.localization.HARD_MAX_ENTRIES_PER_FILE
import cg.creamgod45.localization.HARD_MAX_ENTRIES_PER_SCHEME
import cg.creamgod45.localization.HARD_MAX_LANGUAGE_FILE_KB
import cg.creamgod45.localization.HARD_MAX_LANGUAGE_SCHEME_MB
import cg.creamgod45.localization.UsageScanSettingsDto
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import java.nio.file.Path
import java.util.Locale

internal enum class DisplayLanguage(
    val locale: Locale?,
) {
    AUTO(null),
    ENGLISH(Locale.ENGLISH),
    TRADITIONAL_CHINESE(Locale.TAIWAN),
    SIMPLIFIED_CHINESE(Locale.SIMPLIFIED_CHINESE),
    JAPANESE(Locale.JAPANESE),
    KOREAN(Locale.KOREAN),
}

internal enum class DefaultBasePathMode { PROJECT_DIRECTORY, PARENT_LEVELS }

internal val LEGACY_DEFAULT_EXCLUSIONS =
    listOf(
        ".git",
        ".github",
        "docs",
        "vendor",
        ".env",
        ".run",
        ".claude",
        ".codex",
        ".gemini",
        ".agents",
        ".ai",
        ".vscode",
    )

@Service(Service.Level.APP)
@State(
    name = "cg.creamgod45.localization.LanguageManagerSettings",
    storages = [Storage(value = "LanguageManager.xml", roamingType = RoamingType.DISABLED)],
)
internal class LanguageManagerSettings : PersistentStateComponent<LanguageManagerSettings.SettingsState> {
    class SettingsState {
        var displayLanguage: String = DisplayLanguage.AUTO.name
        var defaultBasePathMode: String = DefaultBasePathMode.PROJECT_DIRECTORY.name
        var defaultParentLevels: Int = 1
        var defaultRegexPatterns: MutableList<String> = DEFAULT_USAGE_REGEX_PATTERNS.toMutableList()
        var defaultExcludedDirectories: MutableList<String> = DEFAULT_USAGE_EXCLUDED_DIRECTORIES.toMutableList()
        var defaultMaxLanguageFileKb: Int = DEFAULT_MAX_LANGUAGE_FILE_KB
        var defaultMaxLanguageSchemeMb: Int = DEFAULT_MAX_LANGUAGE_SCHEME_MB
        var defaultMaxEntriesPerFile: Int = DEFAULT_MAX_ENTRIES_PER_FILE
        var defaultMaxEntriesPerScheme: Int = DEFAULT_MAX_ENTRIES_PER_SCHEME
        var ignoreDuplicateValueIssues: Boolean = false
        var ignoreUnusedKeyIssues: Boolean = false
        var aiProvider: String = AiProviderType.OPENAI_COMPATIBLE.name
        var aiEndpoint: String = "https://api.openai.com/v1/chat/completions"
        var aiModel: String = ""
        var aiTemperature: String = ""
    }

    private var settingsState = SettingsState()

    var displayLanguage: DisplayLanguage
        get() = runCatching { DisplayLanguage.valueOf(settingsState.displayLanguage) }.getOrDefault(DisplayLanguage.AUTO)
        set(value) {
            settingsState.displayLanguage = value.name
        }

    var defaultBasePathMode: DefaultBasePathMode
        get() =
            runCatching { DefaultBasePathMode.valueOf(settingsState.defaultBasePathMode) }
                .getOrDefault(DefaultBasePathMode.PROJECT_DIRECTORY)
        set(value) {
            settingsState.defaultBasePathMode = value.name
        }

    var defaultParentLevels: Int
        get() = settingsState.defaultParentLevels.coerceIn(1, MAX_PARENT_LEVELS)
        set(value) {
            settingsState.defaultParentLevels = value.coerceIn(1, MAX_PARENT_LEVELS)
        }

    var defaultRegexPatterns: List<String>
        get() = settingsState.defaultRegexPatterns.toList()
        set(value) {
            settingsState.defaultRegexPatterns = value.toMutableList()
        }

    var defaultExcludedDirectories: List<String>
        get() = settingsState.defaultExcludedDirectories.toList()
        set(value) {
            settingsState.defaultExcludedDirectories = value.toMutableList()
        }

    var defaultMaxLanguageFileKb: Int
        get() = settingsState.defaultMaxLanguageFileKb.coerceIn(1, HARD_MAX_LANGUAGE_FILE_KB)
        set(value) {
            settingsState.defaultMaxLanguageFileKb = value.coerceIn(1, HARD_MAX_LANGUAGE_FILE_KB)
        }

    var defaultMaxLanguageSchemeMb: Int
        get() = settingsState.defaultMaxLanguageSchemeMb.coerceIn(1, HARD_MAX_LANGUAGE_SCHEME_MB)
        set(value) {
            settingsState.defaultMaxLanguageSchemeMb = value.coerceIn(1, HARD_MAX_LANGUAGE_SCHEME_MB)
        }

    var defaultMaxEntriesPerFile: Int
        get() = settingsState.defaultMaxEntriesPerFile.coerceIn(1, HARD_MAX_ENTRIES_PER_FILE)
        set(value) {
            settingsState.defaultMaxEntriesPerFile = value.coerceIn(1, HARD_MAX_ENTRIES_PER_FILE)
        }

    var defaultMaxEntriesPerScheme: Int
        get() = settingsState.defaultMaxEntriesPerScheme.coerceIn(1, HARD_MAX_ENTRIES_PER_SCHEME)
        set(value) {
            settingsState.defaultMaxEntriesPerScheme = value.coerceIn(1, HARD_MAX_ENTRIES_PER_SCHEME)
        }

    var ignoreDuplicateValueIssues: Boolean
        get() = settingsState.ignoreDuplicateValueIssues
        set(value) {
            settingsState.ignoreDuplicateValueIssues = value
        }

    var ignoreUnusedKeyIssues: Boolean
        get() = settingsState.ignoreUnusedKeyIssues
        set(value) {
            settingsState.ignoreUnusedKeyIssues = value
        }

    var aiProvider: AiProviderType
        get() = runCatching { AiProviderType.valueOf(settingsState.aiProvider) }.getOrDefault(AiProviderType.OPENAI_COMPATIBLE)
        set(value) { settingsState.aiProvider = value.name }

    var aiEndpoint: String
        get() = settingsState.aiEndpoint
        set(value) { settingsState.aiEndpoint = value.trim() }

    var aiModel: String
        get() = settingsState.aiModel
        set(value) { settingsState.aiModel = value.trim() }

    var aiTemperature: String
        get() = settingsState.aiTemperature.trim()
        set(value) { settingsState.aiTemperature = value.trim() }

    fun defaultUsageSettings(projectBasePath: String?): UsageScanSettingsDto =
        UsageScanSettingsDto(
            basePath = resolveDefaultBasePath(projectBasePath, defaultBasePathMode, defaultParentLevels),
            regexPatterns = defaultRegexPatterns,
            excludedDirectories = defaultExcludedDirectories,
            maxLanguageFileKb = defaultMaxLanguageFileKb,
            maxLanguageSchemeMb = defaultMaxLanguageSchemeMb,
            maxEntriesPerFile = defaultMaxEntriesPerFile,
            maxEntriesPerScheme = defaultMaxEntriesPerScheme,
        )

    override fun getState(): SettingsState = settingsState

    override fun loadState(state: SettingsState) {
        val defaults = state.defaultExcludedDirectories
        if (defaults == LEGACY_DEFAULT_EXCLUSIONS || defaults == LEGACY_DEFAULT_EXCLUSIONS + "storage") {
            state.defaultExcludedDirectories = DEFAULT_USAGE_EXCLUDED_DIRECTORIES.toMutableList()
        }
        settingsState = state
    }

    companion object {
        const val MAX_PARENT_LEVELS = 10

        fun currentLanguage(): DisplayLanguage =
            ApplicationManager.getApplication()?.getService(LanguageManagerSettings::class.java)?.displayLanguage
                ?: DisplayLanguage.AUTO

        fun getInstance(): LanguageManagerSettings = ApplicationManager.getApplication().getService(LanguageManagerSettings::class.java)
    }
}

internal fun resolveDefaultBasePath(
    projectBasePath: String?,
    mode: DefaultBasePathMode,
    parentLevels: Int,
): String {
    if (mode == DefaultBasePathMode.PROJECT_DIRECTORY || projectBasePath.isNullOrBlank()) return ""
    var path = runCatching { Path.of(projectBasePath) }.getOrNull() ?: return ""
    repeat(parentLevels.coerceIn(1, LanguageManagerSettings.MAX_PARENT_LEVELS)) {
        path = path.parent ?: return@repeat
    }
    return path.toString()
}

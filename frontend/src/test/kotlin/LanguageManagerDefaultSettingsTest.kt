package cg.creamgod45.settings

import cg.creamgod45.localization.DEFAULT_MAX_ENTRIES_PER_FILE
import cg.creamgod45.localization.DEFAULT_MAX_ENTRIES_PER_SCHEME
import cg.creamgod45.localization.DEFAULT_MAX_LANGUAGE_FILE_KB
import cg.creamgod45.localization.DEFAULT_MAX_LANGUAGE_SCHEME_MB
import cg.creamgod45.localization.DEFAULT_USAGE_EXCLUDED_DIRECTORIES
import cg.creamgod45.localization.DEFAULT_USAGE_REGEX_PATTERNS
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class LanguageManagerDefaultSettingsTest {
    @Test
    fun `project directory mode uses empty base path marker`() {
        assertEquals("", resolveDefaultBasePath("workspace/project", DefaultBasePathMode.PROJECT_DIRECTORY, 1))
    }

    @Test
    fun `parent mode resolves configured number of levels`() {
        val project = Path.of("root", "workspace", "apps", "project").toAbsolutePath()

        assertEquals(
            project.parent.parent.toString(),
            resolveDefaultBasePath(project.toString(), DefaultBasePathMode.PARENT_LEVELS, 2),
        )
    }

    @Test
    fun `new settings state contains requested plugin defaults`() {
        val state = LanguageManagerSettings.SettingsState()

        assertEquals(DefaultBasePathMode.PROJECT_DIRECTORY.name, state.defaultBasePathMode)
        assertEquals(1, state.defaultParentLevels)
        assertEquals(DEFAULT_USAGE_REGEX_PATTERNS, state.defaultRegexPatterns)
        assertEquals(DEFAULT_USAGE_EXCLUDED_DIRECTORIES, state.defaultExcludedDirectories)
        assertEquals(DEFAULT_MAX_LANGUAGE_FILE_KB, state.defaultMaxLanguageFileKb)
        assertEquals(DEFAULT_MAX_LANGUAGE_SCHEME_MB, state.defaultMaxLanguageSchemeMb)
        assertEquals(DEFAULT_MAX_ENTRIES_PER_FILE, state.defaultMaxEntriesPerFile)
        assertEquals(DEFAULT_MAX_ENTRIES_PER_SCHEME, state.defaultMaxEntriesPerScheme)
        assertEquals(false, state.ignoreDuplicateValueIssues)
        assertEquals(false, state.ignoreUnusedKeyIssues)
        assertEquals("", state.aiTemperature)
    }

    @Test
    fun `legacy default exclusions gain newly supplied common directories`() {
        listOf(LEGACY_DEFAULT_EXCLUSIONS, LEGACY_DEFAULT_EXCLUSIONS + "storage").forEach { previousDefaults ->
            val legacy =
                LanguageManagerSettings.SettingsState().apply {
                    defaultExcludedDirectories = previousDefaults.toMutableList()
                }
            val settings = LanguageManagerSettings()

            settings.loadState(legacy)

            assertEquals(DEFAULT_USAGE_EXCLUDED_DIRECTORIES, settings.defaultExcludedDirectories)
        }
    }

    @Test
    fun `custom exclusion lists are not replaced during migration`() {
        val custom = mutableListOf("vendor", "my-generated-files")
        val state = LanguageManagerSettings.SettingsState().apply { defaultExcludedDirectories = custom }
        val settings = LanguageManagerSettings()

        settings.loadState(state)

        assertEquals(custom, settings.defaultExcludedDirectories)
    }
}

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
    fun `quick in-place editing is enabled by default and can be turned off`() {
        assertEquals(true, LanguageManagerSettings.SettingsState().quickInlineEditEnabled)

        val settings = LanguageManagerSettings()
        settings.quickInlineEditEnabled = false
        val restored = LanguageManagerSettings().apply { loadState(settings.state) }

        assertEquals(false, restored.quickInlineEditEnabled)
    }

    @Test
    fun `settings saved before quick editing existed keep it enabled`() {
        // A LanguageManager.xml written by 1.7.x has no quickInlineEditEnabled option.
        val legacyXml =
            """
            <State>
              <option name="ignoreUnusedKeyIssues" value="true" />
            </State>
            """.trimIndent()
        val legacy =
            com.intellij.util.xmlb.XmlSerializer.deserialize(
                com.intellij.openapi.util.JDOMUtil
                    .load(legacyXml),
                LanguageManagerSettings.SettingsState::class.java,
            )

        assertEquals(true, legacy.ignoreUnusedKeyIssues)
        assertEquals(true, LanguageManagerSettings().apply { loadState(legacy) }.quickInlineEditEnabled)
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

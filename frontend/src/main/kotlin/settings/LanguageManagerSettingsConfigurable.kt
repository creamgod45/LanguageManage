package cg.creamgod45.settings

import cg.creamgod45.LanguageManagerBundle.message
import cg.creamgod45.RegexPatternUi
import cg.creamgod45.RegexPresetUi
import cg.creamgod45.localization.AiProviderType
import cg.creamgod45.localization.DEFAULT_USAGE_EXCLUDED_DIRECTORIES
import cg.creamgod45.localization.DEFAULT_USAGE_REGEX_PATTERNS
import cg.creamgod45.localization.HARD_MAX_ENTRIES_PER_FILE
import cg.creamgod45.localization.HARD_MAX_ENTRIES_PER_SCHEME
import cg.creamgod45.localization.HARD_MAX_LANGUAGE_FILE_KB
import cg.creamgod45.localization.HARD_MAX_LANGUAGE_SCHEME_MB
import cg.creamgod45.toolWindow.LanguageManagerToolWindowFactory
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.DefaultListCellRenderer
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JSpinner
import javax.swing.SpinnerNumberModel

class LanguageManagerSettingsConfigurable(
    private val project: Project,
) : Configurable {
    private var languageBox: ComboBox<DisplayLanguage>? = null
    private var basePathModeBox: ComboBox<DefaultBasePathMode>? = null
    private var parentLevelsSpinner: JSpinner? = null
    private var regexModel: DefaultListModel<String>? = null
    private var exclusionModel: DefaultListModel<String>? = null
    private var maxLanguageFileKbSpinner: JSpinner? = null
    private var maxLanguageSchemeMbSpinner: JSpinner? = null
    private var maxEntriesPerFileSpinner: JSpinner? = null
    private var maxEntriesPerSchemeSpinner: JSpinner? = null
    private var ignoreDuplicateValueIssuesBox: JBCheckBox? = null
    private var ignoreUnusedKeyIssuesBox: JBCheckBox? = null
    private var aiProviderBox: ComboBox<AiProviderType>? = null
    private var aiEndpointField: JBTextField? = null
    private var aiModelField: JBTextField? = null
    private var aiTemperatureField: JBTextField? = null
    private var aiTokenField: JBPasswordField? = null

    override fun getDisplayName(): String = message("settings.display.name")

    override fun createComponent(): JComponent {
        languageBox =
            ComboBox(DisplayLanguage.entries.toTypedArray()).apply {
                renderer = localizedRenderer { value -> (value as? DisplayLanguage)?.let { message(it.messageKey()) } }
            }
        basePathModeBox =
            ComboBox(DefaultBasePathMode.entries.toTypedArray()).apply {
                renderer = localizedRenderer { value -> (value as? DefaultBasePathMode)?.let { message(it.messageKey()) } }
                addActionListener { updateParentLevelsEnabled() }
            }
        parentLevelsSpinner = JSpinner(SpinnerNumberModel(1, 1, LanguageManagerSettings.MAX_PARENT_LEVELS, 1))
        regexModel = DefaultListModel()
        exclusionModel = DefaultListModel()
        maxLanguageFileKbSpinner = JSpinner(SpinnerNumberModel(1, 1, HARD_MAX_LANGUAGE_FILE_KB, 128))
        maxLanguageSchemeMbSpinner = JSpinner(SpinnerNumberModel(1, 1, HARD_MAX_LANGUAGE_SCHEME_MB, 1))
        maxEntriesPerFileSpinner = JSpinner(SpinnerNumberModel(1, 1, HARD_MAX_ENTRIES_PER_FILE, 1_000))
        maxEntriesPerSchemeSpinner = JSpinner(SpinnerNumberModel(1, 1, HARD_MAX_ENTRIES_PER_SCHEME, 5_000))
        ignoreDuplicateValueIssuesBox = JBCheckBox(message("settings.issues.ignore.duplicate.values"))
        ignoreUnusedKeyIssuesBox = JBCheckBox(message("settings.issues.ignore.unused.keys"))
        aiProviderBox =
            ComboBox(AiProviderType.entries.toTypedArray()).apply {
                renderer = localizedRenderer { value -> (value as? AiProviderType)?.let { message(it.messageKey()) } }
                addActionListener { applyProviderDefaultEndpoint() }
            }
        aiEndpointField = JBTextField()
        aiModelField = JBTextField()
        aiTemperatureField = JBTextField()
        aiTokenField = JBPasswordField()

        val form =
            FormBuilder
                .createFormBuilder()
                .addLabeledComponent(message("settings.language.label"), languageBox!!)
                .addSeparator()
                .addComponent(javax.swing.JLabel(message("settings.issues.title")))
                .addComponent(ignoreDuplicateValueIssuesBox!!)
                .addComponent(ignoreUnusedKeyIssuesBox!!)
                .addSeparator()
                .addComponent(javax.swing.JLabel(message("settings.ai.title")))
                .addLabeledComponent(message("settings.ai.provider"), aiProviderBox!!)
                .addLabeledComponent(message("settings.ai.endpoint"), aiEndpointField!!)
                .addLabeledComponent(message("settings.ai.model"), aiModelField!!)
                .addLabeledComponent(message("settings.ai.temperature"), aiTemperatureField!!)
                .addTooltip(message("settings.ai.temperature.help"))
                .addLabeledComponent(message("settings.ai.token"), aiTokenField!!)
                .addTooltip(message("settings.ai.help"))
                .addSeparator()
                .addComponent(javax.swing.JLabel(message("settings.defaults.title")))
                .addComponent(javax.swing.JLabel(message("settings.load.limits.title")))
                .addLabeledComponent(message("settings.load.max.file.kb"), maxLanguageFileKbSpinner!!)
                .addLabeledComponent(message("settings.load.max.scheme.mb"), maxLanguageSchemeMbSpinner!!)
                .addLabeledComponent(message("settings.load.max.entries.file"), maxEntriesPerFileSpinner!!)
                .addLabeledComponent(message("settings.load.max.entries.scheme"), maxEntriesPerSchemeSpinner!!)
                .addTooltip(message("settings.load.limits.help"))
                .addLabeledComponent(message("settings.default.base.mode"), basePathModeBox!!)
                .addLabeledComponent(message("settings.default.parent.levels"), parentLevelsSpinner!!)
                .addTooltip(message("settings.default.parent.levels.help", LanguageManagerSettings.MAX_PARENT_LEVELS))
                .addLabeledComponent(
                    message("settings.default.regex"),
                    listEditor(
                        regexModel!!,
                        message("settings.regex.add.prompt"),
                        message("settings.regex.edit.prompt"),
                        DEFAULT_USAGE_REGEX_PATTERNS,
                        regexInput = true,
                    ),
                ).addComponent(RegexPatternUi.helpComponent())
                .addLabeledComponent(
                    message("settings.default.exclusions"),
                    listEditor(
                        exclusionModel!!,
                        message("settings.exclusion.add.prompt"),
                        message("settings.exclusion.edit.prompt"),
                        DEFAULT_USAGE_EXCLUDED_DIRECTORIES,
                    ),
                ).addTooltip(message("settings.usage.exclusions.help"))
                .addComponentFillVertically(JPanel(), 0)
                .panel

        reset()
        return JBScrollPane(form).apply { border = JBUI.Borders.empty() }
    }

    override fun isModified(): Boolean {
        val settings = LanguageManagerSettings.getInstance()
        return languageBox?.selectedItem != settings.displayLanguage ||
            basePathModeBox?.selectedItem != settings.defaultBasePathMode ||
            (parentLevelsSpinner?.value as? Int) != settings.defaultParentLevels ||
            regexModel.values() != settings.defaultRegexPatterns ||
            exclusionModel.values() != settings.defaultExcludedDirectories ||
            (maxLanguageFileKbSpinner?.value as? Int) != settings.defaultMaxLanguageFileKb ||
            (maxLanguageSchemeMbSpinner?.value as? Int) != settings.defaultMaxLanguageSchemeMb ||
            (maxEntriesPerFileSpinner?.value as? Int) != settings.defaultMaxEntriesPerFile ||
            (maxEntriesPerSchemeSpinner?.value as? Int) != settings.defaultMaxEntriesPerScheme ||
            ignoreDuplicateValueIssuesBox?.isSelected != settings.ignoreDuplicateValueIssues ||
            ignoreUnusedKeyIssuesBox?.isSelected != settings.ignoreUnusedKeyIssues ||
            aiProviderBox?.selectedItem != settings.aiProvider ||
            aiEndpointField?.text?.trim() != settings.aiEndpoint ||
            aiModelField?.text?.trim() != settings.aiModel ||
            aiTemperatureField?.text?.trim() != settings.aiTemperature ||
            aiTokenField?.password?.concatToString().orEmpty() != AiProviderCredentialStore.getToken()
    }

    override fun apply() {
        val regexPatterns = regexModel.values()
        val exclusions = exclusionModel.values()
        validateDefaults(
            regexPatterns,
            exclusions,
            maxEntriesPerFileSpinner?.value as? Int ?: 1,
            maxEntriesPerSchemeSpinner?.value as? Int ?: 1,
        )
        validateAiTemperature()

        val settings = LanguageManagerSettings.getInstance()
        val selectedLanguage = languageBox?.selectedItem as? DisplayLanguage ?: DisplayLanguage.AUTO
        val languageChanged = selectedLanguage != settings.displayLanguage
        val issueVisibilityChanged =
            ignoreDuplicateValueIssuesBox?.isSelected != settings.ignoreDuplicateValueIssues ||
                ignoreUnusedKeyIssuesBox?.isSelected != settings.ignoreUnusedKeyIssues
        settings.displayLanguage = selectedLanguage
        settings.defaultBasePathMode = basePathModeBox?.selectedItem as? DefaultBasePathMode
            ?: DefaultBasePathMode.PROJECT_DIRECTORY
        settings.defaultParentLevels = parentLevelsSpinner?.value as? Int ?: 1
        settings.defaultRegexPatterns = regexPatterns
        settings.defaultExcludedDirectories = exclusions
        settings.defaultMaxLanguageFileKb = maxLanguageFileKbSpinner?.value as? Int ?: settings.defaultMaxLanguageFileKb
        settings.defaultMaxLanguageSchemeMb = maxLanguageSchemeMbSpinner?.value as? Int ?: settings.defaultMaxLanguageSchemeMb
        settings.defaultMaxEntriesPerFile = maxEntriesPerFileSpinner?.value as? Int ?: settings.defaultMaxEntriesPerFile
        settings.defaultMaxEntriesPerScheme = maxEntriesPerSchemeSpinner?.value as? Int ?: settings.defaultMaxEntriesPerScheme
        settings.ignoreDuplicateValueIssues = ignoreDuplicateValueIssuesBox?.isSelected ?: false
        settings.ignoreUnusedKeyIssues = ignoreUnusedKeyIssuesBox?.isSelected ?: false
        settings.aiProvider = aiProviderBox?.selectedItem as? AiProviderType ?: AiProviderType.OPENAI_COMPATIBLE
        settings.aiEndpoint = aiEndpointField?.text.orEmpty()
        settings.aiModel = aiModelField?.text.orEmpty()
        settings.aiTemperature = aiTemperatureField?.text.orEmpty()
        AiProviderCredentialStore.setToken(aiTokenField?.password?.concatToString().orEmpty())
        if (languageChanged || issueVisibilityChanged) LanguageManagerToolWindowFactory.refreshOpenToolWindows()
    }

    override fun reset() {
        val settings = LanguageManagerSettings.getInstance()
        languageBox?.selectedItem = settings.displayLanguage
        basePathModeBox?.selectedItem = settings.defaultBasePathMode
        parentLevelsSpinner?.value = settings.defaultParentLevels
        regexModel?.replaceWith(settings.defaultRegexPatterns)
        exclusionModel?.replaceWith(settings.defaultExcludedDirectories)
        maxLanguageFileKbSpinner?.value = settings.defaultMaxLanguageFileKb
        maxLanguageSchemeMbSpinner?.value = settings.defaultMaxLanguageSchemeMb
        maxEntriesPerFileSpinner?.value = settings.defaultMaxEntriesPerFile
        maxEntriesPerSchemeSpinner?.value = settings.defaultMaxEntriesPerScheme
        ignoreDuplicateValueIssuesBox?.isSelected = settings.ignoreDuplicateValueIssues
        ignoreUnusedKeyIssuesBox?.isSelected = settings.ignoreUnusedKeyIssues
        aiProviderBox?.selectedItem = settings.aiProvider
        aiEndpointField?.text = settings.aiEndpoint
        aiModelField?.text = settings.aiModel
        aiTemperatureField?.text = settings.aiTemperature
        aiTokenField?.text = AiProviderCredentialStore.getToken()
        updateParentLevelsEnabled()
    }

    override fun disposeUIResources() {
        languageBox = null
        basePathModeBox = null
        parentLevelsSpinner = null
        regexModel = null
        exclusionModel = null
        maxLanguageFileKbSpinner = null
        maxLanguageSchemeMbSpinner = null
        maxEntriesPerFileSpinner = null
        maxEntriesPerSchemeSpinner = null
        ignoreDuplicateValueIssuesBox = null
        ignoreUnusedKeyIssuesBox = null
        aiProviderBox = null
        aiEndpointField = null
        aiModelField = null
        aiTemperatureField = null
        aiTokenField = null
    }

    private fun updateParentLevelsEnabled() {
        parentLevelsSpinner?.isEnabled = basePathModeBox?.selectedItem == DefaultBasePathMode.PARENT_LEVELS
    }

    private fun applyProviderDefaultEndpoint() {
        val field = aiEndpointField ?: return
        val oldDefaults = setOf("https://api.openai.com/v1/chat/completions", "https://api.anthropic.com/v1/messages", "")
        if (field.text.trim() !in oldDefaults) return
        field.text =
            when (aiProviderBox?.selectedItem as? AiProviderType) {
                AiProviderType.ANTHROPIC -> "https://api.anthropic.com/v1/messages"
                else -> "https://api.openai.com/v1/chat/completions"
            }
    }

    private fun validateAiTemperature() {
        val text = aiTemperatureField?.text?.trim().orEmpty()
        if (text.isEmpty()) return
        val value = text.toDoubleOrNull()
        val maximum = if (aiProviderBox?.selectedItem == AiProviderType.ANTHROPIC) 1.0 else 2.0
        if (value == null || !value.isFinite() || value !in 0.0..maximum) {
            throw ConfigurationException(message("settings.ai.temperature.invalid", maximum))
        }
    }

    private fun validateDefaults(
        regexPatterns: List<String>,
        exclusions: List<String>,
        maxEntriesPerFile: Int,
        maxEntriesPerScheme: Int,
    ) {
        if (regexPatterns.isEmpty() || regexPatterns.size > 20) {
            throw ConfigurationException(message("settings.default.regex.count"))
        }
        regexPatterns.forEach { pattern ->
            if (pattern.length > 512 || pattern.any(Char::isISOControl) || runCatching { Regex(pattern) }.isFailure) {
                throw ConfigurationException(message("settings.default.regex.invalid", pattern.take(120)))
            }
        }
        if (exclusions.size > 100 || exclusions.any(::unsafeExclusion)) {
            throw ConfigurationException(message("settings.default.exclusion.invalid"))
        }
        if (maxEntriesPerFile > maxEntriesPerScheme) {
            throw ConfigurationException(message("settings.load.entries.order"))
        }
    }

    private fun unsafeExclusion(value: String): Boolean {
        val normalized = value.trim().replace('\\', '/').trim('/')
        return normalized.isEmpty() || normalized.length > 200 || normalized.any(Char::isISOControl) ||
            "://" in normalized || ':' in normalized ||
            normalized.split('/').any { it.isBlank() || it == "." || it == ".." }
    }

    private fun listEditor(
        model: DefaultListModel<String>,
        addPrompt: String,
        editPrompt: String,
        defaultValues: List<String>,
        regexInput: Boolean = false,
    ): JComponent {
        val list = JBList(model).apply { visibleRowCount = 5 }
        return JPanel(BorderLayout(0, JBUI.scale(4))).apply {
            add(JBScrollPane(list).apply { preferredSize = Dimension(JBUI.scale(620), JBUI.scale(110)) }, BorderLayout.CENTER)
            add(
                JPanel(FlowLayout(FlowLayout.LEADING, JBUI.scale(4), 0)).apply {
                    add(
                        JButton(message("settings.list.add")).apply {
                            addActionListener {
                                requestListValue(addPrompt, null, regexInput)
                                    ?.trim()
                                    ?.takeIf(String::isNotEmpty)
                                    ?.let { if (it !in model.values()) model.addElement(it) }
                            }
                        },
                    )
                    add(
                        JButton(message("settings.list.edit")).apply {
                            addActionListener {
                                val index = list.selectedIndex
                                if (index < 0) return@addActionListener
                                requestListValue(editPrompt, model[index], regexInput)
                                    ?.trim()
                                    ?.takeIf(String::isNotEmpty)
                                    ?.let { model[index] = it }
                            }
                        },
                    )
                    add(
                        JButton(message("settings.list.remove")).apply {
                            addActionListener { list.selectedIndices.sortedDescending().forEach(model::remove) }
                        },
                    )
                    add(
                        JButton(message("settings.list.restore.defaults")).apply {
                            addActionListener { model.replaceWith(defaultValues) }
                        },
                    )
                    if (regexInput) {
                        add(
                            RegexPresetUi.button { patterns ->
                                patterns.filterNot { it in model.values() }.forEach(model::addElement)
                            },
                        )
                    }
                },
                BorderLayout.SOUTH,
            )
        }
    }

    private fun requestListValue(
        prompt: String,
        initialValue: String?,
        regexInput: Boolean,
    ): String? =
        if (regexInput) {
            RegexPatternUi.showInputDialog(project, message("settings.display.name"), prompt, initialValue)
        } else {
            Messages.showInputDialog(project, prompt, message("settings.display.name"), null, initialValue, null)
        }
}

private fun localizedRenderer(label: (Any?) -> String?): DefaultListCellRenderer =
    object : DefaultListCellRenderer() {
        override fun getListCellRendererComponent(
            list: JList<*>?,
            value: Any?,
            index: Int,
            selected: Boolean,
            focus: Boolean,
        ) = super.getListCellRendererComponent(list, label(value) ?: value, index, selected, focus)
    }

private fun DefaultListModel<String>?.values(): List<String> =
    this?.let { model -> (0 until model.size()).map(model::getElementAt) }.orEmpty()

private fun DefaultListModel<String>.replaceWith(values: List<String>) {
    clear()
    values.forEach(::addElement)
}

private fun DisplayLanguage.messageKey(): String =
    when (this) {
        DisplayLanguage.AUTO -> "settings.language.auto"
        DisplayLanguage.ENGLISH -> "settings.language.english"
        DisplayLanguage.TRADITIONAL_CHINESE -> "settings.language.traditional.chinese"
        DisplayLanguage.SIMPLIFIED_CHINESE -> "settings.language.simplified.chinese"
        DisplayLanguage.JAPANESE -> "settings.language.japanese"
        DisplayLanguage.KOREAN -> "settings.language.korean"
    }

private fun DefaultBasePathMode.messageKey(): String =
    when (this) {
        DefaultBasePathMode.PROJECT_DIRECTORY -> "settings.default.base.project"
        DefaultBasePathMode.PARENT_LEVELS -> "settings.default.base.parents"
    }

private fun AiProviderType.messageKey(): String =
    when (this) {
        AiProviderType.OPENAI_COMPATIBLE -> "settings.ai.provider.openai"
        AiProviderType.ANTHROPIC -> "settings.ai.provider.anthropic"
    }

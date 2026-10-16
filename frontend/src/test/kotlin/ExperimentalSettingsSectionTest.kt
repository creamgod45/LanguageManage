package cg.creamgod45.settings

import cg.creamgod45.LanguageManagerBundle.message
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.components.JBCheckBox
import java.awt.Container
import javax.swing.JComponent
import javax.swing.JLabel

/**
 * The experimental editor key hover must be visible on the main LanguageManager settings page itself (issue #38):
 * a separate child page under the settings tree was easy to miss.
 */
class ExperimentalSettingsSectionTest : BasePlatformTestCase() {
    fun testMainSettingsPageShowsTheExperimentalSectionAndTracksChanges() {
        val settings = LanguageManagerSettings.getInstance()
        val previousEnabled = settings.editorKeyHoverEnabled
        val previousDelay = settings.editorKeyHoverDelayMs
        val configurable = LanguageManagerSettingsConfigurable(project)
        try {
            settings.editorKeyHoverEnabled = false
            settings.editorKeyHoverDelayMs = DEFAULT_EDITOR_KEY_HOVER_DELAY_MS
            val component = configurable.createComponent()

            val labels = descendants(component).filterIsInstance<JLabel>().map { it.text }
            assertTrue(message("settings.experimental.title") in labels)
            val checkBox = configurable.editorKeyHoverBoxForTest()!!
            assertTrue(checkBox in descendants(component).filterIsInstance<JBCheckBox>())
            assertEquals(message("settings.experimental.editor.hover"), checkBox.text)
            assertFalse(checkBox.isSelected)
            val spinner = configurable.editorKeyHoverDelaySpinnerForTest()!!
            assertEquals(0.7, spinner.value)
            assertFalse("Delay is disabled while the feature is off", spinner.isEnabled)
            assertFalse(configurable.isModified)

            checkBox.doClick()
            assertTrue(spinner.isEnabled)
            assertTrue(configurable.isModified)

            checkBox.doClick()
            spinner.value = 1.5
            assertTrue(configurable.isModified)

            settings.editorKeyHoverEnabled = true
            settings.editorKeyHoverDelayMs = 1_200
            configurable.reset()
            assertTrue(checkBox.isSelected)
            assertEquals(1.2, spinner.value)
            assertFalse(configurable.isModified)
        } finally {
            configurable.disposeUIResources()
            settings.editorKeyHoverEnabled = previousEnabled
            settings.editorKeyHoverDelayMs = previousDelay
        }
    }

    private fun descendants(component: JComponent): List<java.awt.Component> =
        buildList {
            fun walk(container: Container) {
                container.components.forEach { child ->
                    add(child)
                    if (child is Container) walk(child)
                }
            }
            walk(component)
        }
}

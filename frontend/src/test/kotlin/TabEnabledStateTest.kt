package cg.creamgod45.localization.ui

import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTabbedPane
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TabEnabledStateTest {
    @Test
    fun `disabling a tab also disables its custom tab component`() {
        val label = JLabel("Usage Locations")
        val tabs =
            JTabbedPane().apply {
                addTab("Translations", JPanel())
                addTab("Usage Locations", JPanel())
                setTabComponentAt(1, label)
            }

        tabs.setTabEnabled(1, false)
        assertFalse(tabs.isEnabledAt(1))
        assertFalse(label.isEnabled)

        tabs.setTabEnabled(1, true)
        assertTrue(tabs.isEnabledAt(1))
        assertTrue(label.isEnabled)
    }

    @Test
    fun `tabs without a custom tab component still toggle`() {
        val tabs = JTabbedPane().apply { addTab("Translations", JPanel()) }

        tabs.setTabEnabled(0, false)
        assertFalse(tabs.isEnabledAt(0))
    }
}

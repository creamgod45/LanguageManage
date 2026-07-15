package cg.creamgod45.toolWindow

import cg.creamgod45.settings.LanguageManagerSettingsConfigurable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ToolWindowFactoryCompatibilityTest {
    @Test
    fun `factory does not emit compatibility bridges to unstable platform methods`() {
        val unstablePlatformMethods =
            setOf(
                "isApplicable",
                "isDoNotActivateOnStart",
                "getAnchor",
                "getIcon",
                "manage",
            )

        val emittedBridges =
            LanguageManagerToolWindowFactory::class.java.declaredMethods
                .mapTo(mutableSetOf()) { it.name }
                .intersect(unstablePlatformMethods)

        assertTrue(
            emittedBridges.isEmpty(),
            "ToolWindowFactory compatibility bridges must not be emitted: $emittedBridges",
        )
    }

    @Test
    fun `gear action targets registered LanguageManager settings page`() {
        assertEquals(
            "cg.creamgod45.localization.LanguageManagerSettings",
            LanguageManagerToolWindowFactory.PLUGIN_SETTINGS_ID,
        )
        assertEquals(
            LanguageManagerSettingsConfigurable::class.java,
            LanguageManagerToolWindowFactory.PLUGIN_SETTINGS_CLASS,
        )
    }
}

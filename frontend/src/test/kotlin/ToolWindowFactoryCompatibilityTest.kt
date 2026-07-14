package cg.creamgod45.toolWindow

import kotlin.test.Test
import kotlin.test.assertTrue

class ToolWindowFactoryCompatibilityTest {
    @Test
    fun `factory does not emit compatibility bridges to unstable platform methods`() {
        val unstablePlatformMethods = setOf(
            "isApplicable",
            "isDoNotActivateOnStart",
            "getAnchor",
            "getIcon",
            "manage",
        )

        val emittedBridges = LanguageManagerToolWindowFactory::class.java.declaredMethods
            .mapTo(mutableSetOf()) { it.name }
            .intersect(unstablePlatformMethods)

        assertTrue(
            emittedBridges.isEmpty(),
            "ToolWindowFactory compatibility bridges must not be emitted: $emittedBridges",
        )
    }
}

package cg.creamgod45.localization.ui

import cg.creamgod45.localization.EditorKeyMatchDto
import cg.creamgod45.localization.EditorKeyTargetDto
import cg.creamgod45.localization.LanguageEntryDto
import cg.creamgod45.settings.DEFAULT_EDITOR_KEY_HOVER_DELAY_MS
import cg.creamgod45.settings.LanguageManagerSettings
import cg.creamgod45.settings.MAX_EDITOR_KEY_HOVER_DELAY_MS
import cg.creamgod45.settings.MIN_EDITOR_KEY_HOVER_DELAY_MS
import cg.creamgod45.settings.editorKeyHoverDelayMsFromSeconds
import cg.creamgod45.settings.editorKeyHoverDelaySeconds
import java.awt.Dimension
import java.awt.Rectangle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EditorKeyHoverSupportTest {
    private fun entry(
        locale: String,
        namespace: String,
        key: String,
        value: String,
    ) = LanguageEntryDto("$locale:$namespace:$key", "scheme", "/lang/$locale.json", locale, namespace, key, value)

    @Test
    fun `only writable main editors with an active scheme are eligible`() {
        val eligible = { main: Boolean, viewer: Boolean, document: Boolean, file: Boolean, local: Boolean, scheme: Boolean ->
            EditorKeyHoverSupport.isEligibleEditor(main, viewer, document, file, local, scheme)
        }

        assertTrue(eligible(true, false, true, true, true, true))
        assertFalse(eligible(false, false, true, true, true, true), "diff, console or preview editor")
        assertFalse(eligible(true, true, true, true, true, true), "viewer")
        assertFalse(eligible(true, false, false, true, true, true), "read-only document (lock icon)")
        assertFalse(eligible(true, false, true, false, true, true), "read-only file (lock icon)")
        assertFalse(eligible(true, false, true, true, false, true), "in-memory file")
        assertFalse(eligible(true, false, true, true, true, false), "no active scheme")
    }

    @Test
    fun `match lookup is end exclusive and prefers the innermost range`() {
        val outer = EditorKeyMatchDto(0, 20, "outer")
        val inner = EditorKeyMatchDto(5, 10, "inner")
        val matches = listOf(outer, inner)

        assertEquals(outer, EditorKeyHoverSupport.matchAt(matches, 0))
        assertEquals(inner, EditorKeyHoverSupport.matchAt(matches, 5))
        assertEquals(inner, EditorKeyHoverSupport.matchAt(matches, 9))
        assertEquals(outer, EditorKeyHoverSupport.matchAt(matches, 10))
        assertNull(EditorKeyHoverSupport.matchAt(matches, 20))
    }

    @Test
    fun `rows come from the latest entries and list every locale with missing ones marked`() {
        val entries =
            listOf(
                entry("en", "messages", "welcome", "Welcome"),
                entry("zh_TW", "messages", "welcome", "歡迎"),
                entry("ja", "messages", "other", "その他"),
            )
        val match = EditorKeyMatchDto(0, 16, "messages.welcome", listOf(EditorKeyTargetDto("messages", "welcome")))

        val row = EditorKeyHoverSupport.rowsFor(match, entries).single()
        val translations = EditorKeyHoverSupport.translations(row, EditorKeyHoverSupport.locales(entries))

        assertEquals(listOf("en" to "Welcome", "ja" to null, "zh_TW" to "歡迎"), translations)
        assertTrue(EditorKeyHoverSupport.rowsFor(match.copy(targets = emptyList()), entries).isEmpty())
    }

    @Test
    fun `create draft prefills the suggested namespace and key`() {
        val match = EditorKeyMatchDto(0, 10, "auth.failed", suggestedNamespace = "auth", suggestedKey = "failed")

        val draft = EditorKeyHoverSupport.createDraft(match)

        assertEquals("auth", draft.namespace)
        assertEquals("failed", draft.key)
        assertEquals("Plain", EditorKeyHoverSupport.createDraft(EditorKeyMatchDto(0, 5, "Plain")).key)
    }

    @Test
    fun `long translations are shortened for display only`() {
        assertEquals("short", EditorKeyHoverSupport.displayValue("short"))
        assertEquals("abc…", EditorKeyHoverSupport.displayValue("abcdef", maxChars = 3))
    }

    @Test
    fun `popup size stays inside fixed and screen relative limits`() {
        val desktop = Rectangle(0, 0, 2560, 1440)

        assertEquals(Dimension(560, 420), EditorKeyHoverSupport.popupSize(Dimension(5_000, 9_000), desktop))
        assertEquals(Dimension(280, 120), EditorKeyHoverSupport.popupSize(Dimension(100, 120), desktop))

        val small = Rectangle(0, 0, 800, 600)
        assertEquals(Dimension(480, 300), EditorKeyHoverSupport.popupSize(Dimension(5_000, 9_000), small))

        val tiny = Rectangle(0, 0, 300, 200)
        val tinySize = EditorKeyHoverSupport.popupSize(Dimension(5_000, 9_000), tiny)
        assertEquals(Dimension(180, 100), tinySize)

        val hiDpi = EditorKeyHoverSupport.popupSize(Dimension(5_000, 9_000), desktop) { it * 2 }
        assertEquals(Dimension(1120, 720), hiDpi)
    }

    @Test
    fun `hover delay defaults to 0_7 seconds and is clamped`() {
        assertEquals(700, DEFAULT_EDITOR_KEY_HOVER_DELAY_MS)
        assertEquals(700, editorKeyHoverDelayMsFromSeconds(0.7))
        assertEquals(1_250, editorKeyHoverDelayMsFromSeconds(1.25))
        assertEquals(MIN_EDITOR_KEY_HOVER_DELAY_MS, editorKeyHoverDelayMsFromSeconds(0.01))
        assertEquals(MAX_EDITOR_KEY_HOVER_DELAY_MS, editorKeyHoverDelayMsFromSeconds(60.0))
        assertEquals(DEFAULT_EDITOR_KEY_HOVER_DELAY_MS, editorKeyHoverDelayMsFromSeconds(Double.NaN))
        assertEquals(0.7, editorKeyHoverDelaySeconds(700))
    }

    @Test
    fun `experimental hover is off by default and its settings persist`() {
        val defaults = LanguageManagerSettings.SettingsState()
        assertFalse(defaults.editorKeyHoverEnabled)
        assertEquals(DEFAULT_EDITOR_KEY_HOVER_DELAY_MS, defaults.editorKeyHoverDelayMs)

        val settings = LanguageManagerSettings()
        settings.editorKeyHoverEnabled = true
        settings.editorKeyHoverDelayMs = 99_999
        val restored = LanguageManagerSettings().apply { loadState(settings.state) }

        assertTrue(restored.editorKeyHoverEnabled)
        assertEquals(MAX_EDITOR_KEY_HOVER_DELAY_MS, restored.editorKeyHoverDelayMs)

        val corrupted =
            LanguageManagerSettings().apply {
                loadState(
                    LanguageManagerSettings.SettingsState().apply {
                        editorKeyHoverDelayMs =
                            -5
                    },
                )
            }
        assertEquals(MIN_EDITOR_KEY_HOVER_DELAY_MS, corrupted.editorKeyHoverDelayMs)
    }
}

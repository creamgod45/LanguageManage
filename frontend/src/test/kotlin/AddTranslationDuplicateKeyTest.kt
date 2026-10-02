package cg.creamgod45.localization.ui

import cg.creamgod45.localization.LanguageEntryDto
import cg.creamgod45.localization.LanguageSchemeDto
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import java.nio.file.Path

/**
 * Regression test for issue #18 ("Entries are discarded after 'Key already exists' message").
 *
 * Reported steps (Java ResourceBundle Properties):
 * 1. Actions -> Add Translation
 * 2. Enter a key that already exists and complete the translations
 * 3. Click OK -> "Key already exists"
 * 4. Confirm the message -> everything typed was discarded
 *
 * Expected: the typed values stay so the key can be corrected to one that does not exist yet.
 */
class AddTranslationDuplicateKeyTest : BasePlatformTestCase() {
    private val root = Path.of("issue18", "i18n").toAbsolutePath()
    private val englishFile = root.resolve("Messages.properties").toString()
    private val germanFile = root.resolve("Messages_de.properties").toString()
    private val scheme = LanguageSchemeDto("scheme", "Properties", listOf(englishFile, germanFile), 1)
    private val existingEntries =
        listOf(
            LanguageEntryDto("en.save", "scheme", englishFile, "en", "Messages", "button.save", "Save"),
            LanguageEntryDto("de.save", "scheme", germanFile, "de", "Messages", "button.save", "Speichern"),
        )

    fun testDuplicateKeyKeepsTheDialogOpenWithAllTypedValuesAndAcceptsACorrectedKey() {
        // Step 1: Actions -> Add Translation.
        val dialog = MultiLanguageEntryDialog(project, scheme, existingEntries, null)
        try {
            val form = dialog.form()

            // Step 2: an existing key plus completed translations.
            form.key.text = "button.save"
            form.valueFor(germanFile).text = "Änderungen speichern"
            form.valueFor(englishFile).text = "Save changes"

            // Step 3: click OK.
            dialog.clickOk()

            // Previously the dialog closed here and the backend "Key already exists" error discarded the input.
            assertFalse("Dialog must stay open for a duplicate key", dialog.isOK)
            assertFalse("Dialog must not be disposed for a duplicate key", dialog.isDisposed)
            assertEquals("button.save", form.key.text)
            assertEquals("Änderungen speichern", form.valueFor(germanFile).text)
            assertEquals("Save changes", form.valueFor(englishFile).text)

            // Expected result: correct the key to one that does not exist yet and save the kept values.
            form.key.text = "button.save.changes"
            dialog.clickOk()

            assertTrue("Dialog must close after the key is corrected", dialog.isOK)
            assertEquals(
                mapOf(englishFile to "Save changes", germanFile to "Änderungen speichern"),
                dialog.mutations().associate { it.filePath to it.value },
            )
            assertEquals(setOf("button.save.changes"), dialog.mutations().map { it.key }.toSet())
            assertTrue("A corrected key creates new entries instead of updating existing ids", dialog.mutations().all { it.id == null })
        } finally {
            dialog.disposeIfNeeded()
        }
    }

    fun testKeyWithSurroundingWhitespaceIsKeptAsTypedAndWarned() {
        // Surrounding spaces may be intentional, so " button.save " is a different key and is saved as typed.
        val dialog = MultiLanguageEntryDialog(project, scheme, existingEntries, null)
        try {
            val form = dialog.form()
            val hint = dialog.keyWhitespaceHintForTest()
            form.key.text = "button.save.changes"
            assertFalse("No warning for a key without surrounding spaces", hint.isVisible)

            form.key.text = "  button.save  "
            assertTrue("Warning appears while typing surrounding spaces", hint.isVisible)
            assertFalse(hint.text.startsWith("!"))
            form.valueFor(englishFile).text = "Save changes"

            dialog.clickOk()

            assertTrue("A key that differs by surrounding spaces is not a duplicate", dialog.isOK)
            assertEquals(setOf("  button.save  "), dialog.mutations().map { it.key }.toSet())
        } finally {
            dialog.disposeIfNeeded()
        }
    }

    fun testFullWidthSpaceAroundKeyIsWarnedAndBlankKeyIsRejected() {
        val dialog = MultiLanguageEntryDialog(project, scheme, existingEntries, null)
        try {
            val form = dialog.form()
            form.key.text = "　button.save"
            assertTrue("IME full-width space is warned", dialog.keyWhitespaceHintForTest().isVisible)

            form.key.text = "   "
            assertFalse("Blank key is reported by validation, not by the whitespace hint", dialog.keyWhitespaceHintForTest().isVisible)
            dialog.clickOk()
            assertFalse("Blank key must not be saved", dialog.isOK)
        } finally {
            dialog.disposeIfNeeded()
        }
    }

    fun testReopenedDialogAfterBackendRejectionRestoresTypedInput() {
        // If the backend still rejects the save (e.g. the key was added concurrently), the panel reopens the dialog from this draft.
        val first = MultiLanguageEntryDialog(project, scheme, emptyList(), null)
        val draft =
            try {
                val form = first.form()
                form.key.text = "button.save"
                form.valueFor(germanFile).text = "Änderungen speichern"
                form.valueFor(englishFile).text = "Save changes"
                first.draft()
            } finally {
                first.disposeIfNeeded()
            }

        val reopened = MultiLanguageEntryDialog(project, scheme, existingEntries, null, draft)
        try {
            val form = reopened.form()
            assertEquals("button.save", form.key.text)
            assertEquals("Änderungen speichern", form.valueFor(germanFile).text)
            assertEquals("Save changes", form.valueFor(englishFile).text)
        } finally {
            reopened.disposeIfNeeded()
        }
    }

    private class EntryForm(
        val key: JBTextField,
        private val valuesByFile: Map<String, JBTextArea>,
    ) {
        fun valueFor(filePath: String): JBTextArea = valuesByFile[filePath] ?: error("No editor for $filePath in ${valuesByFile.keys}")
    }

    /** The headless test peer has no window, so the fields a user types into are taken from the dialog directly. */
    private fun MultiLanguageEntryDialog.form(): EntryForm {
        val editors = valueEditorsForTest()
        assertEquals(setOf(englishFile, germanFile), editors.keys)
        return EntryForm(keyFieldForTest(), editors)
    }

    private fun DialogWrapper.clickOk() {
        val okAction =
            DialogWrapper::class.java
                .getDeclaredMethod(
                    "getOKAction",
                ).apply { isAccessible = true }
                .invoke(this) as javax.swing.Action
        okAction.actionPerformed(java.awt.event.ActionEvent(this, java.awt.event.ActionEvent.ACTION_PERFORMED, "OK"))
    }

    private fun DialogWrapper.disposeIfNeeded() {
        if (!isDisposed) Disposer.dispose(disposable)
    }
}

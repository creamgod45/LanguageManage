package cg.creamgod45

import cg.creamgod45.LanguageManagerBackendBundle.message as backendMessage

/** Validation shared by key mutation paths without treating translation keys as identifiers. */
internal object TranslationInputValidation {
    private const val MAX_KEY_LENGTH = 256

    fun key(raw: String): String {
        require(raw.length <= MAX_KEY_LENGTH) { backendMessage("input.too.long") }
        require(raw.none(::isUnsafeControlCharacter)) { backendMessage("input.control") }
        return raw.trim().also { require(it.isNotEmpty()) { backendMessage("key.invalid") } }
    }

    private fun isUnsafeControlCharacter(char: Char): Boolean = char == '\u0000' || char.code < 32 || char.code in 127..159
}

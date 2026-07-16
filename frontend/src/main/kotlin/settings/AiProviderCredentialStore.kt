package cg.creamgod45.settings

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.ide.passwordSafe.PasswordSafe

internal object AiProviderCredentialStore {
    private val attributes = CredentialAttributes("LanguageManager AI Provider API Token")

    fun getToken(): String = PasswordSafe.instance.getPassword(attributes).orEmpty()

    fun setToken(token: String) {
        PasswordSafe.instance.setPassword(attributes, token.ifBlank { null })
    }
}

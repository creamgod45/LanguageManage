package cg.creamgod45.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.RoamingType
import java.util.Locale

internal enum class DisplayLanguage(val locale: Locale?) {
    AUTO(null),
    ENGLISH(Locale.ENGLISH),
    TRADITIONAL_CHINESE(Locale.TAIWAN),
    SIMPLIFIED_CHINESE(Locale.SIMPLIFIED_CHINESE),
    JAPANESE(Locale.JAPANESE),
    KOREAN(Locale.KOREAN),
}

@Service(Service.Level.APP)
@State(
    name = "cg.creamgod45.localization.LanguageManagerSettings",
    storages = [Storage(value = "LanguageManager.xml", roamingType = RoamingType.DISABLED)],
)
internal class LanguageManagerSettings : PersistentStateComponent<LanguageManagerSettings.SettingsState> {
    class SettingsState {
        var displayLanguage: String = DisplayLanguage.AUTO.name
    }

    private var settingsState = SettingsState()

    var displayLanguage: DisplayLanguage
        get() = runCatching { DisplayLanguage.valueOf(settingsState.displayLanguage) }.getOrDefault(DisplayLanguage.AUTO)
        set(value) {
            settingsState.displayLanguage = value.name
        }

    override fun getState(): SettingsState = settingsState

    override fun loadState(state: SettingsState) {
        settingsState = state
    }

    companion object {
        fun currentLanguage(): DisplayLanguage =
            ApplicationManager.getApplication()?.getService(LanguageManagerSettings::class.java)?.displayLanguage
                ?: DisplayLanguage.AUTO

        fun getInstance(): LanguageManagerSettings =
            ApplicationManager.getApplication().getService(LanguageManagerSettings::class.java)
    }
}

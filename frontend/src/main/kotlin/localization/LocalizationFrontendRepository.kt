package cg.creamgod45.localization.ui

import cg.creamgod45.localization.*
import com.intellij.openapi.project.Project
import com.intellij.platform.project.projectId
import fleet.rpc.client.durable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

internal class LocalizationFrontendRepository(
    private val project: Project,
) {
    val state: Flow<LocalizationStateDto> =
        flow {
            durable { LocalizationManagerRpcApi.getInstance().state(project.projectId()).collect { emit(it) } }
        }

    suspend fun createScheme(
        name: String,
        files: List<String>,
        usageSettings: UsageScanSettingsDto,
    ) = LocalizationManagerRpcApi.getInstance().createScheme(project.projectId(), name, files, usageSettings)

    suspend fun deleteScheme(id: String) = LocalizationManagerRpcApi.getInstance().deleteScheme(project.projectId(), id)

    suspend fun activateScheme(id: String) = LocalizationManagerRpcApi.getInstance().activateScheme(project.projectId(), id)

    suspend fun updateSchemeUsageSettings(
        id: String,
        settings: UsageScanSettingsDto,
    ) = LocalizationManagerRpcApi.getInstance().updateSchemeUsageSettings(project.projectId(), id, settings)

    suspend fun reload(id: String) = LocalizationManagerRpcApi.getInstance().reload(project.projectId(), id, true)

    suspend fun discoverLanguageFiles(folderPaths: List<String>) =
        LocalizationManagerRpcApi.getInstance().discoverLanguageFiles(project.projectId(), folderPaths)

    suspend fun exportSchemeSettings() = LocalizationManagerRpcApi.getInstance().exportSchemeSettings(project.projectId())

    suspend fun previewSchemeSettingsImport(content: String) =
        LocalizationManagerRpcApi.getInstance().previewSchemeSettingsImport(project.projectId(), content)

    suspend fun importSchemeSettings(content: String) =
        LocalizationManagerRpcApi.getInstance().importSchemeSettings(project.projectId(), content)

    suspend fun save(
        id: String,
        mutation: EntryMutationDto,
    ) = LocalizationManagerRpcApi.getInstance().saveEntry(project.projectId(), id, mutation)

    suspend fun delete(
        id: String,
        entryIds: List<String>,
    ) = LocalizationManagerRpcApi.getInstance().deleteEntries(project.projectId(), id, entryIds)

    suspend fun rename(
        id: String,
        oldKey: String,
        newKey: String,
    ) = LocalizationManagerRpcApi.getInstance().renameKey(project.projectId(), id, oldKey, newKey)

    suspend fun repair(id: String) = LocalizationManagerRpcApi.getInstance().repair(project.projectId(), id)

    suspend fun repairEntries(
        id: String,
        entryIds: List<String>,
    ) = LocalizationManagerRpcApi.getInstance().repairEntries(project.projectId(), id, entryIds)

    suspend fun previewLocaleVersion(
        id: String,
        request: LocaleVersionRequestDto,
    ) = LocalizationManagerRpcApi.getInstance().previewLocaleVersion(project.projectId(), id, request)

    suspend fun createLocaleVersion(
        id: String,
        request: LocaleVersionRequestDto,
        expectedTargetHashes: Map<String, String>,
    ) = LocalizationManagerRpcApi.getInstance().createLocaleVersion(project.projectId(), id, request, expectedTargetHashes)

    suspend fun previewChanges(
        id: String,
        request: ChangePreviewRequestDto,
    ) = LocalizationManagerRpcApi.getInstance().previewChanges(project.projectId(), id, request)

    suspend fun applyPreviewedChanges(
        id: String,
        request: ChangePreviewRequestDto,
        expectedBeforeHashes: Map<String, String>,
    ) = LocalizationManagerRpcApi.getInstance().applyPreviewedChanges(project.projectId(), id, request, expectedBeforeHashes)
}

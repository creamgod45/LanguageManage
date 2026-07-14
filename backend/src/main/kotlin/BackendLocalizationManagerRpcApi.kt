package cg.creamgod45

import cg.creamgod45.localization.*
import com.intellij.platform.project.ProjectId
import com.intellij.platform.project.findProjectOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.withContext

class BackendLocalizationManagerRpcApi : LocalizationManagerRpcApi {
    private fun ProjectId.service() = findProjectOrNull()?.let(LocalizationManagerService::getInstance)
    override suspend fun state(projectId: ProjectId): Flow<LocalizationStateDto> = projectId.service()?.state ?: emptyFlow()
    override suspend fun createScheme(projectId: ProjectId, name: String, files: List<String>, usageSettings: UsageScanSettingsDto) =
        withContext(Dispatchers.IO) { projectId.service()?.createScheme(name, files, usageSettings); Unit }
    override suspend fun deleteScheme(projectId: ProjectId, schemeId: String) = withContext(Dispatchers.IO) { projectId.service()?.deleteScheme(schemeId); Unit }
    override suspend fun activateScheme(projectId: ProjectId, schemeId: String) = withContext(Dispatchers.IO) { projectId.service()?.activateScheme(schemeId); Unit }
    override suspend fun updateSchemeUsageSettings(projectId: ProjectId, schemeId: String, settings: UsageScanSettingsDto) =
        withContext(Dispatchers.IO) { projectId.service()?.updateSchemeUsageSettings(schemeId, settings); Unit }
    override suspend fun reload(projectId: ProjectId, schemeId: String, force: Boolean) = withContext(Dispatchers.IO) { projectId.service()?.reload(schemeId, force); Unit }
    override suspend fun discoverLanguageFiles(projectId: ProjectId, folderPaths: List<String>): FolderDiscoveryDto =
        withContext(Dispatchers.IO) {
            projectId.service()?.discoverLanguageFiles(folderPaths)
                ?: FolderDiscoveryDto(folderPaths.firstOrNull().orEmpty(), folderPaths = folderPaths)
        }
    override suspend fun exportSchemeSettings(projectId: ProjectId): String =
        withContext(Dispatchers.IO) { projectId.service()?.exportSchemeSettings().orEmpty() }
    override suspend fun previewSchemeSettingsImport(projectId: ProjectId, content: String): SchemeImportPreviewDto =
        withContext(Dispatchers.IO) { projectId.service()?.previewSchemeSettingsImport(content) ?: SchemeImportPreviewDto() }
    override suspend fun importSchemeSettings(projectId: ProjectId, content: String) =
        withContext(Dispatchers.IO) { projectId.service()?.importSchemeSettings(content); Unit }
    override suspend fun saveEntry(projectId: ProjectId, schemeId: String, mutation: EntryMutationDto) = withContext(Dispatchers.IO) { projectId.service()?.saveEntry(schemeId, mutation); Unit }
    override suspend fun deleteEntries(projectId: ProjectId, schemeId: String, entryIds: List<String>) = withContext(Dispatchers.IO) { projectId.service()?.deleteEntries(schemeId, entryIds); Unit }
    override suspend fun renameKey(projectId: ProjectId, schemeId: String, oldKey: String, newKey: String) = withContext(Dispatchers.IO) { projectId.service()?.renameKey(schemeId, oldKey, newKey); Unit }
    override suspend fun repair(projectId: ProjectId, schemeId: String) = withContext(Dispatchers.IO) { projectId.service()?.repair(schemeId); Unit }
    override suspend fun repairEntries(projectId: ProjectId, schemeId: String, entryIds: List<String>) = withContext(Dispatchers.IO) { projectId.service()?.repairEntries(schemeId, entryIds); Unit }
    override suspend fun previewLocaleVersion(projectId: ProjectId, schemeId: String, request: LocaleVersionRequestDto): ChangePreviewDto =
        withContext(Dispatchers.IO) { projectId.service()?.previewLocaleVersion(schemeId, request) ?: ChangePreviewDto() }
    override suspend fun createLocaleVersion(projectId: ProjectId, schemeId: String, request: LocaleVersionRequestDto, expectedTargetHashes: Map<String, String>) =
        withContext(Dispatchers.IO) { projectId.service()?.createLocaleVersion(schemeId, request, expectedTargetHashes); Unit }
    override suspend fun previewChanges(projectId: ProjectId, schemeId: String, request: ChangePreviewRequestDto): ChangePreviewDto =
        withContext(Dispatchers.IO) { projectId.service()?.previewChanges(schemeId, request) ?: ChangePreviewDto() }
    override suspend fun applyPreviewedChanges(projectId: ProjectId, schemeId: String, request: ChangePreviewRequestDto, expectedBeforeHashes: Map<String, String>) =
        withContext(Dispatchers.IO) { projectId.service()?.applyPreviewedChanges(schemeId, request, expectedBeforeHashes); Unit }
}

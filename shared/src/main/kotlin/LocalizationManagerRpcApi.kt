package cg.creamgod45.localization

import com.intellij.platform.project.ProjectId
import com.intellij.platform.rpc.RemoteApiProviderService
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.remoteApiDescriptor
import kotlinx.coroutines.flow.Flow

@Rpc
interface LocalizationManagerRpcApi : RemoteApi<Unit> {
    companion object {
        suspend fun getInstance(): LocalizationManagerRpcApi =
            RemoteApiProviderService.resolve(remoteApiDescriptor<LocalizationManagerRpcApi>())
    }

    suspend fun state(projectId: ProjectId): Flow<LocalizationStateDto>

    suspend fun loadProgress(projectId: ProjectId): Flow<LoadProgressDto>

    suspend fun resolveUsageLocation(
        projectId: ProjectId,
        schemeId: String,
        entryId: String,
        filePath: String,
        offset: Int,
    ): UsageLocationDto

    suspend fun createScheme(
        projectId: ProjectId,
        name: String,
        files: List<String>,
        usageSettings: UsageScanSettingsDto,
    )

    suspend fun deleteScheme(
        projectId: ProjectId,
        schemeId: String,
    )

    suspend fun activateScheme(
        projectId: ProjectId,
        schemeId: String,
    )

    suspend fun updateSchemeUsageSettings(
        projectId: ProjectId,
        schemeId: String,
        settings: UsageScanSettingsDto,
    )

    suspend fun addActiveSchemeExcludedDirectories(
        projectId: ProjectId,
        folderPaths: List<String>,
    ): ExclusionUpdateResultDto

    suspend fun reload(
        projectId: ProjectId,
        schemeId: String,
        force: Boolean,
    )

    suspend fun discoverLanguageFiles(
        projectId: ProjectId,
        folderPaths: List<String>,
        settings: UsageScanSettingsDto,
    ): FolderDiscoveryDto

    suspend fun exportSchemeSettings(projectId: ProjectId): String

    suspend fun previewSchemeSettingsImport(
        projectId: ProjectId,
        content: String,
    ): SchemeImportPreviewDto

    suspend fun importSchemeSettings(
        projectId: ProjectId,
        content: String,
    )

    suspend fun saveEntry(
        projectId: ProjectId,
        schemeId: String,
        mutation: EntryMutationDto,
    )

    suspend fun saveEntries(
        projectId: ProjectId,
        schemeId: String,
        mutations: List<EntryMutationDto>,
    )

    suspend fun translateWithAi(
        projectId: ProjectId,
        request: AiTranslationRequestDto,
    ): AiTranslationResultDto

    suspend fun previewEntryMutations(
        projectId: ProjectId,
        schemeId: String,
        mutations: List<EntryMutationDto>,
    ): ChangePreviewDto

    suspend fun applyPreviewedEntryMutations(
        projectId: ProjectId,
        schemeId: String,
        mutations: List<EntryMutationDto>,
        expectedBeforeHashes: Map<String, String>,
    )

    suspend fun deleteEntries(
        projectId: ProjectId,
        schemeId: String,
        entryIds: List<String>,
    )

    suspend fun renameKey(
        projectId: ProjectId,
        schemeId: String,
        oldKey: String,
        newKey: String,
    )

    suspend fun repair(
        projectId: ProjectId,
        schemeId: String,
    )

    suspend fun repairEntries(
        projectId: ProjectId,
        schemeId: String,
        entryIds: List<String>,
    )

    suspend fun previewLocaleVersion(
        projectId: ProjectId,
        schemeId: String,
        request: LocaleVersionRequestDto,
    ): ChangePreviewDto

    suspend fun createLocaleVersion(
        projectId: ProjectId,
        schemeId: String,
        request: LocaleVersionRequestDto,
        expectedTargetHashes: Map<String, String>,
    )

    suspend fun previewChanges(
        projectId: ProjectId,
        schemeId: String,
        request: ChangePreviewRequestDto,
    ): ChangePreviewDto

    suspend fun applyPreviewedChanges(
        projectId: ProjectId,
        schemeId: String,
        request: ChangePreviewRequestDto,
        expectedBeforeHashes: Map<String, String>,
    )
}

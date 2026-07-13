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
    suspend fun createScheme(projectId: ProjectId, name: String, files: List<String>)
    suspend fun deleteScheme(projectId: ProjectId, schemeId: String)
    suspend fun activateScheme(projectId: ProjectId, schemeId: String)
    suspend fun reload(projectId: ProjectId, schemeId: String, force: Boolean)
    suspend fun saveEntry(projectId: ProjectId, schemeId: String, mutation: EntryMutationDto)
    suspend fun deleteEntries(projectId: ProjectId, schemeId: String, entryIds: List<String>)
    suspend fun renameKey(projectId: ProjectId, schemeId: String, oldKey: String, newKey: String)
    suspend fun repair(projectId: ProjectId, schemeId: String)
}

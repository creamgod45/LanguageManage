package cg.creamgod45

import cg.creamgod45.localization.LocalizationManagerRpcApi
import com.intellij.platform.rpc.backend.RemoteApiProvider
import fleet.rpc.remoteApiDescriptor

internal class BackendRpcApiProvider : RemoteApiProvider {
    override fun RemoteApiProvider.Sink.remoteApis() {
        remoteApi(remoteApiDescriptor<LocalizationManagerRpcApi>()) {
            BackendLocalizationManagerRpcApi()
        }
    }
}

package git.more.profiles.backend

import com.intellij.platform.rpc.backend.RemoteApiProvider
import fleet.rpc.remoteApiDescriptor
import git.more.profiles.rpc.GitProfilesRpcApi

/** Registers the backend RPC implementation with the platform's remote-API registry. */
internal class BackendRpcApiProvider : RemoteApiProvider {
    override fun RemoteApiProvider.Sink.remoteApis() {
        remoteApi(remoteApiDescriptor<GitProfilesRpcApi>()) {
            GitProfilesRpcApiImpl()
        }
    }
}

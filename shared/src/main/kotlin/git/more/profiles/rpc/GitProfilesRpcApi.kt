package git.more.profiles.rpc

import com.intellij.platform.project.ProjectId
import com.intellij.platform.rpc.RemoteApiProviderService
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.remoteApiDescriptor
import git.more.profiles.GitProfile

/**
 * The contract the frontend (JetBrains Client) uses to drive git identity operations that must run
 * on the backend (host), where the repositories and git config actually live.
 *
 * All functions are `suspend` and every parameter/return type is `@Serializable`, as cross-process
 * RPC requires.
 */
@Rpc
interface GitProfilesRpcApi : RemoteApi<Unit> {
    companion object {
        suspend fun getInstance(): GitProfilesRpcApi =
            RemoteApiProviderService.resolve(remoteApiDescriptor<GitProfilesRpcApi>())
    }

    /** Reads the global identity and every repository's local identity in one round-trip. */
    suspend fun getSnapshot(projectId: ProjectId): GitConfigSnapshotDto

    /** Sets the machine-wide `--global` identity. */
    suspend fun setGlobalProfile(projectId: ProjectId, profile: GitProfile)

    /** Assigns a repository's local (`--local`) identity, overriding the global one. */
    suspend fun setRepoProfile(projectId: ProjectId, repoId: String, profile: GitProfile)

    /** Removes a repository's local identity so it falls back to the global one. */
    suspend fun unsetRepoProfile(projectId: ProjectId, repoId: String)
}

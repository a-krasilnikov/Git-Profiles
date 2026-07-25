package git.more.profiles.backend

import com.intellij.platform.project.ProjectId
import com.intellij.platform.project.findProjectOrNull
import git.more.profiles.GitProfile
import git.more.profiles.rpc.GitConfigSnapshotDto
import git.more.profiles.rpc.GitProfilesRpcApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Resolves the [ProjectId] back to a project and delegates to [GitProfilesBackendService],
 * running the blocking git calls on [Dispatchers.IO].
 */
internal class GitProfilesRpcApiImpl : GitProfilesRpcApi {

    override suspend fun getSnapshot(projectId: ProjectId): GitConfigSnapshotDto {
        val project = projectId.findProjectOrNull() ?: return GitConfigSnapshotDto(null, emptyList())
        return withContext(Dispatchers.IO) { GitProfilesBackendService.getInstance(project).snapshot() }
    }

    override suspend fun setGlobalProfile(projectId: ProjectId, profile: GitProfile) {
        val project = projectId.findProjectOrNull() ?: return
        withContext(Dispatchers.IO) { GitProfilesBackendService.getInstance(project).setGlobalProfile(profile) }
    }

    override suspend fun setRepoProfile(projectId: ProjectId, repoId: String, profile: GitProfile) {
        val project = projectId.findProjectOrNull() ?: return
        withContext(Dispatchers.IO) { GitProfilesBackendService.getInstance(project).setRepoProfile(repoId, profile) }
    }

    override suspend fun unsetRepoProfile(projectId: ProjectId, repoId: String) {
        val project = projectId.findProjectOrNull() ?: return
        withContext(Dispatchers.IO) { GitProfilesBackendService.getInstance(project).unsetRepoProfile(repoId) }
    }
}

package git.more.profiles.backend

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import git.more.profiles.GitProfile
import git.more.profiles.rpc.GitConfigSnapshotDto
import git.more.profiles.rpc.RepoAssignmentDto
import git.more.profiles.rpc.RepoDto
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager

/**
 * Owns all git4idea access for one project: repository enumeration plus git config reads/writes.
 *
 * The git calls block, so they must run off the EDT — the RPC layer invokes them from a background
 * dispatcher (see [GitProfilesRpcApiImpl]).
 */
@Service(Service.Level.PROJECT)
class GitProfilesBackendService(private val project: Project) {

    /** Reads the global identity and each repository's local identity in one shot. */
    fun snapshot(): GitConfigSnapshotDto {
        val global = GitConfigOperations.readGlobalProfile(project)
        val repositories = repositories().map { repository ->
            // The dialog distinguishes an override from an inherited identity, so it needs the
            // local scope rather than what git resolves.
            RepoAssignmentDto(repository.toDto(), GitConfigOperations.readLocalProfile(repository))
        }
        return GitConfigSnapshotDto(global, repositories)
    }

    fun setGlobalProfile(profile: GitProfile) {
        GitConfigOperations.setGlobalProfile(project, profile)
    }

    fun setRepoProfile(repoId: String, profile: GitProfile) {
        val repository = repositoryById(repoId) ?: return
        GitConfigOperations.setProfile(repository, profile)
    }

    fun unsetRepoProfile(repoId: String) {
        val repository = repositoryById(repoId) ?: return
        GitConfigOperations.unsetLocalProfile(repository)
    }

    private fun repositories(): List<GitRepository> =
        GitRepositoryManager.getInstance(project).repositories

    private fun repositoryById(repoId: String): GitRepository? =
        repositories().firstOrNull { it.root.path == repoId }

    private fun GitRepository.toDto(): RepoDto = RepoDto(root.path, root.name)

    companion object {
        fun getInstance(project: Project): GitProfilesBackendService = project.service()
    }
}

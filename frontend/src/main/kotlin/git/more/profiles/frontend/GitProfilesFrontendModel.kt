package git.more.profiles.frontend

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.platform.ide.progress.withModalProgress
import com.intellij.platform.project.projectId
import git.more.profiles.GitProfile
import git.more.profiles.rpc.GitConfigSnapshotDto
import git.more.profiles.rpc.GitProfilesRpcApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The frontend's RPC client: everything the UI needs from the backend goes through here.
 */
@Service(Service.Level.PROJECT)
class GitProfilesFrontendModel(private val project: Project, private val scope: CoroutineScope) {

    /** Reads the global identity and every repository's local identity in one round-trip. */
    suspend fun snapshot(): GitConfigSnapshotDto =
        GitProfilesRpcApi.getInstance().getSnapshot(project.projectId())

    suspend fun setGlobalProfile(profile: GitProfile) {
        GitProfilesRpcApi.getInstance().setGlobalProfile(project.projectId(), profile)
    }

    suspend fun setRepoProfile(repoId: String, profile: GitProfile) {
        GitProfilesRpcApi.getInstance().setRepoProfile(project.projectId(), repoId, profile)
    }

    suspend fun unsetRepoProfile(repoId: String) {
        GitProfilesRpcApi.getInstance().unsetRepoProfile(project.projectId(), repoId)
    }

    /**
     * Shows the dialog after a host-side invocation, delivered by [OpenGitProfilesDialogListener].
     * That callback cannot suspend, so the work is handed to this service's scope.
     */
    fun openDialogFromHost() {
        scope.launch {
            runCatching {
                withModalProgress(project, GitProfilesBundle.message("progress.reading.config")) {
                    openGitProfilesDialog(project, this@GitProfilesFrontendModel)
                }
            }.onFailure { thisLogger().warn("Git Profiles: failed to open the dialog", it) }
        }
    }

    companion object {
        fun getInstance(project: Project): GitProfilesFrontendModel = project.service()
    }
}

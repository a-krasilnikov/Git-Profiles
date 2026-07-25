package git.more.profiles.frontend

import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import git.more.profiles.frontend.ui.GitProfilesDialog
import git.more.profiles.rpc.GitConfigSnapshotDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Opens the dialog. Must be called from a coroutine; the caller wraps it in modal progress.
 */
internal suspend fun openGitProfilesDialog(project: Project, model: GitProfilesFrontendModel) {
    // Git config is the source of truth: read it first, and import the identities it discovered
    // so they show up as stored profiles.
    val snapshot = model.snapshot()
    importDiscoveredProfiles(snapshot)

    withContext(Dispatchers.EDT) {
        GitProfilesDialog(project, snapshot, model).show()
    }
}

private fun importDiscoveredProfiles(snapshot: GitConfigSnapshotDto) {
    val service = GitProfilesService.getInstance()
    val discovered = listOfNotNull(snapshot.global) + snapshot.repositories.mapNotNull { it.local }
    discovered.distinct().forEach { service.add(it) }
}

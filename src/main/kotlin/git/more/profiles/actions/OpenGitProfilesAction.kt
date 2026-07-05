package git.more.profiles.actions

import git.more.profiles.GitProfilesBundle
import git.more.profiles.services.GitConfigOperations
import git.more.profiles.services.GitProfilesService
import git.more.profiles.ui.GitProfilesDialog
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.TaskCancellation
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import git4idea.repo.GitRepositoryManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class OpenGitProfilesAction : DumbAwareAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val repositories = GitRepositoryManager.getInstance(project).repositories

        // Git config is the source of truth: snapshot it before showing the dialog, and
        // import identities the snapshot discovered so they show up as stored profiles.
        val snapshot = runWithModalProgressBlocking(
            ModalTaskOwner.project(project),
            GitProfilesBundle.message("progress.reading.config"),
            TaskCancellation.nonCancellable(),
        ) {
            withContext(Dispatchers.IO) {
                val snapshot = GitProfilesDialog.Snapshot(
                    globalProfile = GitConfigOperations.readGlobalProfile(project),
                    repositories = repositories.map { it to GitConfigOperations.readLocalProfile(it) },
                )
                val service = GitProfilesService.getInstance()
                val discovered = listOfNotNull(snapshot.globalProfile) + snapshot.repositories.mapNotNull { it.second }
                discovered.distinct().forEach { service.add(it) }
                snapshot
            }
        }

        GitProfilesDialog(project, snapshot).show()
    }
}

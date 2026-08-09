package git.more.profiles.actions

import com.intellij.ide.HelpTooltip
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.TaskCancellation
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import git.more.profiles.GitProfilesBundle.message
import git.more.profiles.providers.GitProfileProvider
import git.more.profiles.services.GitConfigOperations
import git.more.profiles.services.GitProfileCache
import git.more.profiles.services.GitProfilesService
import git.more.profiles.ui.GitProfilesDialog
import git4idea.repo.GitRepositoryManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

class OpenGitProfilesAction : DumbAwareAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val project = e.project
        e.presentation.isEnabledAndVisible = project != null
        if (project == null || !e.isFromActionToolbar) return

        e.presentation.putClientProperty(ActionButton.CUSTOM_HELP_TOOLTIP, buildTooltip(project))
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val repositories = GitRepositoryManager.getInstance(project).repositories

        // Git config is the source of truth: snapshot it before showing the dialog, and
        // import identities the snapshot discovered so they show up as stored profiles.
        runWithModalProgressBlocking(
            ModalTaskOwner.project(project),
            message("progress.reading.config"),
            TaskCancellation.nonCancellable(),
        ) {
            withContext(Dispatchers.IO) {
                coroutineScope {
                    // Hits the network, so let it overlap with the git config reads below.
                    val providerProfiles = async { GitProfileProvider.discoverAll() }

                    val snapshot = GitProfilesDialog.Snapshot(
                        globalProfile = GitConfigOperations.readGlobalProfile(project),
                        // The dialog distinguishes an override from an inherited identity, so it
                        // needs the local scope rather than what git resolves.
                        repositories = repositories.map { it to GitConfigOperations.readLocalProfile(it) },
                    )
                    val service = GitProfilesService.getInstance()
                    val discovered =
                        listOfNotNull(snapshot.globalProfile) + snapshot.repositories.mapNotNull { it.second }
                    discovered.distinct().forEach { service.add(it) }
                    providerProfiles.await().forEach { service.add(it.profile, it.origin) }

                    withContext(Dispatchers.EDT) {
                        GitProfilesDialog(project, snapshot).show()
                        // The listener covers local edits; a new global identity needs this.
                        GitProfileCache.getInstance(project).clear()
                    }
                }
            }
        }
    }
}


private fun buildTooltip(project: Project): HelpTooltip = HelpTooltip()
    .setTitle(message("tooltip.title"))
    .setDescription(buildDescription(project))

private fun buildDescription(project: Project): String {
    val repositories = GitRepositoryManager.getInstance(project).repositories
    val cache = GitProfileCache.getInstance(project)

    if (repositories.size == 1) {
        val profile = cache.getProfile(repositories[0]) ?: return ""

        return HtmlBuilder()
            .append(message("tooltip.repository.current"))
            .br()
            .append(profile.toString())
            .toString()
    }

    val lines = repositories.mapNotNull { repository ->
        val profile = cache.getProfile(repository) ?: return@mapNotNull null
        HtmlChunk.text(message("tooltip.repository.named", repository.root.name, profile.toString()))
    }
    return HtmlBuilder().appendWithSeparators(HtmlChunk.br(), lines).toString()
}
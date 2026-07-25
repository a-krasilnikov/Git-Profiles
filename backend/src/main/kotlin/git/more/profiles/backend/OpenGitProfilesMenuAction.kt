package git.more.profiles.backend

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.platform.rpc.topics.broadcast
import git.more.profiles.rpc.OPEN_GIT_PROFILES_DIALOG_TOPIC
import git.more.profiles.rpc.OpenGitProfilesDialogRequest

/**
 * The plugin's entry point, registered here rather than on the frontend because menus and the
 * commit toolbar are rendered from the *backend's* action model — a frontend-registered action
 * never appears in them in split mode.
 *
 * Shows no UI itself: it broadcasts [OPEN_GIT_PROFILES_DIALOG_TOPIC] to the frontend, which owns
 * the dialog. In a monolithic IDE both sides are the same process and the hand-off is a detour.
 */
internal class OpenGitProfilesMenuAction : DumbAwareAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        thisLogger().info("Git Profiles: broadcasting the open-dialog request to the frontend")
        OPEN_GIT_PROFILES_DIALOG_TOPIC.broadcast(project, OpenGitProfilesDialogRequest)
    }
}

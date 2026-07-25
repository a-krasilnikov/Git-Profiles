package git.more.profiles.frontend

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.platform.rpc.topics.ProjectRemoteTopic
import com.intellij.platform.rpc.topics.ProjectRemoteTopicListener
import git.more.profiles.rpc.OPEN_GIT_PROFILES_DIALOG_TOPIC
import git.more.profiles.rpc.OpenGitProfilesDialogRequest

/**
 * Receives the "show the dialog" request the host sends when the action is invoked from a menu or
 * the commit toolbar.
 *
 * Registered via `platform.rpc.projectRemoteTopicListener`, so the platform wires it up on project
 * open — delivery does not depend on any of the plugin's own services having been created yet.
 */
internal class OpenGitProfilesDialogListener : ProjectRemoteTopicListener<OpenGitProfilesDialogRequest> {

    override val topic: ProjectRemoteTopic<OpenGitProfilesDialogRequest>
        get() = OPEN_GIT_PROFILES_DIALOG_TOPIC

    override fun handleEvent(project: Project, event: OpenGitProfilesDialogRequest) {
        // Logged on both sides of the process boundary on purpose: this hand-off has no visible
        // failure mode — if it breaks, the menu item simply does nothing.
        thisLogger().info("Git Profiles: open-dialog request received from the host")
        GitProfilesFrontendModel.getInstance(project).openDialogFromHost()
    }
}

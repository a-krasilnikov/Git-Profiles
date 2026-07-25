package git.more.profiles.rpc

import com.intellij.platform.rpc.topics.ProjectRemoteTopic
import kotlinx.serialization.Serializable

/** Payload of [OPEN_GIT_PROFILES_DIALOG_TOPIC]; the event itself carries no data. */
@Serializable
object OpenGitProfilesDialogRequest

/**
 * Backend → frontend request to show the Git Profiles dialog.
 *
 * Needed because menus and the commit toolbar are rendered from the *backend's* action model even
 * in split mode, so invoking the plugin from them fires on the host — while the dialog lives on the
 * frontend. A remote topic is the platform's mechanism for this direction: the frontend listener is
 * registered declaratively (`platform.rpc.projectRemoteTopicListener`) by a preloaded platform
 * service, so delivery does not depend on any of our own services having been instantiated yet.
 */
val OPEN_GIT_PROFILES_DIALOG_TOPIC: ProjectRemoteTopic<OpenGitProfilesDialogRequest> =
    ProjectRemoteTopic("git.more.profiles.openDialog", OpenGitProfilesDialogRequest.serializer())

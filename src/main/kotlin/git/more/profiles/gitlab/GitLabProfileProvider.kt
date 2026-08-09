package git.more.profiles.gitlab

import com.intellij.collaboration.util.resolveRelative
import com.intellij.openapi.components.service
import com.intellij.vcs.gitlab.icons.GitlabIcons
import git.more.profiles.GitProfile
import git.more.profiles.GitProfilesBundle.message
import git.more.profiles.providers.AccountProfileProvider
import org.jetbrains.plugins.gitlab.api.GitLabApiManager
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccount
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccountManager
import javax.swing.Icon

/**
 * Offers the identity of every GitLab account the IDE is signed in to, whatever the project at hand
 * happens to be hosted on.
 *
 * The only class in the plugin that touches `org.jetbrains.plugins.gitlab`, and it is instantiated
 * only from `git-profiles-gitlab.xml` — which the platform loads only when the GitLab plugin is
 * there. Keep it that way.
 */
internal class GitLabProfileProvider : AccountProfileProvider<GitLabAccount>() {

    override val id: String = "gitlab"

    override val displayName: String get() = message("provider.gitlab.name")

    override val icon: Icon = GitlabIcons.GitLabLogo

    override fun accounts(): Collection<GitLabAccount> = service<GitLabAccountManager>().accountsState.value

    override suspend fun identitiesFor(account: GitLabAccount): List<GitProfile> {
        val token = service<GitLabAccountManager>().findCredentials(account) ?: return emptyList()

        // getClient only builds the client; the token is turned into an Authorization header for us.
        val rest = service<GitLabApiManager>().getClient(account.server, token).rest
        val request = rest.request(account.server.restApiUri.resolveRelative("user")).GET().build()

        // loadJsonValueByClass rather than the reified loadJsonValue extension: a plain interface
        // method cannot drag another module's inlined internals into our compilation.
        val user = rest.loadJsonValueByClass(request, GitLabUserDto::class.java).body()
        return buildGitLabProfiles(user)
    }
}

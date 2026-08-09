package git.more.profiles.github

import com.intellij.icons.AllIcons
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import git.more.profiles.GitProfile
import git.more.profiles.GitProfilesBundle.message
import git.more.profiles.providers.AccountProfileProvider
import kotlinx.coroutines.CancellationException
import org.jetbrains.plugins.github.api.GithubApiContentHelper
import org.jetbrains.plugins.github.api.GithubApiRequest
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.api.executeSuspend
import org.jetbrains.plugins.github.authentication.accounts.GHAccountManager
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import javax.swing.Icon

/**
 * Offers the identity of every GitHub account the IDE is signed in to, whatever the project at hand
 * happens to be hosted on.
 *
 * The only class in the plugin that touches `org.jetbrains.plugins.github`, and it is instantiated
 * only from `git-profiles-github.xml` — which the platform loads only when the GitHub plugin is
 * there. Keep it that way.
 */
internal class GitHubProfileProvider : AccountProfileProvider<GithubAccount>() {

    override val id: String = "github"

    override val displayName: String get() = message("provider.github.name")

    override val icon: Icon = AllIcons.Vcs.Vendors.Github

    override fun accounts(): Collection<GithubAccount> = service<GHAccountManager>().accountsState.value

    override suspend fun identitiesFor(account: GithubAccount): List<GitProfile> {
        val token = service<GHAccountManager>().findCredentials(account) ?: return emptyList()

        val executor = GithubApiRequestExecutor.Factory.getInstance().create(account.server, token)
        val apiUrl = account.server.toApiUrl()
        val user = executor.executeSuspend(get("$apiUrl/user", GitHubUserDto::class.java))
        return buildGitHubProfiles(user, verifiedEmails(executor, apiUrl))
    }

    /**
     * The token the IDE mints carries `user:email`, but a token the user pasted by hand need not:
     * a 403 here means there are no extra addresses to offer, not that the import failed.
     */
    private suspend fun verifiedEmails(
        executor: GithubApiRequestExecutor,
        apiUrl: String,
    ): List<GitHubEmailDto> = try {
        executor.executeSuspend(
            GithubApiRequest.Get.JsonPage(
                "$apiUrl/user/emails",
                GitHubEmailDto::class.java,
                GithubApiContentHelper.V3_JSON_MIME_TYPE,
            )
        ).items
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        thisLogger().debug("GitHub /user/emails is not readable with this token", e)
        emptyList()
    }

    private fun <T> get(url: String, type: Class<T>): GithubApiRequest.Get.Json<T> =
        GithubApiRequest.Get.Json(url, type, GithubApiContentHelper.V3_JSON_MIME_TYPE)
}

package git.more.profiles.providers

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.extensions.ExtensionPointName
import git.more.profiles.GitProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.jetbrains.annotations.Nls
import javax.swing.Icon

/** A profile together with the id of the provider that found it. */
data class DiscoveredProfile(val profile: GitProfile, val origin: String)

/**
 * A hosting provider the IDE can be signed in to, able to offer the git identities of its accounts.
 *
 * Everything the rest of the plugin needs to know about a provider lives here — its stored id, what
 * to call it, how to badge it, and how to read identities — so adding GitLab means one new
 * implementation and no changes anywhere else.
 *
 * Implementations belong behind an optional dependency on the plugin they integrate with, and this
 * interface must stay free of any such plugin's types: it is loaded even when they are all disabled.
 */
interface GitProfileProvider {

    /**
     * Recorded on every profile this provider imports.
     *
     * **Persisted**, so it must stay stable across releases — changing it silently un-badges every
     * profile already on disk.
     */
    val id: String

    /** How the provider is named to the user, e.g. in the profiles table. */
    val displayName: @Nls String

    /** Badges the profiles this provider imported. */
    val icon: Icon

    /**
     * Identities of every account the IDE is signed in to on this provider. Must not throw.
     *
     * Deliberately not scoped to a project: the profile list is IDE-wide, and an account's identity
     * is worth offering whether or not the project at hand happens to be hosted here.
     */
    suspend fun discoverIdentities(): List<GitProfile>

    companion object {
        private val EP_NAME =
            ExtensionPointName<GitProfileProvider>("git.more.profiles.git-profiles.profileProvider")

        /** The provider that recorded [origin], or `null` once its plugin is gone. */
        fun forOrigin(origin: String): GitProfileProvider? =
            if (origin.isEmpty()) null else EP_NAME.extensionList.firstOrNull { it.id == origin }

        /** Asks every registered provider at once, so one slow one does not delay the others. */
        suspend fun discoverAll(): List<DiscoveredProfile> = coroutineScope {
            EP_NAME.extensionList
                .map { provider -> async { provider.discoverSafely() } }
                .awaitAll()
                .flatten()
        }

        /**
         * Providers are contracted not to throw, but one that does must not take the others — or
         * the dialog — down with it.
         */
        private suspend fun GitProfileProvider.discoverSafely(): List<DiscoveredProfile> = try {
            discoverIdentities().map { DiscoveredProfile(it, id) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            thisLogger().warn("Profile provider '$id' failed", e)
            emptyList()
        }
    }
}

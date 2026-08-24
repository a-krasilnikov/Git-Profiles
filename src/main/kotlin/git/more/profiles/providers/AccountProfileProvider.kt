package git.more.profiles.providers

import com.intellij.openapi.diagnostic.thisLogger
import git.more.profiles.GitProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull

/** The dialog opens behind discovery; a hung network must never hold it up. */
private const val DISCOVERY_TIMEOUT_MS = 5_000L

/**
 * Base for providers backed by accounts the IDE is signed in to.
 *
 * It owns the two things every such provider has to get right — never hang the dialog, and never
 * let one unreachable account cost the others — leaving subclasses the part that genuinely
 * differs: which accounts exist and what their API says about one. Neither is scoped to a project;
 * see [GitProfileProvider.discoverIdentities].
 *
 * The account type is an unbounded parameter rather than `com.intellij.collaboration.auth
 * .ServerAccount`, tempting as that common supertype is: it lives in the collaborationTools module,
 * and this class is loaded even when every hosting plugin is disabled.
 */
abstract class AccountProfileProvider<A : Any> : GitProfileProvider {

    /** Accounts the IDE is currently signed in to. */
    protected abstract fun accounts(): Collection<A>

    /** Reads the account's identities from its API. May throw; the caller handles it. */
    protected abstract suspend fun identitiesFor(account: A): List<GitProfile>

    final override suspend fun discoverIdentities(): List<GitProfile> = coroutineScope {
        accounts()
            .map { account -> async { identitiesSafely(account) } }
            .awaitAll()
            .flatten()
    }

    /**
     * Whatever one account costs — an error, or a server that never answers — it costs only that
     * account.
     *
     * The timeout has to be per account rather than one budget for all of them: a shared deadline
     * expiring cancels the whole traversal, throwing away the identities the accounts before it had
     * already returned. Accounts are read concurrently so that the wall-clock cost stays one
     * timeout rather than one per account.
     */
    private suspend fun identitiesSafely(account: A): List<GitProfile> = try {
        val identities = withTimeoutOrNull(DISCOVERY_TIMEOUT_MS) { identitiesFor(account) }
        if (identities == null) {
            thisLogger().warn("Timed out reading identities from '$id' account $account")
        }
        identities.orEmpty()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        thisLogger().warn("Could not read identities from '$id' account $account", e)
        emptyList()
    }
}

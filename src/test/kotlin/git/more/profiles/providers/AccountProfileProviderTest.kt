package git.more.profiles.providers

import git.more.profiles.GitProfile
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import javax.swing.Icon

/**
 * Pins what the base class is still there for: one unreachable account must not cost the others.
 *
 * A provider is contracted not to throw, but its API call is a network round-trip against a server
 * the user does not control, so it will — and losing every other account's identity to one bad
 * token would be a silent, confusing failure.
 */
class AccountProfileProviderTest {

    /** Accounts are plain names here; the base class never looks inside them. */
    private class FakeProvider(
        private val accounts: List<String>,
        private val failOn: Set<String> = emptySet(),
    ) : AccountProfileProvider<String>() {

        override val id: String = "fake"
        override val displayName: String = "Fake"
        override val icon: Icon get() = error("not used")

        override fun accounts(): Collection<String> = accounts

        override suspend fun identitiesFor(account: String): List<GitProfile> {
            if (account in failOn) throw IllegalStateException("$account is unreachable")
            return listOf(GitProfile(account, "$account@example.com"))
        }
    }

    private fun discover(provider: FakeProvider): List<GitProfile> =
        runBlocking { provider.discoverIdentities() }

    @Test
    fun `every account contributes its identities`() {
        val profiles = discover(FakeProvider(listOf("work", "home")))

        assertEquals(
            listOf(GitProfile("work", "work@example.com"), GitProfile("home", "home@example.com")),
            profiles,
        )
    }

    @Test
    fun `a failing account does not cost the others`() {
        val profiles = discover(FakeProvider(listOf("work", "broken", "home"), failOn = setOf("broken")))

        assertEquals(
            listOf(GitProfile("work", "work@example.com"), GitProfile("home", "home@example.com")),
            profiles,
        )
    }

    @Test
    fun `every account failing yields nothing rather than throwing`() {
        val profiles = discover(FakeProvider(listOf("a", "b"), failOn = setOf("a", "b")))

        assertEquals(emptyList<GitProfile>(), profiles)
    }

    @Test
    fun `no accounts means no identities`() {
        assertEquals(emptyList<GitProfile>(), discover(FakeProvider(emptyList())))
    }
}

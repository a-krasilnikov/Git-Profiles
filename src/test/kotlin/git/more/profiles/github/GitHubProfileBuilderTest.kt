package git.more.profiles.github

import git.more.profiles.GitProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit tests for turning GitHub API payloads into profiles (no platform, no network). */
class GitHubProfileBuilderTest {

    private fun user(
        login: String = "octocat",
        name: String? = "The Octocat",
        email: String? = null,
    ) = GitHubUserDto().also {
        it.login = login
        it.name = name
        it.email = email
    }

    private fun email(address: String, primary: Boolean = false, verified: Boolean = true) =
        GitHubEmailDto().also {
            it.email = address
            it.primary = primary
            it.verified = verified
        }

    @Test
    fun `a hidden profile email still yields the primary address`() {
        val profiles = buildGitHubProfiles(
            user(email = null),
            listOf(email("octocat@example.com", primary = true)),
        )

        assertEquals(GitProfile("The Octocat", "octocat@example.com"), profiles.first())
    }

    @Test
    fun `the primary address comes before the other verified ones`() {
        val profiles = buildGitHubProfiles(
            user(email = null),
            listOf(email("second@example.com"), email("primary@example.com", primary = true)),
        )

        assertEquals(listOf("primary@example.com", "second@example.com"), profiles.map { it.email })
    }

    @Test
    fun `the public address is offered after the verified ones`() {
        val profiles = buildGitHubProfiles(
            user(email = "public@example.com"),
            listOf(email("primary@example.com", primary = true)),
        )

        assertEquals(listOf("primary@example.com", "public@example.com"), profiles.map { it.email })
    }

    @Test
    fun `without the emails endpoint the public address is all there is`() {
        val profiles = buildGitHubProfiles(user(email = "public@example.com"), emails = emptyList())

        assertEquals(listOf(GitProfile("The Octocat", "public@example.com")), profiles)
    }

    /** Nothing is invented, so an account GitHub tells us nothing usable about yields nothing. */
    @Test
    fun `an account with no readable address yields nothing`() {
        val profiles = buildGitHubProfiles(user(email = null), emails = emptyList())

        assertEquals(emptyList<GitProfile>(), profiles)
    }

    @Test
    fun `unverified addresses are not offered`() {
        val profiles = buildGitHubProfiles(
            user(email = null),
            listOf(email("unverified@example.com", primary = true, verified = false)),
        )

        assertTrue(profiles.none { it.email == "unverified@example.com" })
    }

    @Test
    fun `an address listed twice becomes a single profile`() {
        val profiles = buildGitHubProfiles(
            user(email = "octocat@example.com"),
            listOf(email("octocat@example.com", primary = true)),
        )

        assertEquals(listOf("octocat@example.com"), profiles.map { it.email })
    }

    @Test
    fun `the login stands in for a missing display name`() {
        val profiles = buildGitHubProfiles(user(name = null, email = "a@example.com"), emptyList())

        assertEquals("octocat", profiles.single().name)
    }

    @Test
    fun `a blank display name falls back to the login too`() {
        val profiles = buildGitHubProfiles(user(name = "   ", email = "a@example.com"), emptyList())

        assertEquals("octocat", profiles.single().name)
    }

    @Test
    fun `nothing is built for an account without a usable name`() {
        val profiles = buildGitHubProfiles(user(login = "", name = null, email = "a@example.com"), emptyList())

        assertEquals(emptyList<GitProfile>(), profiles)
    }

    @Test
    fun `a blank public email is ignored`() {
        val profiles = buildGitHubProfiles(user(email = "  "), emptyList())

        assertEquals(emptyList<GitProfile>(), profiles)
    }
}

package git.more.profiles.gitlab

import git.more.profiles.GitProfile
import org.junit.Assert.assertEquals
import org.junit.Test

/** Unit tests for turning the GitLab /user response into profiles (no platform, no network). */
class GitLabProfileBuilderTest {

    private fun user(
        username: String = "octocat",
        name: String? = "The Octocat",
        email: String? = null,
        publicEmail: String? = null,
        commitEmail: String? = null,
    ) = GitLabUserDto().also {
        it.username = username
        it.name = name
        it.email = email
        it.publicEmail = publicEmail
        it.commitEmail = commitEmail
    }

    @Test
    fun `the commit address comes first`() {
        val profiles = buildGitLabProfiles(
            user(email = "primary@example.com", commitEmail = "commit@example.com"),
        )

        assertEquals(GitProfile("The Octocat", "commit@example.com"), profiles.first())
    }

    /**
     * A private commit address is reported by GitLab rather than invented by us, so it is offered
     * like any other — nothing filters it out.
     */
    @Test
    fun `a private profile offers the noreply address GitLab reported`() {
        val profiles = buildGitLabProfiles(
            user(email = "primary@example.com", commitEmail = "42-octocat@users.noreply.gitlab.com"),
        )

        assertEquals("42-octocat@users.noreply.gitlab.com", profiles.first().email)
    }

    @Test
    fun `candidates keep the commit public primary order`() {
        val profiles = buildGitLabProfiles(
            user(
                email = "primary@example.com",
                publicEmail = "public@example.com",
                commitEmail = "commit@example.com",
            ),
        )

        assertEquals(
            listOf("commit@example.com", "public@example.com", "primary@example.com"),
            profiles.map { it.email },
        )
    }

    /** GitLab returns an empty string rather than null when no public address is configured. */
    @Test
    fun `an unset public email is not offered`() {
        val profiles = buildGitLabProfiles(user(publicEmail = "", commitEmail = "commit@example.com"))

        assertEquals(listOf(GitProfile("The Octocat", "commit@example.com")), profiles)
    }

    @Test
    fun `an address reported twice becomes a single profile`() {
        val profiles = buildGitLabProfiles(user(email = "same@example.com", commitEmail = "same@example.com"))

        assertEquals(listOf(GitProfile("The Octocat", "same@example.com")), profiles)
    }

    @Test
    fun `the username stands in for a missing display name`() {
        val profiles = buildGitLabProfiles(user(name = null, commitEmail = "commit@example.com"))

        assertEquals("octocat", profiles.single().name)
    }

    @Test
    fun `a blank display name falls back to the username too`() {
        val profiles = buildGitLabProfiles(user(name = "   ", commitEmail = "commit@example.com"))

        assertEquals("octocat", profiles.single().name)
    }

    @Test
    fun `nothing is built for an account without a usable name`() {
        val profiles = buildGitLabProfiles(user(username = "", name = null, commitEmail = "c@example.com"))

        assertEquals(emptyList<GitProfile>(), profiles)
    }

    @Test
    fun `an account with no addresses at all yields nothing`() {
        assertEquals(emptyList<GitProfile>(), buildGitLabProfiles(user()))
    }
}

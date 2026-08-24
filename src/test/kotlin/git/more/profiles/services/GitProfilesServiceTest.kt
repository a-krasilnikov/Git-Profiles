package git.more.profiles.services

import git.more.profiles.GitProfile
import git.more.profiles.ProfileOrigin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The store records an origin without interpreting it, so any provider id will do here. */
private const val SOME_ORIGIN = "some-provider"

/** Unit tests for the profile store (no platform, no git). */
class GitProfilesServiceTest {

    private val service = GitProfilesService()

    @Test
    fun `add stores profiles in order`() {
        assertTrue(service.add(GitProfile("Work User", "work@example.com")))
        assertTrue(service.add(GitProfile("Home User", "home@example.com")))

        assertEquals(
            listOf(GitProfile("Work User", "work@example.com"), GitProfile("Home User", "home@example.com")),
            service.profiles,
        )
    }

    @Test
    fun `add skips an already stored profile`() {
        service.add(GitProfile("Work User", "work@example.com"))

        assertFalse(service.add(GitProfile("Work User", "work@example.com")))

        assertEquals(1, service.profiles.size)
    }

    @Test
    fun `add stores a defensive copy`() {
        val original = GitProfile("Work User", "work@example.com")
        service.add(original)

        original.name = "Changed"

        assertEquals("Work User", service.profiles.single().name)
    }

    @Test
    fun `update replaces the matching profile`() {
        val old = GitProfile("Work User", "work@example.com")
        service.add(old)

        service.update(old, GitProfile("Home User", "home@example.com"))

        assertEquals(listOf(GitProfile("Home User", "home@example.com")), service.profiles)
    }

    @Test
    fun `update of an unknown profile does nothing`() {
        service.add(GitProfile("Work User", "work@example.com"))

        service.update(GitProfile("Missing", "missing@example.com"), GitProfile("Home User", "home@example.com"))

        assertEquals(listOf(GitProfile("Work User", "work@example.com")), service.profiles)
    }

    @Test
    fun `remove deletes the profile`() {
        service.add(GitProfile("Work User", "work@example.com"))
        service.add(GitProfile("Home User", "home@example.com"))

        service.remove(GitProfile("Work User", "work@example.com"))

        assertEquals(listOf(GitProfile("Home User", "home@example.com")), service.profiles)
    }

    @Test
    fun `loadState replaces stored profiles`() {
        service.add(GitProfile("Work User", "work@example.com"))

        service.loadState(GitProfilesService.State().apply {
            profiles = mutableListOf(GitProfilesService.StoredProfile("Home User", "home@example.com"))
        })

        assertEquals(listOf(GitProfile("Home User", "home@example.com")), service.profiles)
    }

    @Test
    fun `add records the origin it was discovered under`() {
        val profile = GitProfile("Work User", "work@example.com")

        service.add(profile, SOME_ORIGIN)

        assertEquals(SOME_ORIGIN, service.originOf(profile))
    }

    @Test
    fun `a profile added without an origin is the user's own`() {
        val profile = GitProfile("Work User", "work@example.com")

        service.add(profile)

        assertEquals(ProfileOrigin.NONE, service.originOf(profile))
    }

    @Test
    fun `an already stored profile picks up a discovered origin`() {
        val profile = GitProfile("Work User", "work@example.com")
        service.add(profile)

        assertFalse(service.add(profile, SOME_ORIGIN))

        assertEquals(SOME_ORIGIN, service.originOf(profile))
        assertEquals(1, service.profiles.size)
    }

    @Test
    fun `a recorded origin is not cleared by a later plain add`() {
        val profile = GitProfile("Work User", "work@example.com")
        service.add(profile, SOME_ORIGIN)

        service.add(profile)

        assertEquals(SOME_ORIGIN, service.originOf(profile))
    }

    @Test
    fun `an edited profile becomes the user's own`() {
        val old = GitProfile("Work User", "work@example.com")
        service.add(old, SOME_ORIGIN)

        service.update(old, GitProfile("Home User", "home@example.com"))

        assertEquals(ProfileOrigin.NONE, service.originOf(GitProfile("Home User", "home@example.com")))
    }

    @Test
    fun `an unknown profile has no recorded origin`() {
        assertEquals(ProfileOrigin.NONE, service.originOf(GitProfile("Missing", "missing@example.com")))
    }
}

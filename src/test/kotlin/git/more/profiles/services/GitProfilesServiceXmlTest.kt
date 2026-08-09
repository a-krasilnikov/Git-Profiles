package git.more.profiles.services

import com.intellij.openapi.util.JDOMUtil
import com.intellij.util.xmlb.XmlSerializer
import git.more.profiles.GitProfile
import git.more.profiles.ProfileOrigin
import org.intellij.lang.annotations.Language
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The shape the profile list had before profiles carried an origin. Serializing this produces
 * exactly what earlier versions wrote to disk, so it is an honest input for a compatibility check —
 * unlike XML typed out from memory.
 */
private class LegacyState {
    var profiles: MutableList<GitProfile> = mutableListOf()
}

/** Guards the on-disk format of git-profiles.xml. */
class GitProfilesServiceXmlTest {

    @Test
    fun `a state written by the previous version loads unchanged`() {
        val legacy = LegacyState().apply {
            profiles = mutableListOf(
                GitProfile("Work User", "work@example.com"),
                GitProfile("Home User", "home@example.com"),
            )
        }

        val service = GitProfilesService().apply {
            loadState(
                XmlSerializer.deserialize(XmlSerializer.serialize(legacy), GitProfilesService.State::class.java)
            )
        }

        assertEquals(
            listOf(GitProfile("Work User", "work@example.com"), GitProfile("Home User", "home@example.com")),
            service.profiles,
        )
        assertEquals(ProfileOrigin.NONE, service.originOf(GitProfile("Work User", "work@example.com")))
    }

    /** Pins what the legacy element actually looked like, so the hand-written fixture cannot drift. */
    @Test
    fun `the previous version wrote GitProfile elements`() {
        val legacy = LegacyState().apply { profiles = mutableListOf(GitProfile("Work User", "work@example.com")) }

        val written = JDOMUtil.write(XmlSerializer.serialize(legacy))

        assertEquals(
            """
            <LegacyState>
              <option name="profiles">
                <list>
                  <GitProfile>
                    <option name="email" value="work@example.com" />
                    <option name="name" value="Work User" />
                  </GitProfile>
                </list>
              </option>
            </LegacyState>
            """.trimIndent(),
            written,
        )
    }

    @Test
    fun `a stored profile survives a round trip`() {
        val state = GitProfilesService.State().apply {
            profiles = mutableListOf(
                GitProfilesService.StoredProfile("Work User", "work@example.com", "github"),
            )
        }

        val restored =
            XmlSerializer.deserialize(XmlSerializer.serialize(state), GitProfilesService.State::class.java)

        val profile = restored.profiles.single()
        assertEquals("Work User", profile.name)
        assertEquals("work@example.com", profile.email)
        assertEquals("github", profile.origin)
    }

    /**
     * Files written before profiles had an origin must keep loading, which is what `@Tag("GitProfile")`
     * on the stored class is for.
     */
    @Test
    fun `already stored profiles keep loading`() {
        @Language("XML")
        val legacy = """
            <State>
              <option name="profiles">
                <list>
                  <GitProfile>
                    <option name="email" value="work@example.com" />
                    <option name="name" value="Work User" />
                  </GitProfile>
                </list>
              </option>
            </State>
        """.trimIndent()

        val service = GitProfilesService().apply {
            loadState(XmlSerializer.deserialize(JDOMUtil.load(legacy), GitProfilesService.State::class.java))
        }

        val profile = GitProfile("Work User", "work@example.com")
        assertEquals(listOf(profile), service.profiles)
        assertEquals(ProfileOrigin.NONE, service.originOf(profile))
    }
}

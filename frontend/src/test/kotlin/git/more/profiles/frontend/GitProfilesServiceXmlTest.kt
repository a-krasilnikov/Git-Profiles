package git.more.profiles.frontend

import com.intellij.util.xmlb.XmlSerializer
import git.more.profiles.GitProfile
import org.intellij.lang.annotations.Language
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Guards the persistence format of [GitProfilesService].
 *
 * The store deliberately persists [GitProfilesService.StoredProfile] rather than the
 * `@Serializable` [GitProfile]: kotlinx serialization's synthetic members make IntelliJ's XML
 * serializer fail on list items with "Only root object is supported", which crashed the plugin
 * the moment the dialog was opened. These tests would catch a regression back to that state.
 */
class GitProfilesServiceXmlTest {

    @Test
    fun `state survives an XML serialize-deserialize round trip`() {
        val service = GitProfilesService()
        service.add(GitProfile("Work User", "work@example.com"))
        service.add(GitProfile("Home User", "home@example.com"))

        val element = XmlSerializer.serialize(service.state)
        val restored = XmlSerializer.deserialize(element, GitProfilesService.State::class.java)

        val target = GitProfilesService()
        target.loadState(restored)

        assertEquals(
            listOf(GitProfile("Work User", "work@example.com"), GitProfile("Home User", "home@example.com")),
            target.profiles,
        )
    }

    @Test
    fun `already stored profiles keep loading`() {
        // Exactly the shape written by earlier versions into config/options/git-profiles.xml —
        // the @Tag("GitProfile") element name must keep matching it.
        @Language("XML")
        val stored = """
            <State>
              <option name="profiles">
                <list>
                  <GitProfile>
                    <option name="email" value="alex-k@work.email" />
                    <option name="name" value="Alex K" />
                  </GitProfile>
                  <GitProfile>
                    <option name="email" value="alex-k@home.email" />
                    <option name="name" value="Alex K" />
                  </GitProfile>
                </list>
              </option>
            </State>
        """.trimIndent()

        val element = org.jdom.input.SAXBuilder().build(stored.reader()).rootElement
        val state = XmlSerializer.deserialize(element, GitProfilesService.State::class.java)

        val service = GitProfilesService()
        service.loadState(state)

        assertEquals(
            listOf(GitProfile("Alex K", "alex-k@work.email"), GitProfile("Alex K", "alex-k@home.email")),
            service.profiles,
        )
    }
}

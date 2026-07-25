package git.more.profiles.frontend

import com.intellij.openapi.components.*
import com.intellij.util.xmlb.annotations.Tag
import git.more.profiles.GitProfile

/**
 * The single persistent store of the plugin: the IDE-wide list of git profiles.
 *
 * In split mode this lives on the **frontend** (the JetBrains Client) as a client-side user
 * preference. The backend never reads it — it only receives fully-formed [GitProfile]s over RPC
 * and returns git-config data — so no cross-side synchronization is needed. Neither the global
 * identity nor per-repository assignments are persisted here: git config is the source of truth
 * for both (read on dialog open, written on the fly), so discovered identities are re-imported
 * every time the dialog opens and nothing is ever lost.
 *
 * Persistence uses [StoredProfile], a plain class, rather than [GitProfile] directly: `GitProfile`
 * is `@Serializable` (for RPC), and kotlinx serialization's synthetic members make IntelliJ's XML
 * serializer fail to deserialize it as a list item ("Only root object is supported"). `@Tag`
 * keeps the XML element name `<GitProfile>` so existing stored data keeps loading.
 */
@Service(Service.Level.APP)
@State(
    name = "GitProfiles",
    storages = [Storage("git-profiles.xml")],
    category = SettingsCategory.TOOLS,
)
class GitProfilesService : PersistentStateComponent<GitProfilesService.State> {

    @Tag("GitProfile")
    data class StoredProfile(var name: String = "", var email: String = "")

    class State {
        var profiles: MutableList<StoredProfile> = mutableListOf()
    }

    private var state = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }

    val profiles: List<GitProfile>
        get() = state.profiles.map { GitProfile(it.name, it.email) }

    /** Adds the profile unless an equal one is already stored. */
    fun add(profile: GitProfile): Boolean {
        val stored = profile.toStored()
        if (stored in state.profiles) return false
        state.profiles.add(stored)
        return true
    }

    fun update(old: GitProfile, new: GitProfile) {
        val index = state.profiles.indexOf(old.toStored())
        if (index >= 0) state.profiles[index] = new.toStored()
    }

    fun remove(profile: GitProfile) {
        state.profiles.remove(profile.toStored())
    }

    private fun GitProfile.toStored() = StoredProfile(name, email)

    companion object {
        @JvmStatic
        fun getInstance(): GitProfilesService = service()
    }
}

package git.more.profiles.services

import com.intellij.openapi.components.*
import git.more.profiles.GitProfile

/**
 * The single persistent store of the plugin: the IDE-wide list of git profiles.
 *
 * Neither the global identity nor per-repository assignments are persisted here —
 * git config is the source of truth for both (read on dialog open, written on the fly).
 */
@Service(Service.Level.APP)
@State(
    name = "GitProfiles",
    storages = [Storage("git-profiles.xml")],
    category = SettingsCategory.TOOLS,
)
class GitProfilesService : PersistentStateComponent<GitProfilesService.State> {

    class State {
        var profiles: MutableList<GitProfile> = mutableListOf()
    }

    private var state = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }

    val profiles: List<GitProfile>
        get() = state.profiles.toList()

    /** Adds a copy of the profile unless an equal one is already stored. */
    fun add(profile: GitProfile): Boolean {
        if (profile in state.profiles) return false
        state.profiles.add(profile.copy())
        return true
    }

    fun update(old: GitProfile, new: GitProfile) {
        val index = state.profiles.indexOf(old)
        if (index >= 0) state.profiles[index] = new
    }

    fun remove(profile: GitProfile) {
        state.profiles.remove(profile)
    }

    companion object {
        @JvmStatic
        fun getInstance(): GitProfilesService = service()
    }
}

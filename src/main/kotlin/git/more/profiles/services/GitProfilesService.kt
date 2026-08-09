package git.more.profiles.services

import com.intellij.openapi.components.*
import com.intellij.util.xmlb.annotations.Tag
import git.more.profiles.GitProfile
import git.more.profiles.ProfileOrigin

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

    /**
     * The persisted shape of a profile: the identity plus where it came from.
     *
     * Kept separate from [GitProfile] on purpose. [GitProfile] is compared by value throughout the
     * plugin — the global marker, the per-repository selector, deduplication — and an identity read
     * from git config knows nothing about an origin, so folding one in would break those comparisons.
     *
     * `@Tag("GitProfile")` preserves the element name of files written before the origin existed;
     * they load with [ProfileOrigin.NONE], which the serializer also omits again as the default.
     */
    @Tag("GitProfile")
    class StoredProfile(
        var name: String = "",
        var email: String = "",
        var origin: String = ProfileOrigin.NONE,
    ) {
        fun matches(profile: GitProfile): Boolean = name == profile.name && email == profile.email
    }

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

    /** [ProfileOrigin.NONE] for a profile the user typed, and for anything the store does not know. */
    fun originOf(profile: GitProfile): String =
        state.profiles.firstOrNull { it.matches(profile) }?.origin ?: ProfileOrigin.NONE

    /**
     * Stores the profile unless an equal one is already there, and returns whether it was added.
     *
     * An identity that is already stored keeps its place, but one that carries no origin picks up
     * [origin] — a profile typed by hand and later found on a hosting provider is worth marking as
     * coming from there.
     */
    fun add(profile: GitProfile, origin: String = ProfileOrigin.NONE): Boolean {
        val existing = state.profiles.firstOrNull { it.matches(profile) }
        if (existing != null) {
            if (existing.origin.isEmpty()) existing.origin = origin
            return false
        }
        state.profiles.add(StoredProfile(profile.name, profile.email, origin))
        return true
    }

    /** An edited profile is the user's own, wherever it was discovered. */
    fun update(old: GitProfile, new: GitProfile) {
        val index = state.profiles.indexOfFirst { it.matches(old) }
        if (index >= 0) state.profiles[index] = StoredProfile(new.name, new.email)
    }

    fun remove(profile: GitProfile) {
        state.profiles.removeIf { it.matches(profile) }
    }

    companion object {
        @JvmStatic
        fun getInstance(): GitProfilesService = service()
    }
}

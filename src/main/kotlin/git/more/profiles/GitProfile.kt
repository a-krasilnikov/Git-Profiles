package git.more.profiles

/**
 * A git identity: the pair of values used for `user.name` and `user.email`.
 *
 * Used both for the profiles stored by the plugin and for the values read from git config
 * (which may not correspond to any stored profile). Mutable properties with defaults are
 * required for XML serialization.
 */
data class GitProfile(var name: String = "", var email: String = "") {

    override fun toString(): String = "$name <$email>"
}

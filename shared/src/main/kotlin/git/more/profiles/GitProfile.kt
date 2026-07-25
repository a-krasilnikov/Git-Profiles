package git.more.profiles

import kotlinx.serialization.Serializable

/**
 * A git identity: the pair of values used for `user.name` and `user.email`.
 *
 * Used both for the profiles stored by the plugin and for the values read from git config
 * (which may not correspond to any stored profile). This single type crosses the RPC boundary
 * (`@Serializable`) and is the element persisted by the frontend store, so its mutable
 * properties with defaults satisfy both kotlinx.serialization and IntelliJ's XML serializer.
 */
@Serializable
data class GitProfile(var name: String = "", var email: String = "") {

    override fun toString(): String = "$name <$email>"
}

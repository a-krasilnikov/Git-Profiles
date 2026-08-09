package git.more.profiles.github

/**
 * Payloads of the two GitHub REST endpoints we read.
 *
 * The GitHub plugin wraps no email endpoint at all, so [GitHubEmailDto] has no counterpart there.
 * `/user` does have one — `GithubAuthenticatedUser` — but it exposes only getters, so it cannot be
 * built with values in a test; keeping our own type is what lets [buildGitHubProfiles] stay a pure
 * function over plain data.
 *
 * Mutable properties with defaults give Jackson the no-arg constructor it binds through, mirroring
 * the plugin's own data classes. Every field is a single word, so the mapper's snake_case naming
 * strategy does not come into play.
 */
internal class GitHubUserDto {
    var login: String = ""
    var name: String? = null
    var email: String? = null
}

internal class GitHubEmailDto {
    var email: String = ""
    var primary: Boolean = false
    var verified: Boolean = false
}

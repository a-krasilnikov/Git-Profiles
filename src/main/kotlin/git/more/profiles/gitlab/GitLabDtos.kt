package git.more.profiles.gitlab

/**
 * The payload of `GET /api/v4/user`.
 *
 * The plugin's own `GitLabUserRestDTO` is not enough: it carries id, username, name and two URLs,
 * but no email at all.
 *
 * Mutable properties with defaults give Jackson the no-arg constructor it binds fields through,
 * mirroring `GitHubDtos`. Unlike the GitHub mapper, this one applies `SNAKE_CASE`, so [publicEmail]
 * and [commitEmail] bind `public_email` and `commit_email` without any annotation.
 */
internal class GitLabUserDto {
    var username: String = ""
    var name: String? = null

    /** The account's primary address. */
    var email: String? = null

    /** The address the user chose to publish; GitLab returns `""`, not null, when unset. */
    var publicEmail: String? = null

    /**
     * What GitLab itself commits with. Rendered from `User#commit_email_or_default`, so it is never
     * blank: the chosen commit address, else the generated noreply one, else the primary address.
     */
    var commitEmail: String? = null
}

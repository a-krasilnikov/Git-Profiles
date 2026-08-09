package git.more.profiles.github

import git.more.profiles.GitProfile

/**
 * Turns one GitHub account's API responses into candidate profiles — every address GitHub reported,
 * most likely first. Nothing is synthesized: an address the API did not name is not offered.
 *
 * Split out from [GitHubProfileProvider] because this is the part worth testing: the
 * surrounding code is authentication and network.
 */
internal fun buildGitHubProfiles(user: GitHubUserDto, emails: List<GitHubEmailDto>): List<GitProfile> {
    val login = user.login.trim()
    val displayName = user.name?.trim()?.ifEmpty { null } ?: login
    if (displayName.isEmpty()) return emptyList()

    // A LinkedHashSet keeps the preference order while collapsing an address that shows up twice —
    // the public profile email is usually also the primary one.
    val candidates = LinkedHashSet<String>()

    // Unverified addresses are not usable as a commit identity, so they never become profiles.
    emails.filter { it.verified }
        .sortedByDescending { it.primary }
        .mapTo(candidates) { it.email.trim() }

    user.email?.trim()?.let(candidates::add)

    candidates.remove("")
    return candidates.map { GitProfile(displayName, it) }
}

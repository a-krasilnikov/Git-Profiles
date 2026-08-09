package git.more.profiles.gitlab

import git.more.profiles.GitProfile

/**
 * Turns one GitLab account's `/user` response into candidate profiles — every address GitLab
 * reported, most likely first. Nothing is synthesized: an address the API did not name is not
 * offered.
 *
 * Split out from [GitLabProfileProvider] because this is the part worth testing: the surrounding
 * code is authentication and network.
 */
internal fun buildGitLabProfiles(user: GitLabUserDto): List<GitProfile> {
    val username = user.username.trim()
    val displayName = user.name?.trim()?.ifEmpty { null } ?: username
    if (displayName.isEmpty()) return emptyList()

    // A LinkedHashSet keeps the preference order while collapsing an address that shows up twice —
    // commit_email falls back to the primary address, so the two often coincide.
    val candidates = LinkedHashSet<String>()

    // First, because GitLab resolves it to whatever the account actually commits with.
    user.commitEmail?.trim()?.let(candidates::add)
    user.publicEmail?.trim()?.let(candidates::add)
    user.email?.trim()?.let(candidates::add)

    candidates.remove("")
    return candidates.map { GitProfile(displayName, it) }
}

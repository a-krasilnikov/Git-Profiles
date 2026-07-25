package git.more.profiles.rpc

import git.more.profiles.GitProfile
import kotlinx.serialization.Serializable

/**
 * A git repository as seen across the RPC boundary.
 *
 * @property id opaque identifier — the repository root path on the backend. The frontend never
 *   interprets it as a local path; it only echoes it back to address the same repository.
 * @property name display name shown in the UI (the repository root directory name).
 */
@Serializable
data class RepoDto(val id: String, val name: String)

/** A repository together with its *own* (local) identity, or `null` when it inherits the global one. */
@Serializable
data class RepoAssignmentDto(val repo: RepoDto, val local: GitProfile?)

/**
 * The whole git-config picture read on the backend in one shot: the global identity plus each
 * repository's local identity. The frontend derives the effective identity (`local ?: global`).
 */
@Serializable
data class GitConfigSnapshotDto(
    val global: GitProfile?,
    val repositories: List<RepoAssignmentDto>,
)

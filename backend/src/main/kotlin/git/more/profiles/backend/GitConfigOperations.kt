package git.more.profiles.backend

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsException
import git.more.profiles.GitProfile
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitCommandResult
import git4idea.commands.GitLineHandler
import git4idea.repo.GitRepository
import java.io.File

private const val USER_NAME = "user.name"
private const val USER_EMAIL = "user.email"

private const val LOCAL = "--local"
private const val GLOBAL = "--global"

/**
 * Reads and writes `user.name` / `user.email` through git4idea.
 * Every function runs a `git config` process and must be called on a background thread.
 *
 * Backend-only: in split mode this runs on the host, where the repositories and git config live.
 */
object GitConfigOperations {

    fun readGlobalProfile(project: Project): GitProfile? =
        readProfile(project, globalDirectory(project), GLOBAL)

    /** The identity git resolves for the repository: its own, or the global one it inherits. */
    fun readProfile(repository: GitRepository): GitProfile? =
        readProfile(repository.project, repositoryRoot(repository), scope = null)

    /**
     * Only the repository's own identity, so callers can tell an explicit override from an
     * inherited global one — [readProfile] cannot, as git resolves the fallback itself.
     */
    fun readLocalProfile(repository: GitRepository): GitProfile? =
        readProfile(repository.project, repositoryRoot(repository), LOCAL)

    @Throws(VcsException::class)
    fun setGlobalProfile(project: Project, profile: GitProfile) {
        setProfile(project, globalDirectory(project), GLOBAL, profile)
    }

    @Throws(VcsException::class)
    fun setProfile(repository: GitRepository, profile: GitProfile) {
        setProfile(repository.project, repositoryRoot(repository), LOCAL, profile)
    }

    /** Removes the local identity so the repository falls back to the global one. */
    fun unsetLocalProfile(repository: GitRepository) {
        val root = repositoryRoot(repository)
        // A non-zero exit code here just means the key was not set; safe to ignore.
        run(repository.project, root, LOCAL, "--unset", USER_NAME)
        run(repository.project, root, LOCAL, "--unset", USER_EMAIL)
    }

    /** A `null` scope leaves the lookup to git, which applies local over global. */
    private fun readProfile(project: Project, directory: File, scope: String?): GitProfile? {
        val name = readValue(project, directory, scope, USER_NAME)
        val email = readValue(project, directory, scope, USER_EMAIL)
        if (name == null && email == null) return null
        return GitProfile(name.orEmpty(), email.orEmpty())
    }

    private fun setProfile(project: Project, directory: File, scope: String, profile: GitProfile) {
        setValue(project, directory, scope, USER_NAME, profile.name)
        setValue(project, directory, scope, USER_EMAIL, profile.email)
    }

    private fun readValue(project: Project, directory: File, scope: String?, key: String): String? {
        val parameters = listOfNotNull(scope, "--get", key).toTypedArray()
        val result = run(project, directory, *parameters)
        if (!result.success()) return null

        return result.output.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun setValue(project: Project, directory: File, scope: String, key: String, value: String) {
        val result = run(project, directory, scope, key, value)
        if (result.success()) return

        throw VcsException(result.errorOutputAsJoinedString.ifBlank { "git config $key failed" })
    }

    private fun run(project: Project, directory: File, vararg parameters: String): GitCommandResult {
        val handler = GitLineHandler(project, directory, GitCommand.CONFIG)
        handler.setSilent(true)
        handler.addParameters(*parameters)
        return Git.getInstance().runCommand(handler)
    }

    private fun repositoryRoot(repository: GitRepository): File = File(repository.root.path)

    /**
     * Global config does not depend on the working directory, but the spawned git process
     * needs an existing one: prefer the project base directory, fall back to the user home
     * (always present) when it is missing or not materialized on disk.
     */
    private fun globalDirectory(project: Project): File {
        val baseDirectory = project.basePath?.let(::File)
        if (baseDirectory != null && baseDirectory.isDirectory) return baseDirectory
        return File(System.getProperty("user.home"))
    }
}

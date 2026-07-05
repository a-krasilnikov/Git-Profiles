package git.more.profiles.services

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.replaceService
import com.intellij.vcs.test.VcsPlatformTest
import git.more.profiles.GitProfile
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitCommandResult
import git4idea.commands.GitLineHandler
import git4idea.commands.GitLineHandlerListener
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryImpl
import git4idea.reset.GitResetMode
import java.io.File
import java.nio.file.Files

/**
 * Integration tests for [GitConfigOperations] against real [GitRepository] instances and a
 * real `git` executable, on the platform VCS test fixture ([VcsPlatformTest]).
 *
 * The global scope is isolated from the developer machine: the [Git] application service is
 * replaced with a decorator that gives every git process `GIT_CONFIG_GLOBAL` pointing to a
 * per-test temp file and `GIT_CONFIG_NOSYSTEM`, so `--global` reads and writes never touch
 * the real `~/.gitconfig` or the system config.
 */
class GitConfigOperationsTest : VcsPlatformTest() {

    // Git commands must not run on the EDT.
    override fun runInDispatchThread(): Boolean = false

    override fun setUp() {
        super.setUp()
        val globalConfig = File(FileUtil.createTempDirectory("git-profiles-global", null), ".gitconfig")
        val environment = mapOf(
            "GIT_CONFIG_GLOBAL" to globalConfig.absolutePath,
            "GIT_CONFIG_NOSYSTEM" to "true",
        )
        ApplicationManager.getApplication().replaceService(
            Git::class.java,
            EnvCustomizingGit(Git.getInstance(), environment),
            testRootDisposable,
        )
    }

    fun `test basic scenario - set, read and unset a local profile`() {
        val repo = createRepository("repo")

        assertNull(GitConfigOperations.readLocalProfile(repo))

        GitConfigOperations.setLocalProfile(repo, WORK)
        assertEquals(WORK, GitConfigOperations.readLocalProfile(repo))

        GitConfigOperations.unsetLocalProfile(repo)
        assertNull(GitConfigOperations.readLocalProfile(repo))
    }

    fun `test global scenario - set and overwrite the global profile`() {
        assertNull(GitConfigOperations.readGlobalProfile(project))

        GitConfigOperations.setGlobalProfile(project, WORK)
        assertEquals(WORK, GitConfigOperations.readGlobalProfile(project))

        GitConfigOperations.setGlobalProfile(project, HOME)
        assertEquals(HOME, GitConfigOperations.readGlobalProfile(project))
    }

    fun `test global scenario - global and local scopes stay independent`() {
        val repo = createRepository("repo")

        GitConfigOperations.setGlobalProfile(project, WORK)
        assertNull("global values must not appear in the local scope", GitConfigOperations.readLocalProfile(repo))

        GitConfigOperations.setLocalProfile(repo, HOME)
        assertEquals(HOME, GitConfigOperations.readLocalProfile(repo))
        assertEquals(WORK, GitConfigOperations.readGlobalProfile(project))

        GitConfigOperations.unsetLocalProfile(repo)
        assertNull(GitConfigOperations.readLocalProfile(repo))
        assertEquals(WORK, GitConfigOperations.readGlobalProfile(project))
    }

    fun `test multi repo scenario - repositories are independent`() {
        val first = createRepository("first")
        val second = createRepository("second")

        GitConfigOperations.setLocalProfile(first, WORK)
        GitConfigOperations.setLocalProfile(second, HOME)

        assertEquals(WORK, GitConfigOperations.readLocalProfile(first))
        assertEquals(HOME, GitConfigOperations.readLocalProfile(second))

        GitConfigOperations.unsetLocalProfile(first)
        assertNull(GitConfigOperations.readLocalProfile(first))
        assertEquals(HOME, GitConfigOperations.readLocalProfile(second))
    }

    fun `test repo root different from project root`() {
        // The repository lives outside the project base directory.
        val repo = createRepository("detached", parent = FileUtil.createTempDirectory("outside-project", null))
        assertFalse("precondition: repo root must differ from project root", repo.root.path == project.basePath)

        GitConfigOperations.setLocalProfile(repo, WORK)
        assertEquals(WORK, GitConfigOperations.readLocalProfile(repo))

        // Global operations use the project base path as working directory and stay separate.
        GitConfigOperations.setGlobalProfile(project, HOME)
        assertEquals(HOME, GitConfigOperations.readGlobalProfile(project))
        assertEquals(WORK, GitConfigOperations.readLocalProfile(repo))
    }

    private fun createRepository(name: String, parent: File = testNioRoot.toFile()): GitRepository {
        val directory = File(parent, name)
        Files.createDirectories(directory.toPath())

        val handler = GitLineHandler(project, directory, GitCommand.INIT)
        val result = Git.getInstance().runCommand(handler)
        assertTrue(result.errorOutputAsJoinedString, result.success())

        val root = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(directory.toPath())!!
        return GitRepositoryImpl.createInstance(root, project, testRootDisposable)
    }

    /** Adds environment variables to every git process, isolating the global config scope. */
    private class EnvCustomizingGit(
        private val delegate: Git,
        private val environment: Map<String, String>,
    ) : Git by delegate {

        override fun runCommand(handlerConstructor: Computable<out GitLineHandler>): GitCommandResult =
            delegate.runCommand(Computable { handlerConstructor.compute().also(::customize) })

        override fun runCommand(handler: GitLineHandler): GitCommandResult =
            delegate.runCommand(handler.also(::customize))

        override fun runCommandWithoutCollectingOutput(handler: GitLineHandler): GitCommandResult =
            delegate.runCommandWithoutCollectingOutput(handler.also(::customize))

        override fun clone(
            project: Project?,
            parentDirectory: File,
            url: String,
            clonedDirectoryName: String,
            vararg progressListeners: GitLineHandlerListener?
        ): GitCommandResult {
            return delegate.clone(project, parentDirectory, url, clonedDirectoryName, *progressListeners)
        }

        override fun reset(
            repository: GitRepository,
            mode: GitResetMode,
            target: String,
            vararg listeners: GitLineHandlerListener?
        ): GitCommandResult {
            return delegate.reset(repository, mode, target, *listeners)
        }

        override fun getResolvedFiles(repository: GitRepository): GitCommandResult {
            return delegate.getResolvedFiles(repository)
        }

        private fun customize(handler: GitLineHandler) {
            environment.forEach { (name, value) -> handler.addCustomEnvironmentVariable(name, value) }
        }
    }

    private companion object {
        val WORK = GitProfile("Work User", "work@example.com")
        val HOME = GitProfile("Home User", "home@example.com")
    }
}

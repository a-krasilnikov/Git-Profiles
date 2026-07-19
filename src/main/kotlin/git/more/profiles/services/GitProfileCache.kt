package git.more.profiles.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import git.more.profiles.GitProfile
import git4idea.repo.GitConfigListener
import git4idea.repo.GitRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

@Service(Service.Level.PROJECT)
class GitProfileCache(project: Project, private val scope: CoroutineScope) {

    private val cache = ConcurrentHashMap<String, GitProfile>()

    /** Roots with a load already in flight, so repeated reads spawn one git process. */
    private val loading = ConcurrentHashMap.newKeySet<String>()

    init {
        project.messageBus.connect(scope).subscribe(
            GitConfigListener.TOPIC,
            object : GitConfigListener {
                override fun notifyConfigChanged(repository: GitRepository) {
                    cache.remove(repository.root.path)
                }
            },
        )
    }

    /**
     * The profile in effect in the repository, or `null` while it is not known yet — in which
     * case a background load is scheduled and the value shows up on a later call.
     */
    fun getProfile(repository: GitRepository): GitProfile? {
        val profile = cache[repository.root.path]
        if (profile == null) scheduleLoading(repository)
        return profile
    }

    fun clear() {
        cache.clear()
    }

    private fun scheduleLoading(repository: GitRepository) {
        val root = repository.root.path
        if (!loading.add(root)) return

        scope.launch(Dispatchers.IO) {
            try {
                GitConfigOperations.readProfile(repository)?.let { cache[root] = it }
            } finally {
                loading.remove(root)
            }
        }
    }

    companion object {
        @JvmStatic
        fun getInstance(project: Project): GitProfileCache = project.service()
    }
}

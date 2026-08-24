package git.more.profiles

/**
 * Where a stored profile came from: the id of the
 * [provider][git.more.profiles.providers.GitProfileProvider] that found it, or [NONE].
 *
 * An open id rather than a closed set on purpose — each provider declares its own, so a new one can
 * be added without changing the storage format or migrating what users already have on disk.
 */
object ProfileOrigin {

    /**
     * No provider claims the profile: the user typed it, or it was merely discovered in git config.
     * That a profile is also in git config is not worth pointing out.
     */
    const val NONE: String = ""
}

# CLAUDE.md

Guidance for Claude Code when working in this repository.

## What this project is

**Git Profiles** — a plugin for IntelliJ IDEA and other JetBrains IDEs that manages
*git identity profiles*. A **profile** is a named-by-value pair of `user.name` and
`user.email` used as git config values.

The plugin lets the user:

1. Open a simple modal dialog from an action.
2. Store git profiles and view them in a table.
3. Add / remove / edit profiles.
4. Set exactly one profile as the **global** identity (`git config --global user.name/user.email`).
5. Assign a profile **per git repository** in the current project (each repo independently),
   applied as local config (`git config --local user.name/user.email`).
6. Have identities imported automatically — those already in git config, and those of the
   GitHub and GitLab accounts the IDE is signed in to.

See [docs/DESIGN.md](docs/DESIGN.md) for the full requirements and architecture.

## Tech stack

- **Language:** Kotlin (JVM). Build: Gradle Kotlin DSL.
- **Platform:** IntelliJ Platform Gradle Plugin 2.x. Target IDE: IntelliJ IDEA (`intellijIdea("2025.3.5")`).
- **Git integration:** the bundled **git4idea** plugin (repo detection + running `git config`).
- **Hosting integrations:** the bundled **GitHub** (`org.jetbrains.plugins.github`) and **GitLab**
  (`org.jetbrains.plugins.gitlab`) plugins, as **optional** dependencies — everything else works
  when they are disabled.
- **Min compatibility / build config:** `build.gradle.kts`, `gradle.properties`, `settings.gradle.kts`.
- Plugin id / group: `git.more.profiles.git-profiles` (base package `git.more.profiles`).

## Key decisions (settled with the user)

- **Multi-repo:** per-repository assignment. When a project has several git repos, each repo
  gets its own profile choice; there is no single project-wide profile.
- **Apply mechanism:** use **git4idea**, not raw CLI or hand-written config parsing. Add
  `<depends>Git4Idea</depends>` in plugin.xml and `bundledPlugin("Git4Idea")` in the build.
- **Storage scope:** the **profile list is IDE-wide** (application-level persistent state,
  shared across all projects) and is the *only* persisted state. Per-repo assignment is **not**
  stored — it is applied on the fly to git config and re-read from git config when the dialog
  opens (matched against stored profiles to show the active one).
- **Profile fields:** only `user.name` and `user.email`. No separate display label.
- **Global truth comes from git, not just a stored flag:** derive "which profile is global" by
  reading the actual global git config on open, and matching it against stored profiles.
- **Profile origin:** a stored profile records the id of the provider that imported it, so the
  table can badge it with that provider's icon. An open string (`""` = the user's own), not an
  enum, so a new provider needs no storage change. It lives on the persisted `StoredProfile`,
  never on `GitProfile` — that one is compared by value all over the plugin, and an identity read
  from git config knows nothing about an origin.
- **Provider extension point:** `git.more.profiles.git-profiles.profileProvider`. A provider
  declares its persisted id, display name, badge icon and how to read identities; one
  implementation per hosting plugin, registered from that plugin's optional config-file.
- **Import is not project-scoped:** the profile list is IDE-wide, so every signed-in account
  contributes regardless of where the open project is hosted.
- **Nothing is synthesized:** a provider offers only addresses its API actually named. Noreply
  addresses are not constructed — though one the API reports itself is kept like any other.

## Project structure

```
src/main/kotlin/git/more/profiles/            # plugin source (base package)
src/main/resources/META-INF/plugin.xml        # plugin descriptor (actions, deps, extension point)
src/main/resources/META-INF/git-profiles-github.xml  # loaded only with the GitHub plugin
src/main/resources/META-INF/git-profiles-gitlab.xml  # loaded only with the GitLab plugin
src/main/resources/messages/                  # i18n resource bundle
docs/DESIGN.md                                # requirements + architecture
```

Package layout under `git.more.profiles`:
- root — `GitProfile` (model), `ProfileOrigin`, `GitProfilesBundle` (i18n)
- `actions` — `OpenGitProfilesAction` (entry point: Git menu, commit toolbar)
- `services` — `GitProfilesService` (app-level persistence), `GitConfigOperations`
  (git4idea config reads/writes), `GitProfileCache` (toolbar tooltip)
- `ui` — `GitProfilesDialog` (main dialog), `ProfileEditorDialog` (add/edit)
- `providers` — `GitProfileProvider` (the extension point and the funnel that queries every
  provider at once), `AccountProfileProvider` (base class: per-account timeout and error
  isolation)
- `github` / `gitlab` — one provider each, plus its REST DTOs and a pure `build…Profiles`
  function that turns an API response into candidate profiles

## Conventions

- Keep all base classes under the `git.more.profiles` package.
- User-facing strings go through the message bundle, not hard-coded.
- Prefer IntelliJ Platform UI helpers (`DialogWrapper`, `JBTable`, `ToolbarDecorator`,
  `com.intellij.ui.dsl` UI DSL) over raw Swing where practical.
- Persistent state via `PersistentStateComponent` + `@State`/`@Storage` — a single
  application-level service holding the profile list. No project-level state; per-repo
  assignment is read from and written to git config directly.
- Run git operations through git4idea off the EDT; never block the UI thread on git.
- A hosting plugin's classes may be referenced **only** from that provider's own class, the one
  the platform instantiates through the optional config-file. Everything loaded unconditionally —
  `AccountProfileProvider` included — must stay free of them, `com.intellij.collaboration.*`
  among them.
- Keep the `build…Profiles` functions pure and free of plugin types: they are the part worth
  unit-testing, and the surrounding provider code (auth, network) is not testable at all.

## Common commands

```bash
./gradlew build            # compile + tests (verifyPlugin is NOT part of it)
./gradlew runIde           # launch a sandbox IDE with the plugin installed
./gradlew test             # run tests
./gradlew verifyPlugin     # IntelliJ Plugin Verifier
./gradlew buildPlugin      # produce distributable zip in build/distributions
```

On Windows use `gradlew.bat` (or `./gradlew` via the Bash tool).

## Notes / gotchas

- `git config --global` affects the whole machine (`~/.gitconfig`), not just the IDE — this is
  intended for requirement #4.
- Git precedence is local > global, so a repo with an assigned profile naturally overrides the
  global one. No special handling needed.
- git4idea repo detection: `GitRepositoryManager.getInstance(project).repositories`.
- Disabling the GitHub or GitLab plugin must leave everything else working. Check it on the built
  jar rather than by eye: scan every class's constant pool for `org/jetbrains/plugins/github`,
  `org/jetbrains/plugins/gitlab` and `com/intellij/collaboration` — each must appear only in its
  own provider class and that class's lambdas.
- If per-project scoping is ever reintroduced, do **not** ask `GHHostedRepositoriesManager` or
  `GitLabProjectsManager` whether a repo is hosted somewhere: both publish a StateFlow that starts
  empty and fills in asynchronously — the GitLab one behind HTTP probes — so a read taken soon
  after startup silently answers no. Match remote hosts with `GitHostingUrlUtil.matchHost` instead.
- Do not commit the `.intellijPlatform/sandbox/` directory or other build artifacts.

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

See [docs/DESIGN.md](docs/DESIGN.md) for the full requirements and architecture.

## Tech stack

- **Language:** Kotlin (JVM). Build: Gradle Kotlin DSL.
- **Platform:** IntelliJ Platform Gradle Plugin 2.x. Target IDE: IntelliJ IDEA (`intellijIdea("2025.3.5")`).
- **Git integration:** the bundled **git4idea** plugin (repo detection + running `git config`).
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

## Project structure

```
src/main/kotlin/git/more/profiles/     # plugin source (base package)
src/main/resources/META-INF/plugin.xml # plugin descriptor (actions, deps, extensions)
src/main/resources/messages/           # i18n resource bundle
docs/DESIGN.md                         # requirements + architecture
```

Package layout under `git.more.profiles`:
- root — `GitProfile` (model), `GitProfilesBundle` (i18n)
- `actions` — `OpenGitProfilesAction` (entry point: Git + Tools menus, commit toolbar)
- `services` — `GitProfilesService` (app-level persistence), `GitConfigOperations`
  (git4idea config reads/writes)
- `ui` — `GitProfilesDialog` (main dialog), `ProfileEditorDialog` (add/edit)

## Conventions

- Keep all base classes under the `git.more.profiles` package.
- User-facing strings go through the message bundle, not hard-coded.
- Prefer IntelliJ Platform UI helpers (`DialogWrapper`, `JBTable`, `ToolbarDecorator`,
  `com.intellij.ui.dsl` UI DSL) over raw Swing where practical.
- Persistent state via `PersistentStateComponent` + `@State`/`@Storage` — a single
  application-level service holding the profile list. No project-level state; per-repo
  assignment is read from and written to git config directly.
- Run git operations through git4idea off the EDT; never block the UI thread on git.

## Common commands

```bash
./gradlew build            # compile + tests + verify plugin
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
- Do not commit the `.intellijPlatform/sandbox/` directory or other build artifacts.

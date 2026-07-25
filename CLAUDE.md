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

- **Language:** Kotlin (JVM). Build: Gradle Kotlin DSL, multi-module.
- **Platform:** IntelliJ Platform Gradle Plugin 2.x. Target IDE: IntelliJ IDEA (`intellijIdea("2025.3.5")`).
- **Remote development / split mode:** the plugin is a **modular (split) plugin** — one `shared`
  content module plus `frontend` and `backend` content modules. The UI runs on the frontend
  (JetBrains Client / thin client); git operations run on the backend (host); they communicate
  over **RPC** (`@Rpc` interface in `shared`, fleet.rpc). It still runs as a normal monolithic IDE.
- **RPC toolchain:** the `rpc` Gradle plugin is a Kotlin *compiler* plugin, so its version is
  pinned to the Kotlin version. This project uses Kotlin `2.3.20` + `rpc` `2.3.20-RC2-0.1`
  (from `packages.jetbrains.team/.../intellij-dependencies`), verified to compile and generate
  RPC stubs against `2025.3.5`. Bumping Kotlin means finding a matching `rpc` plugin version.
- **Git integration:** the bundled **git4idea** plugin (repo detection + running `git config`),
  used **only in the `backend` module**.
- **Build config:** root `build.gradle.kts` / `settings.gradle.kts` / `gradle.properties`, plus a
  `build.gradle.kts` per module. Serialization/RPC versions in `gradle/libs.versions.toml`.
- Plugin id / group: `git.more.profiles.git-profiles` (base package `git.more.profiles`).

## Key decisions (settled with the user)

- **Multi-repo:** per-repository assignment. When a project has several git repos, each repo
  gets its own profile choice; there is no single project-wide profile.
- **Apply mechanism:** use **git4idea**, not raw CLI or hand-written config parsing.
  git4idea logic lives only in the `backend` module (`bundledPlugin("Git4Idea")` + `<plugin id="Git4Idea"/>`
  in the backend descriptor). The `frontend` module must **not** hard-depend on Git4Idea: the
  JetBrains Client (thin frontend) does not load Git4Idea, so a `<plugin id="Git4Idea"/>` dependency
  would disable the whole frontend module (and its UI) there. The frontend never calls
  `GitRepositoryManager` or any git4idea API anyway.
- **Menus are host-driven; there are therefore TWO actions.** The single most surprising thing about
  this plugin: in split mode the client's **menu bar is built from the backend's action model**, so
  an action registered in a frontend module never appears in it — not in the Git menu, not even in
  Tools. (Evidence: no frontend module in the whole IDE distribution adds to a main-menu group, and
  our frontend `ToolsMenu` anchor registered *without error* yet rendered nothing on the client.)
  Hence the hybrid split:
  - `GitProfiles.OpenFromMenu` — **backend** module, anchored in `ToolsMenu` + `Git.MainMenu` +
    `ChangesView.CommitToolbar`. Shows no UI itself: it `broadcast`s
    `OPEN_GIT_PROFILES_DIALOG_TOPIC` (a `ProjectRemoteTopic` declared in `shared`), which the
    frontend's `OpenGitProfilesDialogListener` receives and turns into the dialog. In a monolith the
    hand-off is a no-op detour.
    **Use a remote topic, not an RPC `Flow`, for backend→frontend pushes that must always arrive.**
    A `Flow` the frontend has to collect only works if something already instantiated the collecting
    service — with lazy services the very first menu invocation silently does nothing (this exact bug
    happened). Remote-topic listeners are registered declaratively via
    `platform.rpc.projectRemoteTopicListener` and wired up by a preloaded platform service, so
    delivery never depends on our own services having been touched. The platform uses the same
    pattern for `ShowBreakpointDialogRemoteTopic`.
    **Broadcasting requires the sending module to depend on `intellij.platform.rpc.topics.backend`**
    (the receiving one on `intellij.platform.rpc.topics.frontend`). `intellij.platform.rpc.backend`
    is a *different* module and is not enough. Miss it and `broadcast()` still compiles, still runs,
    and silently delivers nothing — the menu item just does nothing. Both sides log at INFO
    (`grep "Git Profiles:"` across the host and client logs) precisely because this failure is
    otherwise invisible.
  There is deliberately **no** frontend-registered action: it could not appear in any menu, and in a
  monolith it would just duplicate the backend entry in Search Everywhere.
- **Which action groups exist where** (verified against 2025.3.5, worth re-checking on upgrades):
  `Git.MainMenu` is declared in Git4Idea's *main* descriptor (`intellij.vcs.git.xml`) and
  `ChangesView.CommitToolbar` in platform VCS impl — **neither exists on the thin client**, which is
  the other reason those anchors belong on the backend. Anchoring a group whose owner you do not
  depend on raises a SEVERE "group with id … isn't registered" — it is **not** silently ignored,
  which is why the backend module declares `<plugin id="Git4Idea"/>`.
- **Frontend↔backend split:** all git reads/writes and repository enumeration happen on the backend
  via `GitProfilesRpcApi` (see `GitProfilesBackendService`). The frontend UI holds no git logic; it
  gets one `GitConfigSnapshotDto` (global + each repo's local identity) and computes the effective
  identity (`local ?: global`) itself. A repository is addressed across RPC by its **root path string**.
- **Storage scope:** the **profile list is IDE-wide** (application-level persistent state) and is the
  *only* persisted state. It lives **frontend-only** (client-side user preference): the backend never
  reads it — it just receives fully-formed `GitProfile`s over RPC — so no cross-side sync is needed.
  Per-repo assignment and the global identity are **not** stored; git config is the source of truth,
  read on dialog open and written on the fly. Discovered identities are re-imported every open, so
  nothing is lost.
- **Profile fields:** only `user.name` and `user.email`. No separate display label. `GitProfile` is
  `@Serializable` and is the RPC payload. It is **not** what gets persisted: kotlinx serialization's
  synthetic members make IntelliJ's XML serializer fail on list items ("Only root object is
  supported"), so the store persists `GitProfilesService.StoredProfile` with `@Tag("GitProfile")` to
  keep the on-disk format. `GitProfilesServiceXmlTest` guards this.
- **Global truth comes from git, not just a stored flag:** derive "which profile is global" by
  reading the actual global git config on open (in the snapshot), and matching it against stored profiles.

## Project structure

```
src/main/resources/META-INF/plugin.xml     # root descriptor: id, <content> modules, since-build
shared/    src/main/resources/git-profiles.shared.xml
frontend/  src/main/resources/git-profiles.frontend.xml
backend/   src/main/resources/git-profiles.backend.xml
docs/DESIGN.md                             # requirements + architecture
docs/REMOTE-DEV-TESTING.md                 # manual split-mode / remote-dev test plan
```

Everything is under the `git.more.profiles` base package:

- **shared** — `GitProfile` (model, `@Serializable`), `rpc/GitProfilesRpcApi` (the `@Rpc`
  contract), `rpc/Dtos` (`RepoDto`, `RepoAssignmentDto`, `GitConfigSnapshotDto`),
  `rpc/GitProfilesTopics` (the backend→frontend remote topic).
- **backend** (`.backend`) — `GitConfigOperations` (git4idea reads/writes),
  `GitProfilesBackendService` (repo enumeration + snapshot), `GitProfilesRpcApiImpl` +
  `BackendRpcApiProvider`, `OpenGitProfilesMenuAction` (the entry point).
- **frontend** (`.frontend`) — `GitProfilesService` (app-level persistence),
  `GitProfilesFrontendModel` (RPC client), `GitProfilesDialogOpener`,
  `OpenGitProfilesDialogListener` (receives the topic), `ui/GitProfilesDialog`,
  `ui/ProfileEditorDialog`, `GitProfilesBundle`.

## Conventions

- Keep all base classes under the `git.more.profiles` package.
- User-facing strings go through the message bundle, not hard-coded.
- Prefer IntelliJ Platform UI helpers (`DialogWrapper`, `JBTable`, `ToolbarDecorator`,
  `com.intellij.ui.dsl` UI DSL) over raw Swing where practical.
- Persistent state via `PersistentStateComponent` + `@State`/`@Storage` — a single
  application-level service holding the profile list. No project-level state; per-repo
  assignment is read from and written to git config directly.
- Run git operations through git4idea off the EDT; never block the UI thread on git.
- RPC calls from the frontend go through `GitProfilesFrontendModel` and are wrapped in `durable`,
  which retries across a dropped or not-yet-ready host connection. Keep the operations idempotent.

## Common commands

```bash
./gradlew build            # compile + tests (NOTE: does not run verifyPlugin)
./gradlew runIde           # launch a monolithic sandbox IDE with the plugin installed
./gradlew test             # run tests
./gradlew verifyPlugin     # IntelliJ Plugin Verifier — separate task, run before releasing
./gradlew buildPlugin      # produce distributable zip in build/distributions
```

On Windows use `gradlew.bat` (or `./gradlew` via the Bash tool). Gradle needs JDK 21 —
`JAVA_HOME=C:\Users\user\.gradle\jdks\eclipse_adoptium-21-amd64-windows.2` works.

## Notes / gotchas

- `git config --global` affects the whole machine (`~/.gitconfig`), not just the IDE — this is
  intended for requirement #4.
- Git precedence is local > global, so a repo with an assigned profile naturally overrides the
  global one. No special handling needed.
- git4idea repo detection: `GitRepositoryManager.getInstance(project).repositories`.
- **Content-module naming (easy to get wrong):** a `<content><module name="X"/>` entry, the
  module descriptor file `X.xml`, and the Gradle-produced module jar `lib/modules/X.jar` must all
  share the same name `X`, and Gradle derives that jar name from `rootProject.name` + the
  subproject name — so here it is `git-profiles.shared` / `git-profiles.frontend` /
  `git-profiles.backend` (NOT the base package `git.more.profiles.*`). A mismatch compiles and
  packages fine but fails at runtime with "Cannot resolve <name>.xml … contains invalid plugin
  descriptor", and the whole plugin silently doesn't load.
- **Running it:** `runIde` = monolithic (everything in one process — simplest for manual testing).
  Split mode = the compound "Run IDE with Plugin (Split Mode)"; `runIdeFrontend` needs
  `runIdeBackend` already running (it waits for a join link and fails without one), and the window
  to test in is the JetBrains Client. `splitMode` defaults to off and only affects how `runIde`
  itself launches; pass `-PsplitMode=true` to change that. The action lives in Tools + Git menus and
  the commit toolbar (the Git/commit anchors need a git repo open; Tools is always there).
  See [docs/REMOTE-DEV-TESTING.md](docs/REMOTE-DEV-TESTING.md) for the full manual test plan.
- **Never add the Gradle `application` plugin to the root build** (the modular-plugin template ships
  it). Its `distZip`/`distTar` write `build/distributions/<name>-<version>.zip` — byte-for-byte the
  path `buildPlugin` uses — so running `build` overwrites the plugin artifact with a JVM application
  distribution (`bin/` launch scripts, `lib/*-base.jar`). It builds green and the zip looks
  plausible; it just cannot be installed. Sanity check the artifact with
  `unzip -l build/distributions/*.zip` — you want `lib/modules/git-profiles.*.jar`, not `bin/`.
- A sandbox IDE left running holds `plugins*/git-profiles/lib/*.jar` memory-mapped, so the next
  build fails with "The requested operation cannot be performed on a file with a user-mapped section
  open". Close every sandbox window (monolith, backend host **and** JetBrains Client) before rebuilding.
- Do not commit the `.intellijPlatform/sandbox/` directory or other build artifacts.

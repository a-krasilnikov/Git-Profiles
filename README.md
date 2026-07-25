# Git Profiles

A plugin for IntelliJ IDEA and other JetBrains IDEs that manages **git identity profiles** — named
pairs of `user.name` and `user.email` — and applies them either machine-wide or per repository.

Built as a **modular (split) plugin**, so it works the same in a normal IDE and in remote
development, where the UI runs in the JetBrains Client and your code lives on another machine.

## What it does

- Stores a list of git identities, IDE-wide, and shows them in a table (add / edit / remove).
- Sets any of them as the **global** identity (`git config --global user.name/user.email`).
- Assigns an identity **per git repository** in the current project, independently for each
  (`git config --local`), or lets a repository fall back to the global one.
- Shows the identity actually in effect for every repository (`local` if set, otherwise `global`).

Git config is the source of truth: it is read every time the dialog opens, and identities found
there that are not in the list yet are imported automatically. Only the profile *list* is persisted
by the plugin — and on the client side, so it follows you rather than the host machine.

Open it from **Tools → Git Profiles…**, the **Git** menu, or the commit toolbar.

## Requirements

IntelliJ IDEA **2025.3** or newer (build 253+). That is the first release with the Split Mode,
content-module and RPC infrastructure this plugin is built on.

## Building and running

Gradle needs JDK 21.

```bash
./gradlew build
```

| Task | What it does |
|---|---|
| `./gradlew build` | Compiles all modules and runs the tests |
| `./gradlew runIde` | Launches a sandbox IDE with the plugin (monolithic — easiest way to try it) |
| `./gradlew verifyPlugin` | IntelliJ Plugin Verifier — **not** part of `build`; needs a few GB free |
| `./gradlew buildPlugin` | Produces the installable zip in `build/distributions` |

To exercise the frontend/backend split locally, use the compound **Run IDE with Plugin (Split
Mode)** run configuration and work in the JetBrains Client window it opens. `runIdeFrontend`
requires `runIdeBackend` to be running already.

## Architecture

Three content modules; which side each loads on is decided purely by its platform dependencies:

| Module | Loads on | Contains |
|---|---|---|
| `shared` | both | the model, the `@Rpc` contract, DTOs, the remote topic |
| `frontend` | JetBrains Client, monolith | dialogs, the profile store, the RPC client |
| `backend` | host, monolith | git4idea access, the RPC implementation, the action |

All git work happens on the backend, where the repositories actually are; the frontend never calls
git4idea. See [docs/DESIGN.md](docs/DESIGN.md) for the full design and [CLAUDE.md](CLAUDE.md) for
the non-obvious platform constraints that shaped it.

## Testing

Automated tests cover the pieces in isolation — real `git` integration tests for the backend
operations, and the profile store (including its XML persistence format) for the frontend. They do
not cover data crossing the process boundary; for that, follow the manual plan in
[docs/REMOTE-DEV-TESTING.md](docs/REMOTE-DEV-TESTING.md) before releasing.

## Publishing

Releases go to [JetBrains Marketplace](https://plugins.jetbrains.com) via the `publishPlugin`
Gradle task, driven by the GitHub workflows in `.github/workflows`. They require the
`PUBLISH_TOKEN`, `CERTIFICATE_CHAIN`, `PRIVATE_KEY` and `PRIVATE_KEY_PASSWORD` secrets — see
[Publishing a Plugin](https://plugins.jetbrains.com/docs/intellij/publishing-plugin.html).

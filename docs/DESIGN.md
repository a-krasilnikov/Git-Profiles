# Git Profiles — Requirements & Design

## 1. Purpose

A JetBrains IDE plugin to manage **git identity profiles**. A *profile* is a value pair of
`user.name` and `user.email`. The plugin stores a set of profiles and applies them either
globally (machine-wide git config) or per repository in the current project.

## 2. Functional requirements

| # | Requirement |
|---|-------------|
| R1 | The UI opens as a modal dialog, triggered by an IDE action. |
| R2 | The plugin stores all profiles and shows them in a table. |
| R3 | Profiles can be added, removed, and edited. |
| R4 | Exactly one profile can be set as **global** (`git config --global user.name/user.email`). |
| R5 | For the current project, a profile can be assigned **per git repository** (each repo independently), applied as local git config. |

## 3. Decisions

- **Multi-repo:** per-repository assignment (no single project-wide profile).
- **Apply mechanism:** the bundled **git4idea** plugin (repo detection + `git config`).
- **Storage scope:**
  - Profile list — **application level** (IDE-wide, shared across projects). This is the only
    persisted state.
  - Per-repo assignment — **not persisted**. Applied on the fly to the repo's local git config,
    and re-read from git config when the dialog opens.
- **Profile fields:** `user.name`, `user.email` only. No display label.
- **Profile identity:** the `(name, email)` pair. Exact duplicates are rejected.
- **Global state is derived from git:** on open, read the actual global git config and mark the
  matching profile (if any) as the active global.
- **Auto-import of discovered identities:** identities found in git config (global and each
  repository's local config) that match no stored profile are imported into the store on
  dialog open. No background/event-driven detection — deliberately kept simple.

## 4. Domain model (sketch)

```
Profile
  name: String       # git user.name
  email: String      # git user.email
```

One persistent store:

- `GitProfilesAppService` (application-level `PersistentStateComponent`)
  - `profiles: List<Profile>`

There is **no** project-level state. Neither the global profile nor per-repo assignments are
stored as authoritative state: both are read from git config at dialog-open time (global from
`~/.gitconfig`, per-repo from each repo's local config) and reconciled against the stored
profile list. Selections are applied to git config on the fly.

## 5. UI (dialog)

A single `DialogWrapper` with two areas:

1. **Profiles table** (`JBTable` + `ToolbarDecorator`)
   - Columns: Name, Email. A marker/column indicating which one is currently global.
   - Toolbar: Add, Edit, Remove.
   - Button/action: **Set as Global** (applies the selected profile to `git config --global`).

2. **Current-project repositories** section
   - One row per git repository detected in the project (via git4idea).
   - On open, each repo's current local git config (`user.name`/`user.email`) is read and
     matched against stored profiles to pre-select the active profile (or show "none"/custom
     if it matches no stored profile).
   - Each row: repo path + a combo box to pick one of the stored profiles.
   - Choosing a profile applies it to that repo's local git config immediately (on the fly);
     nothing is persisted in plugin state.

Add/Edit uses a small secondary dialog with Name and Email fields and validation (non-empty
name, basic email format).

## 6. Applying config (git4idea)

- Detect repos: `GitRepositoryManager.getInstance(project).repositories`.
- Global: run `git config --global user.name <name>` and `... user.email <email>`.
- Per repo: run `git config --local user.name <name>` and `... user.email <email>` scoped to
  the repo root.
- All git calls run off the EDT; surface failures to the user.

## 7. Actions & registration

- One action (e.g. `git.more.profiles.OpenGitProfilesAction`) registered in `plugin.xml`,
  placed under the **Git** menu and the **Tools** menu.
- `plugin.xml` declares `<depends>Git4Idea</depends>`; the build adds `bundledPlugin("Git4Idea")`.

## 8. Out of scope (for now)

- Fields beyond name/email (signing key, GPG, per-profile SSH, etc.).
- Auto-switching profiles based on remote URL or path patterns.
- A tool window (the template's sample tool window will be removed).

## 9. Open questions

_None currently blocking. Add here if new ambiguities arise._

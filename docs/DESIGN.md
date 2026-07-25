# Git Profiles — Requirements & Design

## 1. Purpose

A JetBrains IDE plugin to manage **git identity profiles**. A *profile* is a value pair of
`user.name` and `user.email`. The plugin stores a set of profiles and applies them either
globally (machine-wide git config) or per repository in the current project.

It is built as a **modular (split) plugin**, so it works both in a normal monolithic IDE and in
remote development, where the UI runs in the JetBrains Client and the repositories live on a
different machine.

## 2. Functional requirements

| # | Requirement |
|---|-------------|
| R1 | The UI opens as a modal dialog, triggered by an IDE action. |
| R2 | The plugin stores all profiles and shows them in a table. |
| R3 | Profiles can be added, removed, and edited. |
| R4 | Exactly one profile can be set as **global** (`git config --global user.name/user.email`). |
| R5 | For the current project, a profile can be assigned **per git repository** (each repo independently), applied as local git config. |
| R6 | All of the above behave identically in remote development / split mode. |

## 3. Decisions

- **Multi-repo:** per-repository assignment (no single project-wide profile).
- **Apply mechanism:** the bundled **git4idea** plugin (repo detection + `git config`).
- **Storage scope:**
  - Profile list — **application level**, and **frontend-only**: it is a client-side user
    preference, so it follows the user rather than the host. This is the only persisted state.
  - Per-repo assignment and the global identity — **not persisted**. Applied on the fly to git
    config and re-read from it when the dialog opens.
- **Profile fields:** `user.name`, `user.email` only. No display label.
- **Profile identity:** the `(name, email)` pair. Exact duplicates are rejected.
- **Global state is derived from git:** on open, read the actual global git config and mark the
  matching profile (if any) as the active global.
- **Auto-import of discovered identities:** identities found in git config (global and each
  repository's local config) that match no stored profile are imported into the store on
  dialog open. No background/event-driven detection — deliberately kept simple.

## 4. Domain model

```
GitProfile
  name: String       # git user.name
  email: String      # git user.email
```

`GitProfile` lives in the `shared` module: it is `@Serializable` (it crosses the RPC boundary)
and is also what the frontend store persists.

One persistent store: `GitProfilesService` — an application-level `PersistentStateComponent` in
the **frontend** module holding `profiles: List<GitProfile>`. The backend never reads it; it only
receives fully-formed profiles over RPC, so the two sides need no state synchronization.

There is **no** project-level state. Neither the global profile nor per-repo assignments are
stored as authoritative state: both are read from git config at dialog-open time and reconciled
against the stored profile list. Selections are applied to git config on the fly.

> Persistence detail: the store element is `GitProfilesService.StoredProfile`, not `GitProfile`
> itself. kotlinx serialization's synthetic members break IntelliJ's XML serializer for list
> items, so the wire type and the persisted type are deliberately separate. `@Tag("GitProfile")`
> keeps the on-disk format unchanged.

## 5. Split architecture

Three content modules, plus the root descriptor that lists them:

| Module | Loads on | Contains |
|---|---|---|
| `shared` | both sides | `GitProfile`, the `@Rpc` contract, DTOs, the remote topic |
| `frontend` | JetBrains Client, monolith | dialogs, the profile store, the RPC client |
| `backend` | host, monolith | git4idea access, the RPC implementation, the action |

Which side a module loads on is decided by its dependency on `intellij.platform.frontend` /
`intellij.platform.backend` — not by any explicit switch.

**Data flow.** The frontend holds no git logic. It asks the backend for one
`GitConfigSnapshotDto` (the global identity plus each repository's *local* identity) and derives
the effective identity (`local ?: global`) itself. Repositories are addressed across the boundary
by their root path string; the frontend treats it as an opaque id and only displays the name.

**Entry point.** Menus and the commit toolbar are rendered from the *backend's* action model, so
the action is registered in the backend module (`GitProfiles.OpenFromMenu`). It shows no UI:
it broadcasts a `ProjectRemoteTopic` which the frontend's listener turns into the dialog. See
[CLAUDE.md](../CLAUDE.md) for why a frontend-registered action cannot appear in those menus.

## 6. UI (dialog)

A single `DialogWrapper` with two areas:

1. **Profiles table** (`TableView` + `ToolbarDecorator`)
   - Columns: Name, Email, and a marker for the current global profile.
   - Toolbar: Add, Edit, Remove, **Set as Global**.

2. **Current-project repositories** section
   - One row per git repository, from the snapshot read before the dialog opened.
   - Each row: repository name, a combo box to pick a stored profile (or "Use global identity"),
     and a label showing the effective identity.
   - Choosing a profile applies it to that repo's local git config immediately, over RPC.

Add/Edit uses a small secondary dialog with Name and Email fields, rejecting empty values and
exact duplicates.

## 7. Applying config (git4idea, backend only)

- Detect repos: `GitRepositoryManager.getInstance(project).repositories`.
- Global: `git config --global user.name/user.email`.
- Per repo: `git config --local user.name/user.email`, scoped to the repository root.
- All git calls run on `Dispatchers.IO`, never the EDT; failures surface to the user in the dialog.

## 8. Out of scope (for now)

- Fields beyond name/email (signing key, GPG, per-profile SSH, etc.).
- Auto-switching profiles based on remote URL or path patterns.
- A tool window.
- Live refresh of the dialog while it is open (git config is re-read on each open instead).

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
| R6 | Identities are imported automatically: those found in git config, and those of the GitHub and GitLab accounts the IDE is signed in to. An imported profile is badged with the icon of the provider it came from. |

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
- **Import from hosting accounts:** on the same dialog open, every registered provider is asked
  for the identities of the accounts the IDE is signed in to. Silent, like the git config import.
- **Profile origin:** an imported profile records which provider produced it, so the table can
  badge it. An open string rather than an enum — each provider declares its own id — so adding a
  provider changes no storage format and migrates nothing.
- **Import is not scoped to the project:** the profile list is IDE-wide, so a signed-in account
  contributes whether or not the open project is hosted there. (An earlier design gated on the
  project's git remotes; it was dropped as surprising — being signed in was not enough to get
  your own identity offered.)
- **Providers synthesize nothing:** only addresses the API actually named become profiles. In
  particular no noreply address is constructed from a convention, though one that the API reports
  itself — GitHub lists it in `/user/emails`, GitLab returns it as `commit_email` — is kept like
  any other.
- **Hosting plugins are optional dependencies:** a hard one would disable the whole plugin for
  anyone who turns GitHub or GitLab off.

## 4. Domain model (sketch)

```
GitProfile                # the identity itself, compared by value everywhere
  name: String            # git user.name
  email: String           # git user.email

StoredProfile             # how a profile is persisted
  name: String
  email: String
  origin: String          # provider id, "" when the user typed it
```

The two are separate on purpose. `GitProfile` is matched by value throughout — the global marker,
the per-repository selector, deduplication — and an identity read from git config carries no
origin, so folding the field into it would break those comparisons.

One persistent store:

- `GitProfilesService` (application-level `PersistentStateComponent`, `git-profiles.xml`)
  - `profiles: List<StoredProfile>`

The list element keeps the `GitProfile` tag it had before the origin existed, and the origin is
omitted at its default, so files written by earlier versions load untouched.

There is **no** project-level state. Neither the global profile nor per-repo assignments are
stored as authoritative state: both are read from git config at dialog-open time (global from
`~/.gitconfig`, per-repo from each repo's local config) and reconciled against the stored
profile list. Selections are applied to git config on the fly.

## 5. UI (dialog)

A single `DialogWrapper` with two areas:

1. **Profiles table** (`JBTable` + `ToolbarDecorator`)
   - Columns: a narrow badge column, Name, Email, and a marker for the current global one.
   - The badge shows the icon of the provider that imported the profile, with a tooltip naming
     it; profiles the user typed stay blank. The icon comes from the provider, so it disappears
     while that hosting plugin is disabled.
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

- One action (`git.more.profiles.actions.OpenGitProfilesAction`) registered in `plugin.xml`,
  placed under the **Git** menu and on the commit toolbar.
- `plugin.xml` declares `<depends>Git4Idea</depends>`; the build adds `bundledPlugin("Git4Idea")`.
- GitHub and GitLab are declared with `<depends optional="true" config-file="…">`, each pointing
  at a descriptor that registers nothing but that provider.

## 8. Profile providers

The extension point `git.more.profiles.git-profiles.profileProvider` is what a hosting
integration plugs into. A provider declares four things: the id recorded on the profiles it
imports (**persisted**, so it must stay stable), a display name, the icon that badges them, and
how to read the identities of the signed-in accounts.

`AccountProfileProvider` is the base for account-backed providers and owns what all of them must
get right, so that a new one cannot forget it:

- **A timeout per account, not one shared budget.** A shared deadline expiring cancels the whole
  traversal and discards what the earlier accounts had already returned — one hung server would
  cost every account's result.
- **Accounts read concurrently,** so per-account timeouts still cost one timeout of wall clock.
- **Errors contained per account.** One unreachable account yields nothing and logs; the others
  are unaffected.

Its account type is an unbounded type parameter rather than the obvious common supertype
`ServerAccount`: that type lives in the collaborationTools module, and the base class is loaded
even when every hosting plugin is disabled.

**The isolation rule.** A hosting plugin's classes may appear only in that provider's own class,
which the platform instantiates solely through the optional config-file. Anything loaded
unconditionally must stay clear of them — `com.intellij.collaboration.*` included, since it comes
in with those plugins. Verify on the built jar by scanning class constant pools, not by reading.

Providers today: **GitHub** (`/user` + `/user/emails`) and **GitLab** (`/user`, whose
`commit_email` already reports whatever the account actually commits with).

## 9. Out of scope (for now)

- Fields beyond name/email (signing key, GPG, per-profile SSH, etc.).
- Auto-switching profiles based on remote URL or path patterns.
- Filtering out noreply addresses that a provider's API reports itself.
- A tool window (the template's sample tool window will be removed).

## 10. Open questions

_None currently blocking. Add here if new ambiguities arise._

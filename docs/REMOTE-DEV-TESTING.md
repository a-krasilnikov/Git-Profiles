# Manual testing: Git Profiles in split mode / remote development

Why this exists: automated tests cover the pieces in isolation (git operations on the backend,
the profile store on the frontend), but they **cannot** cover the thing the split migration is
actually about — data crossing the frontend↔backend RPC boundary between two processes. That has
to be exercised by hand.

Read this top to bottom the first time. Levels 1→3 go from cheapest to most realistic; do at least
levels 1 and 2 before a release.

---

## Vocabulary (what is what)

| Term | Meaning here |
|---|---|
| **Backend** / host | The process where your code and git repositories physically live. Runs `git config`. |
| **Frontend** / client | The **JetBrains Client** window — the thin UI. This is where the plugin's dialog lives. |
| **Monolith** | One ordinary IDE process being both at once. What `runIde` gives you. |

The plugin is split accordingly: `git-profiles.frontend` (dialog, action, profile list) and
`git-profiles.backend` (git4idea, `git config`), talking over `GitProfilesRpcApi`.

---

## Prerequisite: a test project with git repos

The dialog is only interesting when there are repositories to show. Create a scratch project with
**two** repos (multi-repo is a supported case worth covering) and no identity configured yet:

```bash
mkdir -p /c/temp/gp-test/repo-one /c/temp/gp-test/repo-two && cd /c/temp/gp-test && git -C repo-one init && git -C repo-two init && git -C repo-one config --local --unset user.name; git -C repo-one config --local --unset user.email; echo ok
```

Open `C:\temp\gp-test` as the project in the sandbox IDE when asked below.

Record your **real** global identity first, so you can tell it apart from test values and restore it
at the end — the plugin writes to the machine-wide `~/.gitconfig` by design:

```bash
git config --global --get user.name; git config --global --get user.email
```

---

## Level 1 — Monolith smoke test (5 min)

Fastest way to confirm the feature works at all. Not a split-mode test, but if this is broken,
everything else will be too.

```bash
./gradlew runIde
```

Then in the sandbox IDE, open `C:\temp\gp-test` and walk the feature:

1. **Tools → Git Profiles…** opens the dialog (no exception popup).
2. Add two profiles (Work / Home). Edit one. Remove one. Try adding a duplicate — must be rejected.
3. Select a profile → **Set as Global**. Verify on disk:
   ```bash
   git config --global --get user.name
   ```
4. For each repository row, pick a profile. Verify the repo's own config:
   ```bash
   git -C /c/temp/gp-test/repo-one config --local --get user.email
   ```
5. Set a repo back to **Use global identity** → its local keys must disappear:
   ```bash
   git -C /c/temp/gp-test/repo-one config --local --get user.email; echo "exit=$? (expect 1 = unset)"
   ```
6. Close the IDE, run `runIde` again, reopen the dialog → **your profile list is still there**
   (this is the persistence path that previously crashed).

---

## Level 2 — Local split mode (the important one)

Two real processes, real RPC, real serialization — same code path as true remote development,
minus network latency. **This is the level that validates the migration.**

### Launching

`runIdeFrontend` needs the backend's join link, so **order matters**. Use the compound run
configuration **"Run IDE with Plugin (Split Mode)"** (it is committed in `.run/`), or two terminals:

```bash
./gradlew runIdeBackend
```

then, once the backend window is up, in a second terminal:

```bash
./gradlew runIdeFrontend
```

> **Work in the window that `runIdeFrontend` opens** — that is the JetBrains Client. `runIdeBackend`
> also opens a window of its own; that one is the host and deliberately has **no** plugin UI
> (the frontend module cannot load in backend product mode). Testing in the wrong window is the
> single easiest mistake to make here.

### What to verify

**2.1 The UI is there and works**
Repeat all of Level 1 inside the JetBrains Client window. Every git assertion (`git config …`)
still runs on the host — same machine here, so the same commands work.

**2.2 Git work really happened on the backend**
This is the point of the whole architecture. After **Set as Global** in the client, the value must
appear in the host's git config. If the dialog shows a value but `git config --global --get user.name`
does not, the write never crossed the boundary.

**2.3 The profile list is stored on the *client*, not the host**
The profile list is deliberately frontend-only state. After adding profiles in the client:

```bash
SB=".intellijPlatform/sandbox/git-profiles/IU-2025.3.5"; echo "--- client (expect your profiles) ---"; cat "$SB/config_runIdeFrontend/frontend/options/git-profiles.xml" 2>/dev/null || echo "MISSING - investigate"; echo "--- host (expect absent) ---"; ls "$SB/config_runIdeBackend/options/git-profiles.xml" 2>/dev/null || echo "absent (correct)"
```

**2.4 Git config is re-read on every open**
With the dialog **closed**, change the identity from a terminal:

```bash
git -C /c/temp/gp-test/repo-one config --local user.email changed-externally@example.com
```

Reopen the dialog in the client → `repo-one` must show the new value, and that identity must also
appear in the profiles table (auto-import). The dialog holds no cache: each open is a fresh
snapshot read from the host.

**2.5 Reconnect resilience (`durable`)**
Close **only** the backend window, then start `./gradlew runIdeBackend` again. The client should
recover and the dialog should work again **without restarting the client** — every RPC call is
wrapped in `durable`, which retries across a dropped connection. (A brief error or empty state
while the backend is down is acceptable; a permanently dead UI is not.)

**2.5a The entry point itself crosses the boundary**
Worth calling out because it is the subtlest part of the design: the action is registered on the
**backend**, and opening the dialog is a backend→frontend remote-topic broadcast. So verify all
three host-rendered surfaces actually open a dialog **in the client window**: Tools menu, Git menu,
and the commit toolbar button. If a menu item exists but clicking it does nothing, the topic did not
reach the frontend listener — check the client log for `git-profiles.frontend is not enabled`.

**2.6 Latency — the realistic part**
Local split mode has ~0 ms RPC latency, which hides sluggish UI. Simulate a real network:

- The sandbox already runs with `-Didea.is.internal=true`.
- In the client, open the **Split Mode widget** and set a ping delay (start at 200 ms, then 1000 ms).
- Re-run 2.1. **Expected:** opening the dialog shows a modal progress ("Reading Git configuration")
  and the UI never freezes. **A frozen/unresponsive window is a bug** — it means an RPC call is
  blocking the EDT.

**2.7 Edge cases**
- Open a project with **no** git repositories → dialog shows "No git repositories detected…", no error.
- A repo whose identity matches no stored profile → shown as "Custom: …", and auto-imported into the
  list when the dialog opens.

---

## Level 3 — True remote development (final validation)

Levels 1–2 use the Gradle sandbox. Level 3 installs the built plugin into a real Gateway session,
where the host is genuinely a different machine/OS. On Windows the cheapest real host is **WSL2**.

1. Build the distributable (do this yourself; it lands in `build/distributions/`):
   ```bash
   ./gradlew buildPlugin
   ```
2. Start a remote session: **JetBrains Gateway** → connect to your WSL2 / SSH host → open a project
   there that contains a git repository.
3. Install the plugin **on the host**: in the client, `Settings → Plugins` → gear → *Install Plugin
   from Disk…*, and make sure you are installing into the **host** (remote) side. Restart the session.
   - Note: a locally-built ZIP cannot auto-synchronize between sides the way a Marketplace plugin
     does, so if the client does not pick up its part automatically, install the same ZIP on the
     client side too.
4. Re-run the Level 2 checks, with one difference that matters: run the `git config` assertions **on
   the remote host** (in the WSL/SSH shell), not on Windows. The whole point is that identities are
   written where the code lives.
5. Confirm the profile list follows **you**, not the host: connect to a *second* different host —
   your profiles should still be listed (they live in the client's own config).

---

## Restoring your machine afterwards

The plugin writes real machine-wide git config. Put your identity back:

```bash
git config --global user.name "Your Name"; git config --global user.email "your@email"; git config --global --get user.name; git config --global --get user.email
```

---

## Reporting a problem

Logs are per run mode under `.intellijPlatform/sandbox/git-profiles/IU-2025.3.5/`:

| Run | Log |
|---|---|
| `runIde` (monolith) | `log/idea.log` |
| `runIdeBackend` (host) | `log_runIdeBackend/idea.log` |
| `runIdeFrontend` (client) | `log_runIdeFrontend/frontend/<timestamp>/idea.log` |

Useful filter — plugin loading problems show up as module-level messages:

```bash
grep -nE "git-profiles\.(shared|frontend|backend)|Git Profiles|not enabled because|invalid plugin descriptor" .intellijPlatform/sandbox/git-profiles/IU-2025.3.5/log/idea.log | tail -20
```

Two failure signatures worth recognising:
- `Module git-profiles.frontend is not enabled because dependency X is not available` — the frontend
  module declared a dependency the client does not have; the UI silently disappears.
- `contains invalid plugin descriptor` / `Cannot resolve <name>.xml` — content-module name mismatch
  (module name, descriptor file name and `lib/modules/<name>.jar` must all agree).

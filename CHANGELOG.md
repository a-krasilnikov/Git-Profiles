<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# Git Profiles Changelog

## [Unreleased]

### Added

- Support for remote development and Split Mode: the plugin is now a modular plugin split into
  `shared`, `frontend` and `backend` content modules. The dialog runs in the JetBrains Client while
  all git operations run on the host, communicating over RPC.
- The profile list is stored client-side, so it follows the user across hosts.

### Changed

- Requires IntelliJ IDEA 2025.3 or newer (build 253+), the first release providing Split Mode,
  content modules and the RPC framework.
- Repository data reaches the UI as a single snapshot read from the host, instead of the UI
  querying git directly.

### Removed

- The toolbar tooltip showing the identity in effect. It was attached to a toolbar button that no
  longer exists; the same information is shown for every repository inside the dialog.

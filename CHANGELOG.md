<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# Git-profiles Changelog

## [Unreleased]

### Added

- Profiles are imported from the GitHub and GitLab accounts the IDE is signed in to, alongside the
  identities already found in git config. Both integrations are optional: the plugin works
  unchanged when those bundled plugins are disabled.
- Imported profiles are badged in the table with the icon of the provider they came from.
- A `profileProvider` extension point, so a further hosting integration is one class.

# Changelog

All notable user-facing changes to Baritone Helper are recorded here.

## Unreleased

### Added

- Product-focused README, documentation index, user guide, architecture guide, testing guide, and release-process documentation.
- Structured GitHub bug-report, feature-request, and pull-request templates.

### Changed

- Version-specific specifications and verification records are organized under `docs/releases/` and `docs/archive/` instead of being presented as current root-level contracts.
- The release workflow no longer hardcodes a default release version, requires a matching changelog entry, uses that entry as release notes, and publishes SHA-256 checksums with release artifacts.
- NeoForge MDK template licensing is retained under `LICENSES/NeoForge-MDK-MIT.txt` with a clearer name.

## 3.1.0 - 2026-08-31

### Added

- Long-lived relocated Baritone `MineProcess` integration for worker collection.
- Asynchronous palette-backed world scanning separated from bounded path-calculation workers.
- Persistent worker loaded-view ownership and lifecycle coverage.
- Remote access to the worker's canonical 27/54-slot inventory from the controller while in the same dimension.
- Movement-parity, concurrency, inventory, lifecycle, long-range discovery, and multi-worker performance coverage.

### Changed

- Worker movement now uses Baritone-derived input and movement math instead of the previous collection planner's navigation path.
- Mining, tool selection, placement, pickup, persistence, and deposits share the worker's canonical inventory.
- Search and path publication are generation-fenced so cancelled or replaced work cannot be revived by stale asynchronous results.
- Dashboard diagnostics expose the active Baritone/search/path state more directly.

## 3.0.0 - 2026-08-30

- New workers now default to the maximum supported search area: 512 blocks horizontally and 128 blocks vertically.

## 2.0.0 - 2026-08-30

- Replaced vanilla worker navigation with a relocated server-side Baritone-derived path planner/executor.
- Added exact searchable block picker, finite/unlimited source-block goals, configurable work areas, no-work zones, pathing toggles, and activity log.
- Added real progressive mining, normal world drops, physical pickup, storage conservation, bounded tickets, offline operation, and v1 NBT migration.
- Added NeoForge request/revision/acknowledgement payloads and authoritative dashboard snapshots.

## 1.0.0

- Initial collector-only release.

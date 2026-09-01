# Changelog

All notable user-facing changes to Baritone Helper are recorded here.

## Unreleased

## 3.2.0-rc.1 - 2026-08-31

### Added

- Bounded exhaustive work-area discovery and explicit opt-in Roam mode.
- Target-aware shared persistent world knowledge with dirty coverage and corrected 512-block cached-region semantics.
- Protocol-4 UUID/dimension remote management, idempotent desired-state actions, and real search/path telemetry.
- Transactional packed-worker pickup and owner-only stopped-state restoration.
- Reproducible two-boot cold-discovery, 0/1/2/4-worker JFR, mutation, clean-startup, and exact-artifact verification harnesses.
- Product-focused documentation, user/architecture/testing/release guides, and structured GitHub contribution templates.

### Changed

- Active view and simulation tickets now slide with the worker and are released whenever work is not active; radius 6 was selected by benchmark.
- Hard break prohibitions are distinct from soft avoid-breaking costs, with modern 1.21.1 blocks, fluids, climbing, and support surfaces audited against current upstream Baritone.
- Mining retains progressive break timing, constant `1.0F` movement input, normal multi-drop behavior, tool durability, and server-side interaction revalidation.
- Version-specific specifications and verification records are organized under release/archive documentation instead of being presented as permanent current contracts.
- NeoForge MDK template licensing is retained under `LICENSES/NeoForge-MDK-MIT.txt` with a clearer name.
- Release automation accepts an exact manual tag, reruns release gates, publishes SHA-256 digests, and creates a prerelease without marking it latest.

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

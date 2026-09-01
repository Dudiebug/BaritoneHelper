# Baritone Helper 3.2.0-rc.1

This release candidate replaces fixed-window target discovery with Baritone-
owned bounded exploration and an explicit opt-in roam mode.

## Highlights

- Target-aware, per-dimension world knowledge with persistent coverage and
  conservative dirtying after world changes.
- Correct upstream 512-block cached-region semantics and modern mining/pathing
  parity while preserving server-thread snapshots and bounded executors.
- Current upstream hard/soft break avoidance, liquid flow checks, modern block
  traversal cases, and real multi-drop loot preservation.
- Smaller active sliding view, separate simulation footprint, and no idle
  ticket window.
- UUID/dimension remote management, ordered authoritative telemetry, and
  idempotent protocol-4 requests.
- Transactional pickup into a versioned packed-worker item that preserves cargo
  and configuration without serializing live runtime state.
- Responsive five-tab dashboard with explicit Work Area/Roam controls and real
  search/path activity.
- Re-audited against official Cabaletta Baritone `1.21.1` at `f3a51d47`,
  including its dedicated-server loot-registry correction.

## Compatibility

- Minecraft 1.21.1, NeoForge 21.1.248, Java 21.
- Existing worker saves migrate to bounded `WORK_AREA`; roam is never enabled
  silently.
- Protocol-3 clients are not compatible with protocol 4.
- Blank legacy worker items continue to place a fresh worker.

This is a prerelease. Back up worlds before testing and report the exact server
log, worker state, target, work area, and reproduction steps for any issue.

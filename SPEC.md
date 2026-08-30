# Baritone Helper 2.0 executable specification

## Product contract

- Minecraft 1.21.1, NeoForge 21.1.248, Java 21.
- One universal `baritonehelper-2.0.0.jar` plus a source JAR; no runtime
  Baritone/Automatone/PlayerEngine dependency.
- Canonical IDs: `baritonehelper:baritone_helper`,
  `baritonehelper:worker_controller`, `baritonehelper:cargo_upgrade`, and the
  `baritonehelper:baritone_helper` entity.
- Hidden `buddybot:buddy_bot` aliases load old base content only. No tier,
  rescue, combat, following, wandering, or cross-dimension behavior exists.

## Configuration

`WorkerJobConfiguration` persists an exact block registry ID, requested count
(1–1,000,000), unlimited flag, completed source-block count, work-area
dimension/center/radii, storage dimension/position, exclusions, pathing flags,
traversal-block allowlist, no-work zones, and a monotonic revision. Changing a
target or amount resets progress. A finite goal stops at exactly its requested
source-block count, deposits remaining cargo, and becomes `COMPLETED`; missing
or full storage preserves cargo and reports the exact block reason.

No-work zones have stable UUIDs, names, dimensions, centers, horizontal and
vertical radii, mode (`NO_MODIFY` or `NO_ENTER`), and enabled state. They are
enforced by target search, reservation, path costs, interaction, placement,
pickup, and storage validation.

Runtime states are `UNCONFIGURED`, `READY`, `STARTING`, `SEARCHING`, `PATHING`,
`BREAKING`, `COLLECTING_DROPS`, `RETURNING_TO_STORAGE`, `DEPOSITING`,
`COMPLETED`, `STOPPING`, and `BLOCKED`.

## Interaction and movement

Each worker owns one transient relocated Baritone-derived engine. The controller
submits real goals to its `CustomGoalProcess`; it never calls vanilla
`getNavigation().moveTo`, teleports as a fallback, or uses direct world block
placement. The movement graph includes jumping, parkour, pillar/bridge
placement from real inventory stacks, obstruction clearing, replanning, and
water-enabled traversal.

Collection uses the real progressive interaction manager: selected tool,
hardness/speed, enchantments, durability, crack progress, server break hooks,
game events, `mobGriefing`, and normal `ItemEntity` drops. Drops are acquired by
physical pickup; predicted-drop insertion is not an implementation path. Block
entities, unbreakable/protected/forbidden blocks, and missing correct tools are
rejected safely. Interaction positions use reach and line of sight, including
targets above and below the worker.

## Search and tickets

The planner walks a chunk frontier ordered around the work center, scanning a
bounded block budget per tick and skipping unloaded chunks. It never repeats a
full cubic area scan. Candidate work positions are collision-free, supported,
within the configured work area, and outside forbidden zones.

Active workers hold a bounded entity-owned ticket set for the local 3×3 window,
current target/work/storage chunks, and route hints. Tickets update across chunk
boundaries and release on stop, completion, dismissal, administrative removal,
invalid-owner cleanup, and engine disposal. Persisted paths are not trusted on
restart; configuration is loaded and paths are recalculated.

## Dashboard and protocol

The controller opens a normal responsive `Screen` with Job, Areas, Storage,
Pathing, and Activity Log tabs. The Job tab has an immediate searchable picker
by localized name, namespace, and full registry ID, localized name/icon display,
scrolling, clear target, amount and unlimited controls, and progress. Areas has
coordinate/radius fields and explicit player/worker/world-point modes. Storage
has explicit container selection. Pathing exposes all eight safety toggles.

NeoForge custom payloads replace the old int-only synchronization. Every client
intent contains a worker entity reference, request UUID, and expected revision.
The server validates ownership, dimension, registry ID, numeric bounds, and
staleness, then returns an acknowledgement (success/error code/translation key/
revision) and a fresh authoritative snapshot. Dashboard updates are periodic
while active and contain bounded activity history.

## Compatibility and acceptance

Schema 2 migrates v1 owner, inventory, cargo, target, storage, exclusions, and
active-worker data to `READY`; stale paths and removed tier/rescue fields are
ignored. Dismissal drops inventory exactly once, returns one canonical item,
clears ownership, releases tickets, and removes the entity. Idle helpers remain
stationary and never transfer dimensions.

The acceptance commands are:

```sh
./gradlew clean test --no-daemon --console=plain
./gradlew runGameTestServer --no-daemon --console=plain
./gradlew build --no-daemon --console=plain
```

All three must pass before a v2.0.0 tag or release is published.

# Baritone Helper 2.0 architecture

## Scope

Baritone Helper 2.0 is a single universal NeoForge 21.1.248 JAR for
Minecraft 1.21.1. The worker is a collector-only, invulnerable entity. It does
not follow, fight, rescue, wander, or change dimensions with its owner.

## History audit

The repository was fetched with branches, tags, pull-request heads, and full
history before the rewrite. `main` and the release tag contained the v1
collector. `collector-only-worker` and `fix/controller-lifecycle-feedback`
contained incremental v1 changes. `rewrite/real-baritone-v2` contained
prototype/export workflow material, not a production entity engine. No
reachable or unreachable repository commit supplied a usable server-side
Baritone entity port. The external references therefore remain explicit:

- Goodbird-git/PlayerEngine, `aa4ad2d5ec2a834107c76cdb58af732acb192ecc`;
- Cabaletta Baritone, inspected at `5f259b7f1ffaa8dca4cd1207c34bb8fb5e534756`;
- Automatone swimming work, `1fb7ad155cf4ca7fc846506235da03d6fae4c0e4`.

Only the server-side path planner/executor, movement graph, entity context,
inventory adapter, and interaction adapter needed by the collector were
vendored. The internal package is relocated to
`dev.dudie.baritonehelper.internal.baritone` so an independently installed
Baritone cannot collide with it.

## Runtime ownership

Each `WorkerEntity` owns one transient `Baritone` instance. It is created after
the entity is available on a server level, ticked from the entity's server tick,
and disposed on every entity removal, dismissal, stop, or engine reset. The
engine uses the entity's `EntityContext`, `LivingEntityInventory`, and
`LivingEntityInteractionManager`; it never loads a client-only Minecraft class
on a dedicated server. A fixed two-thread executor is used only for bounded
path calculations over immutable copies of loaded ticketed chunk sections,
hotbar state, and relevant settings. A calculation generation token rejects
late callbacks after cancellation or goal replacement, and the executor is
shut down by the runtime lifecycle.

The worker controller submits `GoalBlock` goals to the embedded
`CustomGoalProcess`. Baritone owns movement inputs and path execution,
including jumping, parkour, pillar/bridge movement, obstruction handling, and
water-enabled movement. The controller owns policy, target reservation, goal
counts, drop pickup, storage, watchdogs, and cancellation.

## Configuration and policy

`WorkerJobConfiguration` persists the exact registry ID, finite/unlimited goal,
progress, work-area dimension/center/radii, storage, exclusions, no-work zones,
pathing flags, traversal-block allowlist, and a monotonic revision. The scanner
uses an incremental chunk frontier with a bounded per-tick block budget rather
than repeatedly scanning a cubic volume. Candidate chunks are prioritized near
the worker while the configured center and radii remain the inclusion boundary.
Initially unloaded chunks are explicitly requested, awaited, scanned, and
released. Candidate work stances are evaluated for support, collision,
six-block reach, and line of sight from the hypothetical stance eye. A bounded
candidate cache survives individual collections, and all candidate, path,
break, place, and storage operations enforce `NO_MODIFY`/`NO_ENTER` zones.

The finite goal counts successfully broken source blocks only. Breaking uses
the real progressive interaction manager, server break hooks, tool speed,
durability, crack progress, and normal world `ItemEntity` drops. The worker
acquires those entities physically; it never inserts predicted drops as a
collection shortcut. A full inventory returns to validated storage or enters a
specific blocked state without deleting cargo.

## Dashboard protocol

The old container/int synchronization is compatibility-only. The production
dashboard is a responsive `Screen` with Job, Areas, Storage, Pathing, and
Activity Log tabs. Exact block IDs are selected from a searchable registry
picker with localized names, icons, namespaces, and scrolling. Work-area and
storage point selection are explicit armed modes; ordinary world clicks never
infer a target block type.

NeoForge custom payloads carry immutable authoritative snapshots and explicit
client intents. Every intent includes a request UUID and expected revision.
The server validates ownership, entity identity, dimensions, registry IDs,
numeric limits, and revisions, then returns an acknowledgement plus a fresh
snapshot. State transitions and activity history are bounded and persisted.
Live snapshots also expose frontier progress, scan/candidate counters, separate
search and worker ticket counts, and path status, node, cost, and sample data.

## Chunk and restart lifecycle

Active workers hold at most 16 entity-owned route tickets prioritized for the
worker, target/work/storage, and bounded route look-ahead. Frontier loading has
a distinct search-ticket reason capped at four chunks. Search tickets release
after scanning or cursor closure; all tickets release on stop, completion,
dismissal, administrative removal, and engine disposal. Persisted configuration
is reloaded after restart, but stale calculated paths are never authoritative;
the scanner and Baritone recalculate them. Path lifecycle is reported as
`IDLE`, `CALCULATING`, `PATH_FOUND`, `EXECUTING`, `ARRIVED`, `NO_PATH`,
`CANCELLED`, or `FAILED`.

## Compatibility and licensing

Schema 2 reads v1 owner, inventory, cargo, target, storage, exclusions, and
active-worker data, applies safe defaults, and migrates an old active job to
`READY`. Removed rescue/tier data is ignored, while the hidden
`buddybot:buddy_bot` aliases remain loadable. Vendored Baritone-derived source
retains its LGPL-3.0-or-later headers and is called out in
`THIRD_PARTY_NOTICES.md` and `LICENSES/LGPL-3.0.txt`; project code remains MIT.

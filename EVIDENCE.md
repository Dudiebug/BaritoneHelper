# Baritone Helper 2.0 verification evidence

## Architecture

- `WorkerEntity` owns one transient relocated Baritone-derived engine and calls
  `serverTick` from the server entity tick.
- `WorkerController` submits `GoalBlock` goals to the embedded path executor;
  no vanilla `getNavigation().moveTo` movement path remains.
- Mining uses `LivingEntityInteractionManager` progressive start/continue/stop
  actions. Normal world drops remain `ItemEntity` instances and are physically
  acquired by the worker.
- `WorkerPlanner.SearchCursor` requests, waits for, scans, and releases an
  ordered chunk frontier with a 4,096-position per-tick budget. It caches up to
  32 candidates and returns separate resource/work positions evaluated from a
  hypothetical interaction eye.
- Route tickets are capped at 16 and separate search-frontier tickets at 4;
  both release on their normal and cancellation lifecycles.
- Path calculations use immutable loaded-chunk and hotbar snapshots. Explicit
  statuses and generation tokens prevent stale asynchronous results from
  reviving cancelled or replaced goals.

## GUI and network

The controller opens a normal responsive five-tab screen. Exact target selection
is a registry-backed, localized, icon-bearing, scrollable picker; no ordinary
world click stores a target type. Areas and storage use explicit armed selection
modes. Custom NeoForge payloads carry request UUIDs, expected revisions,
server-validated actions, acknowledgements, and complete snapshots. The old
container class is retained only as a source-compatible, unsynchronized shim.

## Persistence and migration

Schema 2 stores the exact target, goal/progress, area, storage, exclusions,
pathing/traversal policy, zones, runtime state, resume note, bounded timestamped
activity history, and configuration revision. v1 owner/inventory/cargo/target/
storage/exclusion/active-worker records migrate safely to `READY`; tier/rescue
fields are ignored and stale paths are recalculated.

## Automated verification

The JUnit contract suite covers canonical assets/recipes, removal of legacy
rescue/combat/following architecture, explicit dashboard controls, visible
feedback, separate target/work positions, bounded search tickets, immutable
path snapshots, and path-failure status. NeoForge GameTests cover
configuration and migration, real progressive break/drop pickup, vertical
interaction, inventory/storage conservation, exclusions, finite/unlimited goals,
watchdog/replanning, explicit Start/Stop/idempotency, offline operation, ticket
cleanup, worker protection behavior, distances 4/16/32/64/128, initially
unloaded chunks, corners/walls, underground and vertical targets, radius and
no-work boundaries, multiple targets, restart, and both exact A–E sequences.

Run the release gate under Java 21:

```sh
./gradlew clean test --no-daemon --console=plain
./gradlew runGameTestServer --no-daemon --console=plain
./gradlew build --no-daemon --console=plain
```

The build must contain exactly one runtime JAR and one source JAR. No release is
published unless all commands pass and the artifact/version checks succeed.

The final release-gate run on 2026-08-30 passed all 15 JUnit tests, all 35
required NeoForge GameTests, and the Java 21 build. The two exact 128-block
acceptance runs measured maximum 4,096-position search slices of 15.9953 ms and
10.02 ms; every measured slice remained below the enforced 50 ms ceiling. The
build produced `build/libs/baritonehelper-2.0.0.jar` (559,561 bytes) and
`build/libs/baritonehelper-2.0.0-sources.jar` (312,778 bytes); the embedded
metadata reports version 2.0.0 and license `MIT AND LGPL-3.0-or-later`.

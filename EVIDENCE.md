# Baritone Helper 2.0 verification evidence

## Architecture

- `WorkerEntity` owns one transient relocated Baritone-derived engine and calls
  `serverTick` from the server entity tick.
- `WorkerController` submits `GoalBlock` goals to the embedded path executor;
  no vanilla `getNavigation().moveTo` movement path remains.
- Mining uses `LivingEntityInteractionManager` progressive start/continue/stop
  actions. Normal world drops remain `ItemEntity` instances and are physically
  acquired by the worker.
- `WorkerPlanner.SearchCursor` scans an ordered chunk frontier with a bounded
  per-tick budget and returns separate resource/work positions.
- Entity-owned tickets are capped at 16 and released on every stop/completion/
  removal path.

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
feedback, and separate target/work positions. NeoForge GameTests cover
configuration and migration, real progressive break/drop pickup, vertical
interaction, inventory/storage conservation, exclusions, finite/unlimited goals,
watchdog/replanning, explicit Start/Stop/idempotency, offline operation, ticket
cleanup, and worker protection behavior.

Run the release gate under Java 21:

```sh
./gradlew clean test --no-daemon --console=plain
./gradlew runGameTestServer --no-daemon --console=plain
./gradlew build --no-daemon --console=plain
```

The build must contain exactly one runtime JAR and one source JAR. No release is
published unless all commands pass and the artifact/version checks succeed.

The final release-gate run on 2026-08-30 passed 14 JUnit tests, all 18 required
NeoForge GameTests, and the Java 21 build. It produced
`build/libs/baritonehelper-2.0.0.jar` (529,693 bytes) and
`build/libs/baritonehelper-2.0.0-sources.jar` (300,691 bytes); the embedded
metadata reports version 2.0.0 and license `MIT AND LGPL-3.0-or-later`.

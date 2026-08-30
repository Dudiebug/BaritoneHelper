# Baritone Helper verification evidence

## Implemented product surface

- Canonical mod namespace: `baritonehelper`
- Canonical collector item/entity: `baritonehelper:baritone_helper`
- Controller: `baritonehelper:worker_controller`
- Cargo upgrade: `baritonehelper:cargo_upgrade`
- Hidden compatibility alias: base `buddybot:buddy_bot` only
- Java runtime and CI toolchain: Java 21

## Collector-only guarantees

Production source contains no BuddyBot tiers, rescue controller, rescue ability,
threat classification, melee goal, owner-following goal, idle float/wander goal,
cross-dimension owner transfer, combat-target suspension, or quiet-period state.
The entity is invulnerable, non-attackable, non-hostile, non-pushable, and has no
attack behavior.

## Controller repair

The former controller conflated target selection with execution and used air-use
as an ambiguous pause toggle. The replacement has distinct operations:

- controller-on-block configures or replaces a target and transitions to Ready;
- the dashboard provides explicit Start Job and Stop Job buttons;
- Stop retains target and storage while cancelling movement and tickets;
- Clear Target removes the target instead of leaving a placeholder-equivalent
  value;
- controller air-use and controller-on-helper open the dashboard;
- empty-hand helper interaction opens inventory; and
- every action emits chat feedback while the dashboard exposes live state.

## Navigation repair

The planner now returns both a resource target and a standable work position.
The controller navigates to the work position, verifies interaction distance and
line of sight, and only then collects the resource. Failed paths are retried a
bounded number of times and surface a specific blocking reason.

## Automated coverage

`ControllerUxContractTest` verifies that target selection uses `configureTarget`
rather than implicit `beginCollection`, that pause toggling is absent from the
player controller, that explicit Start/Stop/Clear controls exist, that target and
work positions are separate, and that controller actions have visible feedback.

`WorkerControllerRegressionGameTests` verifies target replacement without
starting, explicit and idempotent Start/Stop, retained target and storage,
worker-ticket release, missing-target rejection, and target clearing.

The existing GameTest and JUnit suites continue to cover inventory/job/storage
persistence, legacy NBT ignoring, invulnerability, hostile targeting, no owner
following or dimension transfer, dismissal conservation, offline collection,
deposit, storage-full behavior, full-inventory conservation, exclusions, cargo
limits, traversal, watchdog behavior, recipe/asset cardinality, and removal of
legacy rescue architecture.

## Required verification commands

```sh
./gradlew test
./gradlew runGameTestServer
./gradlew build
```

GitHub Actions executes these commands under Temurin Java 21. Verification is
complete only after the workflow for the implementation commit reports success.

# Baritone Helper executable specification

## Product identity

- Display name: **Baritone Helper**
- Mod ID and resource namespace: `baritonehelper`
- Java package: `dev.dudie.baritonehelper`
- Canonical items: `baritonehelper:baritone_helper`,
  `baritonehelper:worker_controller`, and `baritonehelper:cargo_upgrade`
- Canonical entity: `baritonehelper:baritone_helper`

Hidden `buddybot:buddy_bot` item and entity registrations exist only to load old
base stacks and already-placed entities. The legacy item places the canonical
entity, dismissal returns the canonical item, and legacy tier/rescue NBT is
ignored. Mk II and Mk III stacks are intentionally not migrated.

## Required behavior

1. There is one canonical owner-bound Baritone Helper with no tier constructor,
   tier accessor, tier NBT, or tier-specific registry constants.
2. Owner, inventory, cargo state, job, collection target, work origin, storage,
   exclusions, blocking reason, and worker-ticket coordinates survive entity
   NBT round trips.
3. Generic, hostile, fall, fire, and explosion damage never reduce health.
4. The helper is not attackable, cannot be seen as an enemy, and cannot attack.
5. The helper contains no melee, owner-following, rescue, threat, random-wander,
   float, or cross-dimension transfer logic. A stopped helper stops navigation.
6. Normal gameplay removal is owner dismissal. Dismissal drops inventory once,
   returns one canonical item, clears the active-worker record, releases every
   worker chunk ticket, and discards the entity.
7. Target selection and execution are separate server-authoritative operations:
   - using the controller on a valid block sets or replaces the exact block ID
     and bounded work origin;
   - target selection transitions to `READY` and never implicitly starts work;
   - **Start Job** transitions a valid configured worker to active collection;
   - **Stop Job** cancels navigation, collection, reservations, watchdog state,
     and worker tickets while retaining target and storage configuration;
   - repeated Start and Stop operations are idempotent; and
   - Clear Target removes the target and transitions to `IDLE`.
8. The controller dashboard displays the authoritative job state, activity,
   target, storage, current target and work position, inventory usage, worker
   tickets, replan attempts, last-progress age, and blocking reason.
9. Every placement, target, storage, exclusion, start, stop, clear, cargo,
   blocked, deposit, and dismissal action produces visible success or failure
   feedback. No player action may silently no-op.
10. Base inventory capacity is 27 slots. One Cargo Upgrade expands it to 54
    slots. Additional upgrades are rejected.
11. Collection uses worker navigation, scans only a bounded area, refuses
    unloaded candidates, block entities, excluded types, and unbreakable blocks,
    and requires `mobGriefing`.
12. The planner treats the resource position and navigation destination as
    separate values. It selects a collision-free, supported work position
    adjacent to the resource rather than pathing into the solid target block.
13. The helper must be within interaction range and have an unobstructed outline
    ray to the target before collection. It cannot mine through walls.
14. The planner predicts drops with the worker tool and does not break a target
    unless every predicted stack fits. Successful breaking inserts exactly
    those drops.
15. A full inventory switches to deposit when valid storage exists. Missing,
    invalid, cross-dimension, or full storage blocks the job with an exact reason
    and without deleting inventory.
16. A navigation watchdog retries stalled targets a bounded number of times and
    then enters `BLOCKED` with a visible reason. It contains no combat suspension
    or quiet-period state.
17. Entity-owned NeoForge chunk tickets keep the current helper area plus
    relevant work, storage, and target chunks ticking while an active job runs.
    Stop, clear, and dismissal release them.
18. Canonical recipe and asset contracts expose exactly the helper, controller,
    and cargo upgrade. Tier recipes, models, translations, and creative entries
    are absent. The sole legacy asset is the base-item compatibility model.

## Verification

Required commands under Java 21:

```sh
./gradlew test
./gradlew runGameTestServer
```

GameTests cover persistence without tier data, damage immunity, hostile-target
rejection, no following or dimension transfer, dismissal conservation and
ticket cleanup, offline collection, deposit, storage failure, inventory
conservation, exclusions, cargo limits, traversal, watchdog behavior, target
replacement, explicit Start/Stop, idempotency, retained configuration, missing-
target rejection, and target clearing. JUnit contracts cover recipe/asset
cardinality, removal of rescue/combat/following architecture, explicit dashboard
controls, visible acknowledgements, and separate target/work positions.

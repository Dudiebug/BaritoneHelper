# Baritone Helper executable specification

## Product identity

- Display name: **Baritone Helper**
- Mod ID and resource namespace: `baritonehelper`
- Java package: `dev.dudie.baritonehelper`
- Public items: `baritonehelper:worker`,
  `baritonehelper:worker_controller`, and `baritonehelper:cargo_upgrade`
- Public entity: `baritonehelper:worker`

A hidden `buddybot:buddy_bot` entity-type alias is retained only to load
already-placed base entities. Legacy tier fields are ignored. Mk II and Mk III
item stacks are not migrated.

## Required behavior

1. There is one owner-bound Resource Worker with no tier constructor, tier
   accessor, tier NBT, or tier-specific registry constants.
2. Owner, inventory, cargo state, job, collection target, work origin, storage,
   exclusions, and worker-ticket coordinates survive entity NBT round trips.
3. Generic, mob, fall, fire, and explosion damage never reduce worker health.
4. The worker is not attackable, cannot be seen as an enemy, and cannot attack.
5. The worker contains no melee, owner-following, rescue, threat, or
   cross-dimension transfer logic. An idle worker stops navigation.
6. Normal gameplay removal is owner dismissal. Dismissal drops inventory
   contents once, returns one base worker item, clears the active-worker record,
   releases every worker chunk ticket, and discards the entity.
7. The Worker Controller:
   - assigns an exact block registry ID and bounded work origin;
   - assigns same-dimension `Container` storage;
   - toggles block-type exclusions;
   - pauses/resumes jobs; and
   - opens the owned worker inventory.
8. Base inventory capacity is 27 slots. One Cargo Upgrade expands it to 54
   slots. Additional cargo upgrades are rejected.
9. Collection uses worker navigation, scans only a bounded area, refuses
   unloaded candidates, block entities, excluded types, and unbreakable blocks,
   and requires `mobGriefing`.
10. The planner predicts drops with the worker tool and does not break a target
    unless every predicted stack fits. Successful breaking inserts exactly
    those drops.
11. A full inventory switches to deposit when valid storage exists. Missing,
    invalid, cross-dimension, or full storage blocks the job without deleting
    inventory.
12. A navigation watchdog temporarily rejects stalled targets and resumes
    bounded scanning instead of accumulating dead combat-suspension or
    quiet-period state.
13. Entity-owned NeoForge chunk tickets keep the current 3×3 worker area plus
    relevant work/storage chunks ticking for offline work. Dismissal releases
    them.
14. Recipe and asset contracts expose exactly the worker, controller, and cargo
    upgrade. Tier recipes, models, translations, and creative-tab entries are
    absent.

## Verification

Required commands under Java 21:

```sh
./gradlew test
./gradlew runGameTestServer
```

The GameTest suite covers NBT persistence without tier data, damage immunity,
hostile-target rejection, idle non-following behavior, and dismissal
conservation/ticket cleanup. JUnit contracts cover item/recipe/asset cardinality
and removal of the rescue/tier/combat architecture.

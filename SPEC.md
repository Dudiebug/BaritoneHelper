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
   exclusions, and worker-ticket coordinates survive entity NBT round trips.
3. Generic, hostile, fall, fire, and explosion damage never reduce health.
4. The helper is not attackable, cannot be seen as an enemy, and cannot attack.
5. The helper contains no melee, owner-following, rescue, threat, random-wander,
   float, or cross-dimension transfer logic. An idle helper stops navigation.
6. Normal gameplay removal is owner dismissal. Dismissal drops inventory once,
   returns one canonical item, clears the active-worker record, releases every
   worker chunk ticket, and discards the entity.
7. The Worker Controller:
   - assigns an exact block registry ID and bounded work origin;
   - assigns same-dimension `Container` storage;
   - toggles block-type exclusions;
   - pauses and resumes jobs; and
   - opens the owned helper inventory.
8. Base inventory capacity is 27 slots. One Cargo Upgrade expands it to 54
   slots. Additional upgrades are rejected.
9. Collection uses worker navigation, scans only a bounded area, refuses
   unloaded candidates, block entities, excluded types, and unbreakable blocks,
   and requires `mobGriefing`.
10. The helper must be within 1.5 blocks and have an unobstructed outline ray to
    the target before collection. It cannot mine through walls.
11. The planner predicts drops with the worker tool and does not break a target
    unless every predicted stack fits. Successful breaking inserts exactly
    those drops.
12. A full inventory switches to deposit when valid storage exists. Missing,
    invalid, cross-dimension, or full storage blocks the job without deleting
    inventory.
13. A navigation watchdog temporarily rejects stalled targets and resumes
    bounded scanning without combat-suspension or quiet-period state.
14. Entity-owned NeoForge chunk tickets keep the current 3×3 helper area plus
    relevant work, storage, and target chunks ticking for offline work.
    Dismissal releases them.
15. Canonical recipe and asset contracts expose exactly the helper, controller,
    and cargo upgrade. Tier recipes, models, translations, and creative entries
    are absent. The sole legacy asset is the base-item compatibility model.

## Verification

Required commands under Java 21:

```sh
./gradlew test
./gradlew runGameTestServer
```

GameTests cover complete persistence without tier data, damage immunity,
hostile-target rejection, no owner following or dimension transfer, dismissal
conservation and ticket cleanup, offline collection, deposit, full-storage
failure, full-inventory conservation, exclusions, cargo limits, traversal, and
the unreachable-target watchdog. JUnit contracts cover canonical
item/recipe/asset cardinality and removal of rescue, tier, combat, following,
and idle autonomous AI architecture.

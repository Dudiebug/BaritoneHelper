# Baritone Helper

Baritone Helper is a NeoForge 1.21.1 mod that adds one persistent,
owner-bound **Baritone Helper** dedicated to resource collection. It collects a
configured block type, carries the resulting items, and deposits them into
assigned storage.

The former BuddyBot rescue companion has been removed. There are no tiers,
combat behaviors, threat detection, owner-following goals, rescue blocks,
rescue cooldowns, or cross-dimension owner teleports.

## Requirements

- Minecraft 1.21.1
- NeoForge 21.1.248 or newer in the 21.1 line
- Java 21
- The mod installed on the server and every connecting client

## Canonical items

Baritone Helper exposes exactly three normal gameplay items:

- `baritonehelper:baritone_helper` — places the collector.
- `baritonehelper:worker_controller` — configures collection, storage,
  exclusions, pause/resume state, and inventory access.
- `baritonehelper:cargo_upgrade` — increases capacity from 27 to 54 slots.

The old base `buddybot:buddy_bot` item and entity IDs are retained only as
hidden compatibility aliases. They have no recipe or creative-tab entry and
resolve into the canonical Baritone Helper behavior. Mk II and Mk III items are
not migrated.

## Use

1. Place a Baritone Helper. Each player may own one active helper.
2. Use the Worker Controller on a block to assign that exact block type for
   collection. The clicked position becomes the center of the bounded work
   area.
3. Sneak-use the controller on a chest, barrel, or another vanilla
   `Container` block entity to assign deposit storage.
4. Sneak-use the controller on a non-container block to toggle that block type
   in the exclusion set.
5. Use the controller in the air to pause or resume the current job.
6. Use the controller directly on the Baritone Helper to open its inventory.
7. Sneak-right-click the Baritone Helper to dismiss it. Stored contents are
   dropped once, one canonical Baritone Helper item is returned, and all worker
   chunk tickets are released.

Baritone Helper is invulnerable, cannot be selected as an attack target, never
fights, and never follows or changes dimensions with its owner. When idle, it
has no movement goal and stays where it was placed. Entity-owned chunk tickets
allow active jobs to continue while the owner is offline.

Collection is server-authoritative, respects `mobGriefing`, refuses block
entities and unbreakable blocks, scans a bounded area, and uses normal worker
navigation. The helper must be adjacent to and have a clear block ray to a
target before collecting it. It predicts drops and refuses to break a block
unless every resulting stack fits. Missing or full storage never deletes cargo.
A watchdog temporarily rejects stalled or unreachable targets and resumes
bounded scanning.

## Build and verification

```sh
./gradlew test
./gradlew runGameTestServer
./gradlew build
```

The GitHub Actions workflow runs all three commands under Java 21. See
[SPEC.md](SPEC.md) for the acceptance contract and [EVIDENCE.md](EVIDENCE.md)
for verification evidence.

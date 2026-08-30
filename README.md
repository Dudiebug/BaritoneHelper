# Baritone Helper

Baritone Helper is a NeoForge 1.21.1 mod that adds one persistent,
owner-bound **Resource Worker**. The worker is dedicated to collecting a
configured block type and depositing the results into assigned storage.

The former BuddyBot rescue companion has been removed. There are no tiers,
combat behaviors, threat detection, owner-following goals, rescue blocks,
rescue cooldowns, or cross-dimension owner teleports.

## Requirements

- Minecraft 1.21.1
- NeoForge 21.1.248 or newer in the 21.1 line
- Java 21
- The mod installed on the server and every connecting client

## Items

Baritone Helper exposes exactly three gameplay items:

- **Resource Worker** — places the single worker entity.
- **Worker Controller** — configures collection, storage, exclusions, and
  pause/resume state; using it on the worker opens the worker inventory.
- **Cargo Upgrade** — increases worker capacity from 27 to 54 slots.

Mk II and Mk III items are intentionally not migrated.

## Use

1. Place a Resource Worker. Each player may own one active worker.
2. Use the Worker Controller on a block to assign that block type for
   collection. The clicked position becomes the center of the bounded work area.
3. Sneak-use the controller on a chest, barrel, or other vanilla
   `Container` block entity to assign deposit storage.
4. Sneak-use the controller on a non-container block to toggle that block type
   in the exclusion set.
5. Use the controller in the air to pause or resume the current job.
6. Use the controller directly on the worker to open its inventory.
7. Sneak-right-click your worker to dismiss it. Its stored contents are dropped
   once, one base Resource Worker item is returned, and its chunk tickets are
   released.

The worker is invulnerable, cannot be selected as an attack target, never
fights, and never follows or changes dimensions with its owner. When idle it
stays where it was placed. Registered worker chunk tickets allow active jobs to
continue while the owner is offline.

Collection is server-authoritative, respects `mobGriefing`, refuses block
entities and unbreakable blocks, verifies that all predicted drops fit before
breaking a block, and retains all items if storage is missing or full.

## Build and verification

```sh
./gradlew test
./gradlew runGameTestServer
./gradlew build
```

The GitHub Actions workflow runs all three commands under Java 21. See
[SPEC.md](SPEC.md) for the acceptance contract and [EVIDENCE.md](EVIDENCE.md)
for the verification status.

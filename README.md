# Baritone Helper

Baritone Helper is a NeoForge 1.21.1 mod that adds one persistent,
owner-bound collector. It gathers a configured block type, carries the drops,
and deposits them into assigned storage.

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
- `baritonehelper:worker_controller` — configures and controls the collector.
- `baritonehelper:cargo_upgrade` — increases capacity from 27 to 54 slots.

The old base `buddybot:buddy_bot` item and entity IDs remain only as hidden
compatibility aliases. They have no recipe or creative-tab entry. Mk II and
Mk III items are not migrated.

## Controller workflow

1. Place a Baritone Helper. Each player may own one active helper.
2. Use the Worker Controller on a collectible block. This **sets or replaces**
   the target and work-area origin; it does not silently start the worker.
3. The controller dashboard opens and shows the authoritative server state.
   Press **Start Job** to begin collection.
4. Press **Stop Job** at any time to stop navigation and collection. The target
   and storage assignment remain configured so the job can be restarted.
5. Use the controller in the air, or use it directly on the helper, to reopen
   the dashboard.
6. Sneak-use the controller on a chest, barrel, or another vanilla `Container`
   block entity to assign deposit storage.
7. Sneak-use the controller on a non-container block to toggle that block type
   in the exclusion set.
8. Right-click the helper with an empty hand to open its inventory. Sneak-right-
   click it to dismiss it.

The dashboard reports the configured target, storage coordinates, lifecycle,
current activity, current destination, inventory usage, active worker tickets,
replan count, and any blocking reason. Every controller action also produces a
concise chat acknowledgement or an exact failure message.

## Worker behavior

The helper is invulnerable, cannot be selected as an attack target, never
fights, and never follows or changes dimensions with its owner. An idle or
stopped helper has no movement goal and releases active worker tickets.

Collection is server-authoritative and respects `mobGriefing`. The planner
scans a bounded area, rejects block entities, exclusions, unloaded candidates,
and unbreakable blocks, then navigates to a standable position beside the
resource instead of trying to path into the solid resource block. Collection
requires interaction distance and a clear block ray. A watchdog retries stalled
paths and eventually exposes a visible blocking reason rather than failing
silently.

The worker predicts drops and refuses to break a target unless every resulting
stack fits. A full inventory triggers a return to assigned storage. Missing,
invalid, cross-dimension, or full storage never deletes cargo. Active jobs keep
the relevant chunks ticking for offline work.

## Build and verification

```sh
./gradlew test
./gradlew runGameTestServer
./gradlew build
```

GitHub Actions runs all three commands under Java 21. See [SPEC.md](SPEC.md) for
the acceptance contract and [EVIDENCE.md](EVIDENCE.md) for verification status.

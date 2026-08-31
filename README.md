# Baritone Helper 2.0.0

Baritone Helper is a collector-only Minecraft 1.21.1 / NeoForge 21.1.248 mod.
It ships one universal JAR containing a relocated, server-side Baritone-derived
path planner and executor. A worker is invulnerable, owner-bound, and does not
fight, follow, rescue, wander, or change dimensions.

## Requirements

- Minecraft 1.21.1
- NeoForge 21.1.248 (21.1 line)
- Java 21
- Install the same universal JAR on the dedicated server and clients

## Items and migration

- `baritonehelper:baritone_helper` places the worker.
- `baritonehelper:worker_controller` opens its dashboard.
- `baritonehelper:cargo_upgrade` expands storage from 27 to 54 slots.

Hidden `buddybot:buddy_bot` item/entity aliases keep old base stacks and placed
entities loadable. v1 owner, inventory, cargo, target, storage, exclusions, and
active-worker records migrate to schema 2; legacy tier/rescue data and Mk II/Mk
III items are ignored. A migrated target is `READY` and never starts work by
itself.

## Controller workflow

1. Place a worker and use the controller in the air, or on the worker, to open
   the dashboard.
2. In **Job**, search the exact registry ID or localized name and click a result.
   Selecting another result replaces the previous target. No ordinary world
   click can configure a target type.
3. Set a finite amount (1–1,000,000) or **Unlimited**, then press **Start Job**.
   Progress counts successfully broken source blocks, not drop quantities.
4. Use **Areas** to edit X/Y/Z and horizontal/vertical radii, use the worker or
   player position, or arm **Select point in world** before clicking a block.
5. Use **Storage** to arm **Select storage**, then click a container. The worker
   preserves cargo if storage is missing, in another dimension, or full.
6. Use **Pathing** to toggle obstruction breaking, placement, bridging,
   pillaring, parkour, water routes, safer routing, and destructive-routing
   policy. Only real inventory blocks may be placed.
7. **Stop Job** synchronously cancels pathing, breaking, pickup, reservations,
   watchdog state, and worker tickets while retaining configuration. **Reset
   Progress** is required before rerunning a completed finite goal.

The **Activity Log** shows bounded timestamped state transitions and resume
notes. The status area reports the server-authoritative job/activity/runtime,
current destination, progress, inventory, worker/search ticket counts, frontier
coverage, candidate counts, path state/cost/sample, replan age, and exact
blocking reason. Area presets provide horizontal radii 32/64/128/256/512 and
vertical radii 16/32/64/128 without hiding the editable exact values.

## Safety and operation

Targets are found by an incremental chunk frontier, not an O(radius³) repeated
scan. Frontier chunks inside the configured area are requested, observed until
loaded, scanned with a 4,096-position per-worker tick budget, and released.
Candidate interaction stances use hypothetical eye position, reach, support,
collision, and line of sight; the worker does not need to see the resource from
its current position before considering it. Work areas default to the maximum
512 horizontal / 128 vertical blocks and are limited to 8–512 / 4–128. No-work
zones support
`NO_MODIFY` (walk-through only) and `NO_ENTER` (path-forbidden) modes and are
enforced by scanning, path costs, interaction, placement, pickup, and storage
validation.

Mining is real progressive server interaction: tool selection, hardness, speed,
enchantments, durability, break animation, game rules, hooks, and normal
`ItemEntity` drops are preserved. The worker physically collects those drops.
Baritone movement handles ordinary travel, jumps, parkour, pillaring, bridging,
obstruction clearing, and water routes from immutable loaded-chunk/inventory
snapshots. Path state is explicit (`CALCULATING`, `PATH_FOUND`, `EXECUTING`,
`ARRIVED`, `NO_PATH`, `CANCELLED`, or `FAILED`), and stale asynchronous results
cannot revive a cancelled goal. Worker route tickets are capped at 16 and
frontier search tickets at 4, so an active job can continue while its owner is
offline without unbounded chunk retention.

## Build and verification

```sh
./gradlew clean test --no-daemon --console=plain
./gradlew runGameTestServer --no-daemon --console=plain
./gradlew build --no-daemon --console=plain
```

The build produces one universal runtime JAR and a source JAR. See
[SPEC.md](SPEC.md), [EVIDENCE.md](EVIDENCE.md), and
[docs/v2-architecture.md](docs/v2-architecture.md) for the acceptance contract,
history audit, and verification record.

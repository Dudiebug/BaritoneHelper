# Baritone Helper

[![Build and GameTest](https://github.com/Dudiebug/BaritoneHelper/actions/workflows/build.yml/badge.svg)](https://github.com/Dudiebug/BaritoneHelper/actions/workflows/build.yml)
[![Latest release](https://img.shields.io/github/v/release/Dudiebug/BaritoneHelper)](https://github.com/Dudiebug/BaritoneHelper/releases)
[![Minecraft 1.21.1](https://img.shields.io/badge/Minecraft-1.21.1-3C8527)](https://www.minecraft.net/)
[![License](https://img.shields.io/badge/license-MIT%20%2B%20LGPL--3.0--or--later-blue)](THIRD_PARTY_NOTICES.md)

Baritone Helper is an autonomous resource-worker mod for Minecraft 1.21.1 on NeoForge. Place an owner-bound worker, select a block target and safety policy, then let its relocated server-side Baritone-derived runtime search, pathfind, mine, collect, and deposit resources. Work is bounded by default; explicit **Roam** mode allows continuing frontier exploration.

> Baritone Helper is not a drop-in Baritone client. It packages a server-oriented Baritone-derived runtime behind a persistent worker entity and controller workflow.

## Download

Use the [GitHub releases page](https://github.com/Dudiebug/BaritoneHelper/releases). Install the same universal JAR on the dedicated server and on clients that use the controller UI.

## Features

- **Baritone-owned exploration** — one long-lived `MineProcess` coordinates known targets, exhaustive bounded coverage, and Roam frontiers.
- **Shared world knowledge** — target-aware per-dimension coverage persists safely while worker policy, jobs, paths, and progress remain isolated.
- **Real mining and drops** — block hardness, tools, enchantments, durability, progressive break timing, hooks, and normal multi-drops are preserved.
- **Bounded concurrency and tickets** — separate four-thread path and two-thread scan executors use fenced publication and bounded sliding view/simulation footprints.
- **Work boundaries** — configure exact work-area coordinates plus `NO_MODIFY` and `NO_ENTER` zones; Roam removes only the work-area boundary.
- **Canonical cargo and storage** — workers provide 27 inventory slots, expandable to 54, and preserve cargo when assigned storage is missing or full.
- **Protocol-4 remote control** — owners address workers by UUID and dimension, with idempotent actions, revisions, sequences, and live path/search telemetry.
- **Safe packed lifecycle** — transactional pickup creates exactly one owner-bound packed worker and restores it stopped with the same UUID.
- **Offline operation** — an already active worker can remain operational when its owner disconnects.

## Requirements

| Component | Requirement |
| --- | --- |
| Minecraft | 1.21.1 |
| NeoForge | 21.1.248 or newer compatible 21.1 build |
| Java | 21 |
| Install side | Dedicated server and clients |

## Quick start

1. Install Baritone Helper on the server and clients.
2. Craft or obtain `baritonehelper:baritone_helper` and `baritonehelper:worker_controller`.
3. Place the worker; it becomes bound to its owner.
4. Open its dashboard with the controller.
5. In **Job**, select an exact block and finite amount or **Unlimited**.
6. In **Area & Safety**, keep bounded **Work Area** or explicitly opt into **Roam**, then configure exclusions and no-work zones.
7. Optionally select a storage container and review pathing settings.
8. Start the job and use the persistent header, telemetry, and activity history to monitor or diagnose it.

Physical container opening and world-point selection require the player to be in the worker's dimension. Configuration, status, start/stop, storage assignment, and transactional pickup remain available remotely.

For a complete walkthrough, see the [User Guide](docs/user-guide.md).

## Core behavior

A worker does not fight, follow its owner, wander while idle, rescue players, or teleport between dimensions. Collection progress counts successfully broken source blocks rather than the number of resulting item drops.

Stopping a job synchronously cancels pathing, scanning generations, breaking, pickup, reservations, and runtime tickets while retaining configuration. Idle, blocked, completed, removed, and packed workers retain no forced view or simulation tickets.

Loaded chunk palettes are captured on the server thread, scanned off-thread as immutable snapshots, and published only when lifecycle epoch, chunk revision, scan generation, and job generation still match. Every world interaction is revalidated on the server thread against enter/modify policy immediately before commit.

## Migration

Existing 3.1.0 saves default to bounded `WORK_AREA`; roaming is never enabled silently. Hidden `buddybot:buddy_bot` aliases keep legacy base stacks and placed entities loadable where supported. A migrated configured worker enters a safe ready state and does not automatically begin work.

Packed items use a versioned network-synchronized data component. Newer or malformed schemas and duplicate worker UUIDs are rejected; blank legacy worker items retain fresh-worker behavior.

## Documentation

- [Documentation index](docs/README.md)
- [User guide](docs/user-guide.md)
- [Architecture](docs/architecture.md)
- [3.2 architecture notes](docs/3.2-architecture.md)
- [3.2 executable specification](SPEC-3.2.0-rc.1.md)
- [3.2 verification evidence](EVIDENCE-3.2.0-rc.1.md)
- [Testing](docs/development/testing.md)
- [Release process](docs/development/release-process.md)
- [Changelog](CHANGELOG.md)

Historical design and verification documents are retained under [`docs/archive/`](docs/archive/) and are not the current product contract.

## Building from source

With Java 21 selected:

```sh
./gradlew clean test --no-daemon --console=plain
./gradlew runGameTestServer --no-daemon --console=plain
./gradlew build --no-daemon --console=plain
```

The build produces one universal runtime JAR and one source JAR in `build/libs/`.

## Contributing

Read [CONTRIBUTING.md](CONTRIBUTING.md) before opening a pull request. Changes to worker lifecycle, networking, world scanning, pathing, inventory ownership, or Baritone-derived code are expected to include appropriate automated coverage.

## Licensing and attribution

Project-authored code is released under the [MIT License](LICENSE). The relocated Baritone-derived subset is distributed under LGPL-3.0-or-later and retains upstream licensing requirements. See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md), [`LICENSES/`](LICENSES/), and [ASSET_CREDITS.md](ASSET_CREDITS.md) for details.

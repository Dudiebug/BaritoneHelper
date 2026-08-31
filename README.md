# Baritone Helper

[![Build and GameTest](https://github.com/Dudiebug/BaritoneHelper/actions/workflows/build.yml/badge.svg)](https://github.com/Dudiebug/BaritoneHelper/actions/workflows/build.yml)
[![Latest release](https://img.shields.io/github/v/release/Dudiebug/BaritoneHelper)](https://github.com/Dudiebug/BaritoneHelper/releases/latest)
[![Minecraft 1.21.1](https://img.shields.io/badge/Minecraft-1.21.1-3C8527)](https://www.minecraft.net/)
[![License](https://img.shields.io/badge/license-MIT%20%2B%20LGPL--3.0--or--later-blue)](THIRD_PARTY_NOTICES.md)

Baritone Helper is an autonomous resource-worker mod for Minecraft 1.21.1 on NeoForge. Place a worker, configure a block target, work area, storage destination, and pathing policy from the controller, then let the worker search, pathfind, mine, collect, and deposit resources using a relocated server-side Baritone-derived runtime.

Workers are owner-bound, invulnerable, collector-only, and can continue an active job while their owner is offline.

> Baritone Helper is not a drop-in Baritone client. It packages a server-oriented Baritone-derived runtime behind a persistent worker entity and controller workflow.

## Download

Use the [latest GitHub release](https://github.com/Dudiebug/BaritoneHelper/releases/latest). Install the same universal JAR on the dedicated server and on connecting clients that use the controller UI.

## Features

- **Autonomous collection** — select an exact Minecraft block type and a finite or unlimited target amount.
- **Baritone-derived pathing** — workers can navigate ordinary terrain, jump, parkour, bridge, pillar, clear allowed obstructions, and use water routes according to the configured policy.
- **Long-range discovery** — loaded world data is scanned asynchronously so workers can discover resources beyond their immediate line of sight.
- **Real mining and drops** — block hardness, tools, enchantments, durability, break progress, server hooks, and normal item drops are preserved.
- **Persistent cargo** — workers provide 27 inventory slots, expandable to 54 with a cargo upgrade.
- **Assigned storage** — collected resources can be deposited into a configured container without deleting cargo when storage is unavailable or full.
- **Work boundaries** — configure exact work-area coordinates and radii plus `NO_MODIFY` and `NO_ENTER` zones.
- **Remote control** — the owner can inspect status, stop/start work, adjust configuration, and open the worker inventory from the controller while in the same dimension.
- **Offline operation** — an already active worker can remain operational when its owner disconnects.
- **Server-authoritative control** — ownership and dashboard actions are validated on the server.

## Requirements

| Component | Requirement |
| --- | --- |
| Minecraft | 1.21.1 |
| NeoForge | 21.1.248 or newer compatible 21.1 build |
| Java | 21 |
| Install side | Dedicated server and clients |

## Quick start

1. Install Baritone Helper on the server and clients, then start Minecraft normally.
2. Craft or obtain `baritonehelper:baritone_helper` and `baritonehelper:worker_controller`.
3. Place the worker in the world. It becomes bound to its owner.
4. Use the controller to open the worker dashboard.
5. In **Job**, search for the desired block by localized name or registry ID and set a finite amount or **Unlimited**.
6. In **Areas**, choose the work center and horizontal/vertical radii.
7. Optionally use **Storage** to select a container for automatic deposits.
8. Review **Pathing** if the worker may need to bridge, pillar, break obstructions, parkour, or route through water.
9. Press **Start Job**.
10. Use the dashboard status and **Activity Log** to see what the worker is doing or why it is blocked.

For a complete walkthrough, see the [User Guide](docs/user-guide.md).

## Core behavior

A worker does not fight, follow its owner, wander while idle, rescue players, or teleport between dimensions. Its job is resource collection.

Collection progress counts successfully broken source blocks rather than the number of resulting item drops. Stopping a job cancels active pathing and interaction state while retaining the target, area, storage, and pathing configuration. A completed finite goal must have its progress reset before it can be run again.

The worker inventory is the canonical inventory used by mining, tool selection, block placement, pickup, persistence, and deposits. If storage cannot be used, cargo stays in the worker rather than being discarded.

## Migration

Baritone Helper keeps hidden `buddybot:buddy_bot` compatibility aliases so old base BuddyBot stacks and placed entities can load. Legacy owner, inventory, cargo, target, storage, exclusion, and active-worker data are migrated where supported. Removed tier, rescue, Mk II, and Mk III behavior is not restored.

A migrated configured worker enters a safe ready state and does not automatically begin a job merely because an old target existed.

## Documentation

- [Documentation index](docs/README.md)
- [User guide](docs/user-guide.md)
- [Architecture](docs/architecture.md)
- [Testing](docs/development/testing.md)
- [Release process](docs/development/release-process.md)
- [3.1.0 implementation record](docs/releases/3.1.0-implementation.md)
- [3.1.0 verification record](docs/releases/3.1.0-verification.md)
- [Changelog](CHANGELOG.md)

Historical design and verification documents are retained under [`docs/archive/`](docs/archive/) for reference and are not the current product contract.

## Building from source

With Java 21 selected:

```sh
./gradlew clean test --no-daemon --console=plain
./gradlew runGameTestServer --no-daemon --console=plain
./gradlew build --no-daemon --console=plain
```

The build produces one universal runtime JAR and one source JAR in `build/libs/`.

## Contributing

Read [CONTRIBUTING.md](CONTRIBUTING.md) before opening a pull request. Changes to the worker lifecycle, networking, world scanning, pathing, inventory ownership, or vendored Baritone-derived code are expected to include appropriate automated coverage.

## Licensing and attribution

Project-authored code is released under the [MIT License](LICENSE). The relocated Baritone-derived subset is distributed under LGPL-3.0-or-later and retains upstream licensing requirements. See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md), [`LICENSES/`](LICENSES/), and [ASSET_CREDITS.md](ASSET_CREDITS.md) for details.

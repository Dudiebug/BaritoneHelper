# Baritone Helper user guide

Baritone Helper provides one persistent, owner-bound worker for automated resource collection. The worker is controlled through the Worker Controller and performs collection using server-authoritative rules.

## Installation

Baritone Helper targets:

- Minecraft 1.21.1
- NeoForge 21.1.248 or a compatible newer 21.1 build
- Java 21

Install the same Baritone Helper JAR on the dedicated server and clients that will connect and use the controller UI.

## Items

- `baritonehelper:baritone_helper` — places the worker.
- `baritonehelper:worker_controller` — opens and controls the worker dashboard.
- `baritonehelper:cargo_upgrade` — expands worker storage from 27 to 54 slots.

## Deploying a worker

Place the Baritone Helper item in the world. The resulting worker is bound to its owner. A stopped or idle worker does not follow, fight for, rescue, or teleport to its owner.

Use the Worker Controller in the air or on the worker to open the dashboard.

## Job tab

### Selecting a target

Search for the block by localized name, namespace, or exact registry ID and select the desired result. Selecting another result replaces the current target.

Target selection configures the worker; it does not itself begin mining.

### Amount

Choose either:

- a finite source-block count from 1 to 1,000,000; or
- **Unlimited**.

Finite progress counts successfully broken source blocks, not the number of dropped items. When a finite goal is complete, use **Reset Progress** before starting that goal again.

### Starting and stopping

**Start Job** begins collection with the current server-authoritative configuration.

**Stop Job** synchronously cancels current pathing, breaking, pickup, reservations, and watchdog state while retaining the configured target, area, storage, exclusions, and pathing policy.

## Areas tab

The work area has an exact center plus horizontal and vertical radii. You can enter coordinates directly, copy the player or worker position, or arm world-point selection and click a block.

Available convenience presets include horizontal radii 32, 64, 128, 256, and 512 and vertical radii 16, 32, 64, and 128. Exact editable values remain authoritative.

### No-work zones

No-work zones can be enabled within a dimension and use one of two modes:

- `NO_MODIFY` — the worker may route through the zone but cannot modify blocks there.
- `NO_ENTER` — the zone is forbidden to pathing as well as modification.

Zones are applied to discovery, path costs, interaction, placement, pickup, and storage validation where relevant.

## Storage tab

Arm **Select storage**, then click a supported container. When the worker deposits, normal cargo conservation rules apply.

If storage is missing, full, invalid, or in another dimension, cargo remains in the worker rather than being deleted.

## Pathing tab

Pathing policy controls whether the worker may use capabilities such as:

- obstruction breaking;
- block placement;
- bridging;
- pillaring;
- parkour;
- water routes;
- safer routing;
- destructive-routing policy.

Placed blocks must come from the worker's real inventory. Mining and obstruction clearing use normal interaction/tool rules rather than predicted world edits.

## Worker inventory

The worker itself is the canonical 27/54-slot container. The same inventory is used for:

- mining tools;
- placeable blocks;
- picked-up drops;
- persistence;
- storage deposits;
- the remote inventory screen.

The owner may open that inventory remotely from the controller while in the same dimension. Non-owners and cross-dimension remote access are rejected.

## Offline operation

An active worker can continue its job after its owner disconnects. Worker-loaded world state and chunk ownership are bounded by the implementation and released as the worker lifecycle requires.

Workers do not automatically move between dimensions to find their owner.

## Activity and status

The dashboard exposes the current job/activity state and a bounded activity log. Depending on state it can also show target/destination, progress, inventory use, search information, path state, replan age, and an exact blocking reason.

Common path states include:

- `CALCULATING`
- `PATH_FOUND`
- `EXECUTING`
- `ARRIVED`
- `NO_PATH`
- `CANCELLED`
- `FAILED`

If the worker appears idle during an active job, check the blocking reason and activity log before changing settings.

## Migration from BuddyBot and older releases

Hidden `buddybot:buddy_bot` aliases preserve compatibility with old base stacks and placed entities. Supported owner, inventory, cargo, target, storage, exclusion, and active-worker data are migrated to the current worker model.

Removed tier/rescue data and Mk II/Mk III content are intentionally not restored. Migrated workers do not automatically start solely because an old target existed.

## Troubleshooting checklist

1. Confirm server and client are using the same Baritone Helper release.
2. Confirm Minecraft/NeoForge versions are supported.
3. Confirm the correct target is selected and the finite goal is not already complete.
4. Check the work-area center/radii and any no-work zones.
5. Check pathing policy if the route requires bridging, pillaring, obstruction clearing, parkour, or water traversal.
6. Confirm the worker has required tools or placement blocks.
7. Check storage validity/capacity if the worker is returning cargo.
8. Read the dashboard blocking reason and activity log.
9. If reporting a bug, include the Baritone Helper, Minecraft, and NeoForge versions plus reproduction steps and relevant logs.

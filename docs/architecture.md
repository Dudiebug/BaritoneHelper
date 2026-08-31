# Architecture

This document describes the current high-level Baritone Helper design. Version-specific implementation details and historical acceptance records live under `docs/releases/` and `docs/archive/`.

## Product boundary

Baritone Helper is a collector-only worker system. The persistent worker entity is owner-bound and does not implement combat, owner following, rescue behavior, idle wandering, or automatic cross-dimension owner teleportation.

The controller is the configuration and observability surface. The server remains authoritative for ownership, entity identity, dimension, registry IDs, numeric bounds, configuration revisions, and lifecycle actions.

## Worker lifecycle

Each placed worker owns persistent configuration and inventory state plus transient runtime state while loaded. Starting a job creates/activates the collection runtime. Stopping, completion, dismissal, replacement, and removal cancel transient work and invalidate stale asynchronous results.

Persisted paths are not trusted across restart. Configuration survives; path/search work is rebuilt from current world state.

## Baritone-derived runtime

The production collection path uses a relocated Baritone-derived runtime under `dev.dudie.baritonehelper.internal.baritone`. Active collection uses a long-lived mining process rather than running a separate controller-side path planner.

The relocated namespace prevents collisions with separately installed Baritone code. Upstream-derived files retain their licensing obligations; see `THIRD_PARTY_NOTICES.md`.

## World discovery and concurrency

World/chunk data required by search is captured from server-owned state and represented through immutable or isolated snapshots before background processing. Resource scanning and A* path calculation use separate bounded executors so discovery work cannot starve path calculation.

Asynchronous work is generation-fenced. Cancellation, target replacement, worker removal, and equivalent lifecycle changes invalidate old generations so stale search/path results cannot republish cancelled goals.

## Movement and interaction

Baritone-derived movement produces the worker's active movement/look/input decisions while a job is executing. The movement graph can account for ordinary travel, jumping, parkour, bridging, pillaring, obstruction clearing, and water routing according to configured policy and actual inventory.

Mining uses progressive server interaction and normal game behavior: tool selection, hardness/speed, enchantments, durability, break progress, game/server hooks, and ordinary `ItemEntity` drops. Resources are physically picked up rather than inserted from a predicted drop table.

## Inventory and storage

The worker's 27/54-slot container is canonical. Tool selection, block placement, pickup, NBT persistence, dashboard inventory access, and storage deposits all reference that inventory.

Storage failure is conservative: if the destination is missing, invalid, full, or inaccessible, cargo stays with the worker.

## Work boundaries

Work configuration includes a dimension, center, horizontal/vertical radii, pathing policy, and optional no-work zones. `NO_MODIFY` zones prevent world modification while allowing traversal; `NO_ENTER` zones also prohibit paths through the zone.

Boundary policy is enforced at multiple layers rather than only at target selection so pathing or interaction cannot silently bypass a configured restriction.

## Loaded world ownership

Workers keep a bounded loaded view required for persistent/offline operation and release owned tickets/resources across stop/removal/dismissal and related lifecycle transitions according to their purpose. Vanilla simulation-distance behavior still determines full ticking semantics outside the mod's explicit ownership.

## Networking and dashboard

Client intents carry a worker reference, request identity, and expected configuration revision. The server validates an action and returns an acknowledgement plus a fresh authoritative state snapshot.

The dashboard is therefore a control client, not the source of truth. It can expose current activity, progress, inventory, discovery/path diagnostics, and explicit blocking reasons without allowing the client to directly mutate world/worker state.

## Compatibility

The current schema keeps compatibility paths for supported BuddyBot/base-worker data. Legacy tiers, rescue behavior, and removed Mk II/Mk III content are intentionally outside the current product model.

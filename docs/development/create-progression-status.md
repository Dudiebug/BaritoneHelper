# Create miner progression — status

Branch: `feat/create-miner-progression`. Work in progress; **not released** and not tagged.

## Done

- Create 6.0 (1.21.1) is a required dependency (`neoforge.mods.toml`, `build.gradle`, `gradle.properties`).
- Miner tiers (`worker/MinerTier.java`): Andesite → Brass → Precision.
  - Andesite: 128/64 radius, 70% mining speed, no Roam, pauses when its owner is offline (`OWNER_OFFLINE` block reason), no cargo upgrade.
  - Brass: 256/96, 85%, works offline, accepts cargo.
  - Precision: 512/128, 100%, Roam allowed. This tier is identical to the pre-tier behavior.
  - Saves without a tier load as Precision, so existing workers are not nerfed.
- Newly placed workers start at Andesite with a plain iron pickaxe. The free Efficiency V pickaxe is removed.
- Brass and Precision upgrade kits (Create mechanical crafting). Right-click a worker to advance one tier; tiers cannot be skipped.
- The worker, controller and cargo upgrade recipes now use Create materials.
- Storage goes through the NeoForge item-handler capability, so Create vaults, depots, funnels and similar blocks work as deposit targets.
- GameTest support:
  - `GameTestPlayers.mockServerPlayer` works around Create's login payload crash with vanilla mock players.
  - Long-range test setup pre-generates the worker's chunk window.
  - New `MinerProgressionGameTests` and `MinerTierTest`.
- Docs: README, user guide (tier table, recipes, storage) and CHANGELOG under Unreleased.
- Verified: `./gradlew test`, `./gradlew build`, and `./gradlew runGameTestServer` (61/61) pass.
  - Run GameTests from tmpfs or with `sync-chunk-writes=false`. On btrfs with fsync, the unthrottled test server times out before chunks load, and long-range tests flake.

## Needs to be done

- **Rework progression around the mod's actual systems** instead of only range and speed numbers. The owner says the current tiers miss the technologies already in the mod. Candidates to gate behind Create-crafted modules:
  - pathing abilities (block breaking and placement, bridging, pillaring, parkour, water routes);
  - remote controller and dashboard;
  - Roam;
  - shared world knowledge;
  - no-work zones;
  - packing;
  - offline operation and chunk loading;
  - cargo.
  - Confirm with the owner which systems count, and whether any exist outside this repo.
- Show the miner tier (or unlocked modules) in the dashboard. This needs a new field in the protocol snapshot.
- Real textures for the upgrade kits; they currently borrow Create item models.
- Balance pass on recipes and tier numbers in actual play.
- Mob vulnerability. Workers are still invulnerable; decide whether lower tiers should be killable.
- Deferred: blueprints from Create schematics, builder workers, job sites.
- Separate fix in progress: the inverted Aqua Affinity check in `LivingEntityInteractionManager`.

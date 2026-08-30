# BuddyBot executable specification

Status: approved product plan; executable wording finalized autonomously on 2026-08-29.

## Setup

- Standalone Git repository based on the official NeoForge 1.21.1 ModDevGradle MDK.
- Java 21, Minecraft 1.21.1, NeoForge 21.1.248, JUnit Jupiter 5.11.4.
- No runtime libraries beyond Minecraft and NeoForge.
- Generated project files: sources, resources, unit tests, GameTests, `tools/gauntlet.sh`, and `EVIDENCE.md`.
- No commits are created without a separate request.

## Executable behavior

1. `BuddyBot`, `BuddyBot Mk II`, and `BuddyBot Mk III` have rescue ranges 16, 32, and 64 blocks and strictly cumulative capabilities.
2. Threat order is long fall/void, suffocation/drowning, lava/fire, explosion/projectile, hostile mob, status damage.
3. Four footprint probes select the highest finite landing surface. A cliff is dangerous when at least three of eight probes drop more than four blocks.
4. A ballistic solution has the requested launch speed and intersects the requested target under constant gravity; unreachable trajectories return empty.
5. A temporary block is restored only while its current state is exactly the state BuddyBot placed.
6. The three shaped recipe JSON files exactly implement the approved ingredient patterns and upgrade chain.
7. Using a BuddyBot item creates one owner-bound persistent entity and consumes the item only on success. A live attachment blocks duplicates; a positively verified stale attachment is repaired.
8. Sneak-interacting with your bot returns its tier item and removes the entity. Other players cannot dismiss it.
9. BuddyBot follows its owner, idles while the owner is offline, and rejoins across dimensions.
10. Basic rescues cover hostiles, projectiles, cliffs, cobweb catches, drowning, and suffocation. Mk II adds four-corner fall clutches, fluids/hazards, and support potions. Mk III adds pearl repositioning, slow falling, platforms, short bridges, and explosion shields.
11. World edits require `mobGriefing`, pass NeoForge entity place/break events, exclude block entities/unbreakable blocks, and never overwrite later player edits during cleanup.
12. BuddyBot never cancels player damage, grants invulnerability, or targets a player.

## Failure model

- Duplicate or stale ownership records: attachment lifecycle and stale-repair tests.
- Player-built state overwritten by cleanup: exact-state restoration tests and GameTests.
- Claim or gamerule bypass: denied-edit GameTests and source gate.
- Capability leakage into cheaper tiers: exhaustive tier matrix tests.
- Bad projectile math: reachable, unreachable, and randomized trajectory properties.
- Accidental god mode or PvP aggression: source gate plus lethal-damage/manual runtime scenario.
- Orphaned temporary edits after dismissal/death: cleanup tests and persisted ledger inspection.

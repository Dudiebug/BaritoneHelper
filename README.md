# BuddyBot

BuddyBot is a standalone NeoForge 1.21.1 mod that adds a craftable, owner-bound
half-robotic Steve companion. It tries to save its owner through visible physical
actions—movement, combat, blocks, projectiles, and potions—without cancelling damage
or granting hidden invulnerability.

## Requirements

- Minecraft 1.21.1
- NeoForge 21.1.248 or newer in the 21.1 line
- Java 21
- The mod installed on the server and every connecting client

## Use

Craft a BuddyBot item and use it on a block to spawn the bot. A player may have one
active bot. Sneak-right-click your own bot to dismiss it and recover the matching
item. A bot lost in combat does not return its item.

| Tier | Range | Highlights |
|---|---:|---|
| BuddyBot | 16 | Follows, fights attackers, body-blocks projectiles, probes cliffs, places cobwebs, clears safe trapping blocks |
| Mk II | 32 | Adds four-corner landing prediction, water/Nether-vine clutches, hazard intervention, support potions |
| Mk III | 64 | Adds calculated pearl movement, slow falling, catch platforms, and explosion shields |

BuddyBot respects `mobGriefing` and NeoForge placement/break cancellation events.
Temporary blocks are removed only while they still match what the bot placed.
Rescues are best-effort and cannot guarantee survival.

## Build and test

```sh
./gradlew build
./gradlew runGameTestServer
```

See [SPEC.md](SPEC.md) for executable acceptance criteria and [ASSET_CREDITS.md](ASSET_CREDITS.md)
for the bundled skin source.

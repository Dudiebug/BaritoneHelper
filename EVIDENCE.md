# BuddyBot verification evidence

Verified on 2026-08-29 with Java 21.0.12, Minecraft 1.21.1, NeoForge
21.1.248, ModDevGradle 2.0.144, and Gradle 9.2.1.

## Automated checks

| Check | Result |
|---|---|
| `tools/gauntlet.sh` from a clean build | PASS |
| JUnit policy/math/recipe suite | PASS — 9 tests |
| NeoForge `runGameTestServer` | PASS — 2 required tests |
| Dedicated `runServer --nogui` startup | PASS — reached `Done (0.290s)!` |
| Release artifact | PASS — `build/libs/buddybot-1.0.0.jar` |
| Policy/math line coverage | 59/60 lines (98.3%) |
| Skin format | PASS — 64×64 RGBA PNG |
| Forbidden damage/invulnerability source gate | PASS |
| Runtime dependency review | PASS — only NeoForge/Minecraft runtime graph; JUnit is test-only |

The GameTests boot a real NeoForge server, create the registered entity, and verify
spawn health/default tier plus owner/tier NBT round-tripping.

## Fault injection

Three temporary mutations were applied together and then reverted:

1. Basic range changed from 16 to 17.
2. Cliff detection changed from three dangerous probes to four.
3. Temporary restoration stopped checking the current block state.

The suite reported `9 tests completed, 3 failed`, killing all three mutations.
The unmodified clean gauntlet and GameTest server were then rerun successfully.

## Safety evidence

- No handler subscribes to or cancels incoming player damage.
- Player targets are explicitly rejected in both direct targeting and tame-owner
  attack policy.
- Block edits require `mobGriefing`, reject block entities and unbreakable blocks,
  and post NeoForge placement/break events.
- Cleanup compares the current state with the exact placed state. It leaves a
  changed player block untouched.
- Source-dimension temporary blocks are cleaned before a cross-dimension transfer.

## Manual runtime work still required

The container has no graphical Minecraft/display or two authenticated clients, so
two-client rendering/multiplayer checks and the complete hazard matrix were not run.
Until those are performed in Minecraft, rescue effectiveness is **needs runtime
test**. In particular, manually test long-fall timing, near-void recovery, Nether
vine placement, pearl travel, protected-claim event cancellation, deliberate lethal
damage, and skin/model rendering on both clients.

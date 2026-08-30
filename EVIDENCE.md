# Baritone Helper verification evidence

## Implementation evidence

The collector-only conversion removes the former tier, rescue, combat,
threat-detection, owner-following, idle autonomous AI, and dimension-transfer
architecture. The canonical gameplay surface is one Baritone Helper, one Worker
Controller, and one Cargo Upgrade under the `baritonehelper` namespace.

The implementation includes:

- persistent 27/54-slot inventory and cargo state;
- persistent collection job, target, origin, storage, and exclusions;
- bounded target planning and explicit worker navigation;
- adjacency and clear-ray checks that prevent through-wall collection;
- conservation checks before block removal;
- lossless blocked-storage behavior;
- a stalled-target watchdog;
- owner-controlled inventory access and dismissal; and
- entity-owned NeoForge chunk tickets for offline work.

## Automated verification

Verified implementation baseline:

- Commit: `d113c523d0ec1ee34fbe2d6b039677e305dc188d`
- GitHub Actions run: `33294843801`
- Runtime: Temurin Java 21.0.12+1
- Minecraft: 1.21.1
- NeoForge: 21.1.248

Commands completed successfully:

```sh
./gradlew test --no-daemon --console=plain
./gradlew runGameTestServer --no-daemon --console=plain
./gradlew build --no-daemon --console=plain
```

Results:

- Six JUnit recipe, asset, namespace, and source-architecture contracts passed.
- All 13 required NeoForge GameTests passed.
- The release JAR built successfully as `baritonehelper-1.0.0.jar`.

The GameTest suite verifies complete persistence without tier data, damage
immunity, hostile-target rejection, no owner following or cross-dimension
transfer, dismissal conservation and ticket cleanup, offline collection,
deposit, full-storage failure, full-inventory conservation, exclusions, cargo
limits, traversal, and unreachable-target watchdog behavior.

## Compatibility evidence

- The canonical item and entity ID is `baritonehelper:baritone_helper`.
- Hidden `buddybot:buddy_bot` item and entity registrations preserve the old
  base stack and placed-entity IDs without exposing old recipes or creative
  entries.
- The compatibility item always places the canonical entity, and dismissal
  always returns the canonical item.
- `BuddyTier`, `RescueCooldown`, and `TemporaryBlocks` are never written and are
  ignored when present in legacy NBT.
- Legacy Mk II/Mk III items and temporary rescue blocks are intentionally not
  migrated.

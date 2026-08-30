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

## Automated checks

The authoritative checks are:

```sh
./gradlew test
./gradlew runGameTestServer
./gradlew build
```

The repository workflow runs these commands with Java 21 on every push and pull
request. A specific successful head commit and workflow run are recorded here
only after the final naming and behavioral test pass completes.

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

# Baritone Helper verification evidence

## Implementation evidence

The collector-only conversion removes the former tier, rescue, combat,
threat-detection, owner-following, and dimension-transfer classes. The public
surface is one Resource Worker, one Worker Controller, and one Cargo Upgrade
under the `baritonehelper` namespace.

The implementation includes:

- persistent 27/54-slot inventory and cargo state;
- persistent collection job, target, origin, storage, and exclusions;
- bounded target planning and worker navigation;
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

The repository workflow runs these commands with Temurin Java 21 on every push
and pull request. This document must not claim a pass until the corresponding
commit has a successful workflow run.

## Compatibility evidence

- The new public entity ID is `baritonehelper:worker`.
- A hidden `buddybot:buddy_bot` entity registration remains solely so existing
  placed base entities can deserialize into the new worker class.
- `BuddyTier`, `RescueCooldown`, and `TemporaryBlocks` are never written and are
  ignored when present in legacy NBT.
- Legacy Mk II/Mk III item stacks and temporary rescue blocks are intentionally
  not migrated.

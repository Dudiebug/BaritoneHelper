# Contributing

Baritone Helper targets Minecraft 1.21.1, NeoForge 21.1.248, and Java 21. Contributions should preserve the collector-only, server-authoritative product contract and the licensing boundaries around the relocated Baritone-derived runtime.

## Development setup

Use Java 21 and a normal Gradle checkout. The main source areas are:

- `src/main/java/dev/dudie/baritonehelper/` — Baritone Helper-owned application code.
- `src/main/java/dev/dudie/baritonehelper/internal/baritone/` — relocated Baritone-derived code; preserve applicable LGPL headers and attribution.
- `src/main/resources/` and `src/generated/resources/` — runtime and generated assets/data.
- `src/test/` plus in-source NeoForge GameTests — contract and integration coverage.
- `tools/` — release/verification helpers.
- `docs/` — current documentation plus versioned release and archival engineering records.

Do not manually edit generated output when a Gradle data-generation task is the source of truth.

## Product constraints

Do not add or reintroduce:

- combat or hostile targeting;
- owner following, rescue behavior, or idle wandering;
- automatic cross-dimension owner teleportation;
- direct predicted-drop insertion instead of normal world drops and pickup;
- direct world placement that bypasses the worker's real inventory and interaction rules;
- unbounded scans, unbounded chunk tickets, or uncontrolled background work.

Changes to the network payload schema must increment the applicable protocol version and include stale-revision/validation coverage. Changes to worker ownership, inventory, storage, pathing, scanning, or asynchronous publication should include regression tests for cancellation and lifecycle cleanup where relevant.

## Verification

Before opening a pull request, run:

```sh
./gradlew clean test --no-daemon --console=plain
./gradlew runGameTestServer --no-daemon --console=plain
./gradlew build --no-daemon --console=plain
```

On supported development hosts, the repository verification helpers may also be used:

```powershell
.\tools\gauntlet.ps1
```

or:

```sh
./tools/gauntlet.sh
```

See [docs/development/testing.md](docs/development/testing.md) for the current testing contract.

## Branches and commits

Use short-lived branches such as `feature/...`, `fix/...`, `docs/...`, or `chore/...`. Keep commits scoped and avoid mixing unrelated refactors with behavioral changes. Delete development branches after they are merged.

## Pull requests

A pull request should explain:

- what behavior changed and why;
- whether persisted data, networking, pathing, chunk loading, or inventory ownership changed;
- what tests were added or updated;
- which verification commands were run;
- any user-facing documentation or changelog entry required by the change.

Behavior changes should update `CHANGELOG.md` under **Unreleased**. Architecture changes should update `docs/architecture.md` or add a versioned release record when appropriate.

## Licensing

Project-authored code outside the relocated Baritone-derived subset is MIT licensed. Baritone-derived files remain under LGPL-3.0-or-later and must retain required upstream notices. Do not remove attribution or license headers from vendored/derived files. See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) and [`LICENSES/`](LICENSES/).

# Testing and verification

Baritone Helper uses three primary release gates: JUnit contract tests, NeoForge dedicated-server GameTests, and a production Gradle build.

## Required commands

Run under Java 21:

```sh
./gradlew clean test --no-daemon --console=plain
./gradlew runGameTestServer --no-daemon --console=plain
./gradlew build --no-daemon --console=plain
```

All three are required before publishing a release.

## What should be covered

Changes should add or update tests at the lowest useful layer and include integration coverage when behavior crosses the Minecraft server/runtime boundary.

Important regression areas include:

- owner authorization and remote inventory access;
- persistence and migration;
- item conservation, cargo upgrades, pickup, and deposits;
- finite/unlimited job lifecycle and cancellation;
- world scanning and long-range discovery;
- chunk/ticket ownership and cleanup;
- Baritone path calculation, movement, interaction, and replanning;
- generation fencing for asynchronous work;
- no-work boundaries and pathing policy;
- network revision validation and stale requests;
- multi-worker fairness/performance for changes affecting executors or tick work.

## Repository helpers

Windows development environments can run:

```powershell
.\tools\gauntlet.ps1
```

Unix-like environments can run:

```sh
./tools/gauntlet.sh
```

`tools/manual-mutation.ps1` is used for targeted mutation-style verification of release-3.1 generation-fence coverage.

## CI

`.github/workflows/build.yml` runs unit tests, NeoForge GameTests, and the build on pushes and pull requests. A successful CI run is expected before behavior changes are considered complete.

## Release verification records

Versioned verification documents under `docs/releases/` record what was measured for a specific release. They are historical evidence, not a substitute for running the current test suite after the code changes.

# Release process

`gradle.properties` is the source of truth for the mod version. Git tags use the same version prefixed with `v`.

## 1. Prepare the version

Update `mod_version` in `gradle.properties` to the intended release version.

Convert the relevant `CHANGELOG.md` **Unreleased** entries into a version section using:

```text
## X.Y.Z - YYYY-MM-DD
```

The release workflow refuses to publish if the changelog does not contain the version being released.

## 2. Run the release gates

Use Java 21 and run:

```sh
./gradlew clean test --no-daemon --console=plain
./gradlew runGameTestServer --no-daemon --console=plain
./gradlew build --no-daemon --console=plain
```

Resolve failures before tagging. When a release makes substantial architecture/performance changes, update or add the appropriate versioned record under `docs/releases/`.

## 3. Review artifacts

A normal build must produce:

- `build/libs/baritonehelper-X.Y.Z.jar`
- `build/libs/baritonehelper-X.Y.Z-sources.jar`

The release workflow validates artifact presence/cardinality and regenerates them from the tagged source.

## 4. Tag the release

Create/push `vX.Y.Z` only after the version, changelog, tests, and build are ready.

The release workflow verifies that the tag exactly equals `v${mod_version}`.

## 5. Automated release

`.github/workflows/release.yml`:

1. checks out the requested tag;
2. validates tag/version/changelog consistency;
3. reruns JUnit tests;
4. reruns NeoForge GameTests;
5. rebuilds runtime and source JARs;
6. extracts the matching changelog section as release notes;
7. generates SHA-256 checksums;
8. creates the GitHub release and attaches both JARs plus `checksums.txt`.

The workflow has no hardcoded default release version.

## 6. Post-release check

Confirm the GitHub release points at the intended tag and contains the runtime JAR, source JAR, checksums, and expected changelog notes. If a versioned verification document is published, record exact release/tag provenance and distinguish pre-release local measurements from the published binary when they are not identical.

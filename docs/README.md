# Baritone Helper documentation

This directory separates current user/developer documentation from version-specific release records and historical engineering material.

## Current documentation

- [User guide](user-guide.md) — installation, worker deployment, jobs, areas, storage, pathing, inventory, offline operation, and troubleshooting.
- [Architecture](architecture.md) — current high-level worker, Baritone runtime, world scanning, networking, inventory, and lifecycle design.
- [Testing](development/testing.md) — unit, GameTest, build, and verification expectations.
- [Release process](development/release-process.md) — versioning, changelog, release-gate, tag, artifact, and checksum workflow.

## Release records

Release records document a specific published line and should not be treated as automatically current after later releases.

- [3.1.0 implementation record](releases/3.1.0-implementation.md)
- [3.1.0 verification record](releases/3.1.0-verification.md)

## Archive

[`archive/`](archive/) contains superseded specifications, verification records, and design work retained for historical context. Current behavior is defined by the current source, tests, README, and current documentation—not by an archived document.

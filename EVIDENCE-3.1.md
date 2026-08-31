# Baritone Helper 3.1 verification evidence

Status: **GREEN** for the implemented 3.1 server-side Baritone integration.

## Source state

- Repository: `BaritoneHelper`
- Reference revision: `85639f4179d77aa5422545b0ea60149d677bb118`
  (`release/3.0.0-max-search-radius`)
- Verification date: 2026-08-30
- Runtime: Eclipse Temurin Java 21

## Implemented architecture

- Each active worker owns one long-lived relocated Baritone runtime and uses the
  canonical `MineProcess`; the legacy `WorkerPlanner.SearchCursor` is no longer
  on the production collection path.
- Palette-backed immutable world snapshots are captured on the server thread,
  scanned on a bounded two-thread executor, and consumed by the relocated
  Baritone pathfinder on a separate bounded four-thread executor.
- Scan requests are coalesced and generation-fenced. Cancellation, target
  replacement, and worker removal prevent stale async publication.
- Movement uses Baritone's player input math while applying the result to the
  server entity. Vanilla look and move controls cannot overwrite active
  Baritone output.
- Worker storage is the canonical inventory for mining, tool use, placement,
  drops, persistence, and the remote 27/54-slot dashboard.
- Every spawned worker owns a centered Chebyshev radius-12 view window: exactly
  25 by 25, or 625, persistent view tickets plus one simulation anchor.

## Automated results

| Check | Result |
| --- | --- |
| JUnit architecture, concurrency, inventory, scanner, movement, and network contracts | **45/45 passed**, 0 failures, 0 errors |
| Dedicated-server GameTests | **40/40 passed** in 16.71 s in the profiled run |
| Four-worker aggregate orchestration p95 | **1.047599 ms** (budget: <= 2 ms) |
| Four-worker individual p95 values | 0.7208, 0.177399, 0.0802, and 0.0692 ms |
| Generation-fence manual mutation | **Killed** by `Release31ParityGameTests` |
| Java Flight Recorder | Valid 8 s profile, 1,108,359 bytes, JFR 2.1 |
| Runtime artifact | `build/libs/baritonehelper-3.1.0.jar` (590,108 bytes) |
| Sources artifact | `build/libs/baritonehelper-3.1.0-sources.jar` (323,818 bytes) |

The GameTest run covers movement parity fixtures, long-range loaded-world
discovery, no-target rescan behavior, four-worker fairness, inventory ownership
and lifecycle, chunk-window ownership and cleanup, and stale-publication fences.
The local, intentionally untracked profile is stored at
`run/baritonehelper-3.1-gametest-20260830.jfr`; `WorldScanner.scanPalette` was
1.23% of 244 execution samples and no helper pathfinding method appeared above
it in the hot-method view.

## Reproduction

Run from the repository root with Java 21 selected through
`BARITONEHELPER_JAVA` or `JAVA_HOME`:

```powershell
.\tools\gauntlet.ps1
.\tools\manual-mutation.ps1
```

The gauntlet performs a clean unit-test, dedicated-server GameTest, and build
run, checks artifact cardinality and versioning, and rejects removed legacy
packages. The mutation runner copies the project to a temporary directory,
requires a green baseline, removes the async generation guard, and requires the
parity tests to fail.

## Scope of the performance claim

The measured p95 is worker orchestration time in the deterministic four-worker
GameTest workload. The run is accelerated and is not a five-minute real-time
20 TPS soak, so this evidence does not claim a measured 19.5 TPS floor, a
server-wide tick p95, or a production-host loaded-chunk pathfinding p95. Those
remain deployment soak metrics rather than release-blocking claims here.

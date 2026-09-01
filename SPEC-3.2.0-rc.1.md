# Baritone Helper 3.2.0-rc.1 executable specification

## Approval and setup

- Approved by the user in the implementation plan, using the public 3.1.0
  release as the baseline.
- The reproducible baseline is tag `v3.1.0` at
  `caedde01babf2799c52b7b8396c94f02cee80ce6`. No unavailable source changes
  or intermediate version checkpoints may be invented.
- Work happens on `codex/3.2.0-rc.1` with grouped commits and Java 21,
  Minecraft 1.21.1, and NeoForge 21.1.248.
- No runtime or test dependency is added. Existing JUnit, NeoForge GameTest,
  Gradle, PowerShell, shell, JFR, and dedicated-server facilities are reused.
- Product changes follow RED -> GREEN -> REFACTOR. The final entry point is
  `tools/gauntlet.ps1`; every skipped layer is recorded in evidence.

## Failure model

| Failure | Required detector |
|---|---|
| A bounded job stops before its whole eligible area is known | Cold 128/256/512 and empty-area GameTests assert coverage and terminal reason |
| A roam job stops because no target is currently known | Roam-late-target GameTest |
| Target A knowledge contaminates target B | Target-switch and two-worker isolation tests |
| Published scan data is stale after mutation, cancellation, or restart | Revision/generation unit tests and dirty/restart GameTests |
| Shared knowledge leaks per-worker policy or mutable path state | Two-worker policy/isolation GameTests |
| Async work is unbounded or touches live world state | Executor/queue contracts, snapshot tests, four-worker soak |
| A worker enters or modifies protected state | Planning and commit-time policy GameTests, including buckets and placement |
| Pickup duplicates or loses the worker/cargo across races or restart | Transaction/component unit tests and lifecycle GameTests |
| A stale/replayed remote action mutates state twice | Protocol idempotence and sequence tests |
| Ticket, heap, or queue state leaks | Lifecycle tests and five-minute JFR soak |
| The built JAR differs from the tested candidate | Exact-artifact hash, inspection, and clean dedicated-server boot |
| Release or notification is claimed without external success | GitHub release readback and Discord delivery readback |

## Executable scenarios

### Search and knowledge

1. `cached_world_decodes_block_positions_and_512_regions`: packed positions,
   including negative coordinates, are returned using 512-block region distance.
2. `coverage_is_target_aware`: scanning target A does not mark target B scanned.
3. `coverage_transitions_and_persists`: UNKNOWN -> SCANNING -> SCANNED -> DIRTY
   is generation-fenced; restart never restores SCANNING as valid.
4. `shared_knowledge_is_policy_neutral`: same-target workers reuse observations
   while applying independent work areas, zones, blacklists, and progress.
5. `bounded_search_is_exhaustive`: a bounded no-target job reaches terminal
   `NO_MATCHING_BLOCKS` only with every eligible chunk scanned.
6. `bounded_search_reports_unreachable`: inaccessible remaining coverage yields
   `SEARCH_AREA_UNREACHABLE` without marking it scanned.
7. `roam_continues_until_late_target`: explicit ROAM keeps exploring and mines a
   target introduced later; migrated jobs remain WORK_AREA.
8. `cold_discovery_128_256_512`: after a two-boot fixture setup, target chunks
   are unloaded and unknown before start, then discovered through worker movement.
9. `stale_scans_do_not_publish`: job, dimension, chunk, and scan generation
   changes reject stale executor results.
10. `mine_process_matches_pinned_upstream_contract`: configurable candidate and
    Y limits, exposed-ore behavior, avoid-breaking, target breaking, goal equality,
    path-start normalization, and audited modern movement cases behave as specified.

### Safety, tickets, and concurrency

11. `all_interactions_revalidate_policy`: break/place/fluid/storage actions check
    no-enter, no-modify, area/roam, block entities, exclusions, and mobGriefing at
    planning and commit time.
12. `ticket_footprint_tracks_runtime`: active view radius is the smallest of 6
    and 8 that passes all gates, simulation radius is 2, and non-active workers
    retain no forced view/simulation tickets.
13. `tickets_release_on_every_exit`: stop, completion, blocking, removal, pickup,
    level change, and restart cleanup leave no stale worker tickets.
14. `four_workers_remain_isolated_and_fair`: four distant jobs share only bounded
    four-path/two-scan executors and make progress without cross-worker state.
15. `movement_stays_progressive_at_one`: server movement remains 1.0F and block
    breaking uses real tool, hardness, hooks, crack, durability, and drops.

### Packed lifecycle and remote protocol

16. `packed_component_round_trips`: the versioned component preserves UUIDs,
    inventory components, upgrades, configuration, progress, zones, pathing,
    storage, revision, and bounded activity, but no transient runtime state.
17. `pickup_is_exactly_once`: LIVE -> PENDING -> COMMITTED delivers one packed
    item, conditionally clears ownership, never dumps cargo, and rolls back on
    failed delivery.
18. `pickup_reconciles_after_restart`: a pending transaction finds/reuses its
    transaction item or safely retries without duplication.
19. `packed_placement_is_safe`: only the owner may place it; schema, UUID
    collision, and spawn failure leave source and ownership unchanged; success
    consumes exactly one even in creative and restores a stopped worker.
20. `remote_identity_is_uuid_and_dimension`: management works cross-dimension;
    physical selection/container operations reject cross-dimension use clearly.
21. `remote_requests_are_idempotent`: identical request UUID/payload replays its
    acknowledgement; conflicting reuse is rejected; stale revisions/sequences
    cannot overwrite authoritative state.
22. `telemetry_is_real_and_bounded`: search/path counters reflect runtime work,
    snapshots publish at most every 10 ticks, and activity history is bounded.

### UI, performance, artifact, and release

23. `dashboard_has_five_responsive_tabs`: persistent header and Job, Area &
    Safety, Storage, Pathing, and Activity controls remain visible and hittable at
    320x240, 640x360, and 1280x720.
24. `soak_meets_budget`: baseline and one/two/four-worker runs use 60-second
    warmups and at least five measured minutes, bounded queues, fair progress,
    no monotonic ticket/heap growth, and the approved TPS/MSPT rule.
25. `candidate_artifact_boots`: the exact hashed 3.2.0-rc.1 JAR passes tests,
    inspection, clean NeoForge dedicated-server startup, and clean shutdown.
26. `release_is_verified_before_notification`: a merged/tagged CI-green commit
    creates a non-latest GitHub prerelease with binary and sources; only verified
    publication permits the direct release link to be sent to `centalyx`.

## Invariants

- Keep separate bounded executors: at most four path calculations and two scans.
- Never read mutable world/chunk state off the server thread.
- Never silently enable ROAM for existing saves or accept protocol-3 clients.
- Never let shared world knowledge contain per-worker policy, path, or progress.
- Never weaken existing tests to obtain green; baseline failures permit zero new
  failures and are reported verbatim.
- Never report an unrun gate, release, artifact, or notification as successful.

## Acceptance commands

```powershell
.\gradlew.bat clean test --no-daemon --console=plain
.\gradlew.bat runGameTestServer --no-daemon --console=plain
.\gradlew.bat build --no-daemon --console=plain
.\tools\gauntlet.ps1
```

The final evidence maps each scenario to an executable test or marks it
unverified with the exact reason; no indirect source-text check proves runtime
behavior that requires GameTest, restart, soak, or dedicated-server evidence.

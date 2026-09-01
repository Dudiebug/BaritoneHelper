# Baritone Helper 3.2.0-rc.1 verification evidence

Status: **all local candidate gates pass**. GitHub CI, public release verification,
and Discord delivery remain pending and are not claimed complete here.

## Source and host

| Field | Value |
|---|---|
| Baseline | Baritone Helper 3.1.0 at `caedde01babf2799c52b7b8396c94f02cee80ce6` |
| Verified candidate source | `78c5fd0d407b560c4f4f501e58585b208713df85` on `codex/3.2.0-rc.1` |
| `mod_version` | `3.2.0-rc.1` |
| Upstream Baritone audit | official `1.21.1` at `f3a51d47a05fa4fc9cacd6d90091f617a8d685df` |
| OS | Microsoft Windows 11 Pro `10.0.26200` |
| CPU visible to JVM | AMD Ryzen 9 5900XT; 12 cores / 12 logical processors available |
| Memory | 17,179,258,880 bytes |
| Java | Temurin OpenJDK `21.0.12.1+1-LTS` |
| NeoForge | `21.1.248` |

The implementation is organized into the requested five grouped commits: shared
exploration, policy/pathing/tickets, packed lifecycle/protocol/UI,
tests/performance, and version/docs/release automation. This evidence update is
a docs-only descendant, so the exact tested source commit remains reachable.

Cold-discovery and soak evidence directories retain the pre-`main`-rebase name
`b638705`; the production Java tree is unchanged by that rebase. The only test
change makes two source-text contracts normalize CRLF to LF before matching.

Graphify was installed and used to index and query the final codebase. The final
graph contained 3,237 nodes, 10,135 edges, and 177 communities. Its
only parser warning was the expected Groovy `build.gradle` syntax limitation;
Java and project-content indexing completed.

## Local release gates

| Gate | Result | Retained evidence |
|---|---|---|
| Source-state contracts | PASS | clean branch head verified before external push |
| Unit contracts | PASS, 103/103 | `release-evidence/gauntlet-bf39255/unit-test-results/` |
| NeoForge GameTests | PASS, 57/57 in 37.45 s | `release-evidence/gauntlet-bf39255/gametest-latest.log` |
| Real multi-drop loot | PASS | clay source produced and deposited four clay balls |
| Four-worker GameTest | PASS | final aggregate p95 1.9874 ms; individual p95 0.4751/0.4524/0.5551/0.5048 ms |
| Manual mutation control | PASS | generation-fence mutation caused its named contract to fail; clean source passed |
| Two-boot cold discovery | PASS | `release-evidence/cold-b638705/` |
| 0/1/2/4-worker 60/300 s soak with JFR | PASS | `release-evidence/soak-b638705/` |
| Radius 6 versus 8 benchmark | PASS; radius 6 selected | `release-evidence/radius8-b638705/` and soak one-worker evidence |
| Exact artifact inspection | PASS | `release-evidence/artifacts-bf39255/` |
| Clean NeoForge startup/shutdown | PASS | `release-evidence/startup/fab55b04a0e04d26b8745bedb3de736a.stdout.log` |

The clean-server test installed the candidate as the only mod JAR in a fresh
NeoForge 21.1.248 dedicated server. It reached `Done (4.988s)`, accepted a clean
stop, and saved every dimension. The official NeoForge installer used for that
test was 6,972,104 bytes with SHA-256
`68eeab77059ba53df1812f1afa5bf530ab2566a3cdcd5f924aa6e71be42e410c`.

## Two-boot cold-discovery proof

Boot 1 generated and saved a fixture at `(256,126,0)` from a worker start at
`(0,126,0)`, then shut down. On boot 2, immediately before starting the job, the
target chunk `[16,0]` was unloaded and its coverage was `UNKNOWN`. The worker
physically advanced to `(252,126,1)` and completed the target after 4,247 ticks,
with movement distance squared 63,505.

The retained state and both boot logs are in `release-evidence/cold-b638705/`.
They establish discovery from cold world state rather than a fixture ticket or a
pre-populated target index.

## View-radius selection

Both candidates used simulation radius 2 and passed the one-worker soak. Radius
6 was selected because correctness passed with the smaller retained footprint.

| Active view radius | TPS | MSPT p50/p95/p99 | View/simulation tickets | Loaded chunks start/end/max | Peak heap |
|---:|---:|---:|---:|---:|---:|
| 6 | 20.00 | 1.1242 / 11.5510 / 15.9107 | 169 / 25 | 1,225 / 1,225 / 1,260 | 726,466,720 B |
| 8 | 20.00 | 1.2501 / 11.1889 / 18.6596 | 289 / 25 | 1,521 / 1,521 / 1,583 | 819,009,456 B |

Radius 6 retained 120 fewer view tickets and 296 fewer endpoint loaded chunks.
The radius-8 comparison used the exact candidate plus the one-line radius
constant change; it was not included in the release source.

## Final soak measurements

Every scenario used a 60-second warmup followed by a 300-second measurement.
Raw JSON, stdout/stderr, worlds, and JFR recordings are retained under
`release-evidence/soak-b638705/`.

| Workers | TPS | MSPT p50 | MSPT p95 | MSPT p99 | Completed per worker | Path/scanner queue max |
|---:|---:|---:|---:|---:|---|---:|
| 0 | 20.00 | 0.1445 | 0.2348 | 0.4766 | n/a | 0 / 0 |
| 1 | 20.00 | 1.1242 | 11.5510 | 15.9107 | 247 | 0 / 0 |
| 2 | 20.00 | 2.2145 | 13.7352 | 19.9207 | 247 / 249 | 0 / 0 |
| 4 | 20.00 | 5.5455 | 24.0084 | 34.4068 | 245 / 240 / 229 / 236 | 0 / 0 |

All workers made progress and the host sustained 20 TPS. The four-worker p95
MSPT remained below the 50 ms acceptance threshold. Maximum active view,
simulation, and search ticket counts were 676, 100, and 0 respectively in the
four-worker run, exactly matching the bounded per-worker policy.

Path cancellation counts were 64, 110, and 225 for the one-, two-, and
four-worker runs; scanner cancellations were zero. These are expected generation
replacements, not queue growth. Loaded chunks were stable at sample endpoints:
841/841, 1,225/1,225, 2,345/2,348, and 4,585/4,588. No queue, ticket, loaded-chunk,
or sampled-heap series showed monotonic growth.

Heap ranges and GC evidence:

| Workers | Heap start/end | Sampled min/max | GC count/time | JFR size |
|---:|---:|---:|---:|---:|
| 0 | 395,486,024 / 480,381,504 B | 395,486,024 / 480,381,504 B | 0 / 0 ms | 906,091 B |
| 1 | 543,882,544 / 415,593,528 B | 280,343,208 / 726,466,720 B | 60 / 254 ms | 3,713,127 B |
| 2 | 1,115,213,048 / 621,935,080 B | 390,982,760 / 1,193,845,840 B | 43 / 283 ms | 5,119,582 B |
| 4 | 600,834,048 / 701,034,288 B | 509,236,056 / 1,469,416,816 B | 187 / 959 ms | 8,094,520 B |

The four-worker JFR contains 302 heap summaries and 151 garbage collections
(113 young and 38 old), supporting the sampled no-monotonic-leak result.

## Candidate artifacts

| Artifact | Size | SHA-256 |
|---|---:|---|
| `baritonehelper-3.2.0-rc.1.jar` | 738,134 B | `496737afb930542c848bdebb4f6df0689a98e33e528bd7e295f9a874c23ef998` |
| `baritonehelper-3.2.0-rc.1-sources.jar` | 379,180 B | `449bdbb61dec7662a1a92496f4397fe8a4dbf8a94c2380a9ffa3378f8b635b33` |

Copies of both clean-LF artifacts are retained under
`release-evidence/artifacts-bf39255/`. They were built from a detached clean
checkout matching GitHub's Linux checkout semantics, then installed alone in the
clean NeoForge server. Public release assets and their downloaded digests must
still be verified independently after the tag workflow completes.

## Reproduction commands

```powershell
.\tools\source-state.ps1 -RequireClean
.\gradlew.bat test --no-daemon --console=plain
.\gradlew.bat runGameTestServer --no-daemon --console=plain
.\gradlew.bat build --no-daemon --console=plain
.\tools\manual-mutation.ps1
.\tools\cold-discovery-two-boot.ps1 -ArtifactPath build\libs\baritonehelper-3.2.0-rc.1.jar -CommandPath cmd.exe -CommandArgument @('/d','/c','gradlew.bat runServer --no-daemon') -StatePath build\verification\cold-discovery\state.properties
.\tools\soak-suite.ps1 -ArtifactPath build\libs\baritonehelper-3.2.0-rc.1.jar -CommandPath cmd.exe -CommandArgument @('/d','/c','gradlew.bat runServer --no-daemon') -ReadyPattern SOAK_READY -JavaPath $env:BARITONEHELPER_JAVA -WarmupSeconds 60 -MeasureSeconds 300
.\tools\inspect-artifact.ps1 -ArtifactPath build\libs\baritonehelper-3.2.0-rc.1.jar
```

## External release gates still pending

- Push branch, open the pull request, and wait for required pull-request CI.
- Merge only after required checks pass, then verify `main` CI.
- Tag the verified merge commit `v3.2.0-rc.1` and verify tag CI.
- Dispatch the exact-tag release workflow and verify the public prerelease, notes,
  binary JAR, sources JAR, and published digest.
- Only then resolve the authenticated Discord account `centalyx` and deliver the
  direct public release URL. Authentication or identity ambiguity must be
  reported rather than treated as delivery.

## Known limitations

- Protocol 3 clients are intentionally incompatible with protocol 4.
- Cross-dimension container opening and physical world-point selection remain
  unavailable. Authoritative remote configuration, status, start/stop, storage
  assignment, and pickup are supported.
- Dashboard geometry has executable headless contracts at 320x240, 640x360, and
  1280x720, but no GPU screenshot corpus is included in this release evidence.
- Performance values are specific to the host and JVM above; they establish the
  stated same-host acceptance gates, not universal server capacity.
- No GitHub release or Discord notification is complete until independently
  verified in the external services.

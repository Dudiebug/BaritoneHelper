# Baritone Helper 3.2.0-rc.1 verification evidence

Status: **candidate gates in progress**. A pending row is not a pass.

## Source and host

| Field | Value |
|---|---|
| Baseline | Baritone Helper 3.1.0 at `caedde01babf2799c52b7b8396c94f02cee80ce6` |
| Candidate branch | `codex/3.2.0-rc.1` |
| Candidate commit | PENDING final grouped commits |
| `mod_version` | `3.2.0-rc.1` |
| Upstream Baritone audit | official `1.21.1` at `f3a51d47a05fa4fc9cacd6d90091f617a8d685df` |
| OS | Microsoft Windows 11 Pro `10.0.26200` |
| CPU visible to JVM | AMD Ryzen 9 5900XT; 12 cores / 12 logical processors available |
| Memory | 17,179,258,880 bytes |
| Java | Temurin OpenJDK `21.0.12.1+1-LTS` |

## Verified results so far

| Gate | Result | Evidence |
|---|---|---|
| Unit contracts | PASS, 102/102 | `gradlew.bat test --no-daemon` |
| Fresh-world NeoForge GameTests | PASS, 57/57 | `gradlew.bat runGameTestServer --no-daemon`; 40.26 s test time |
| Real multi-drop loot | PASS | `actualMultiDropLootIsCollectedWithoutCollapsingTheStack`: one clay source produced and deposited four clay balls |
| Four-worker GameTest | PASS | aggregate worker p95 1.965 ms; individual p95 0.5126/0.4819/0.4899/0.4806 ms |
| Two-boot cold discovery | PASS | `build/verification/cold-discovery/live-20260831-181759.properties` and matching boot logs |
| Intermediate 0/1/2/4 JFR integration | PASS | `build/verification/soak/integration-suite-20260831-183843/` |

The two-boot proof started boot 2 with the target chunk unloaded and target
coverage `UNKNOWN`. The worker moved from `(48,97,0)` to `(300,97,2)`, mined the
target after 4,230 ticks, and recorded movement distance squared 63,508.

The intermediate soak used a 5-second warmup and 30-second measurement per
scenario. It validates the harness but does not replace the required final
60-second warmup and 300-second measurements.

| Workers | TPS | MSPT p50 | MSPT p95 | MSPT p99 | Path queue max | Scanner queue max |
|---:|---:|---:|---:|---:|---:|---:|
| 0 | 20.00 | 0.23 | 0.88 | 2.18 | 0 | 0 |
| 1 | 20.00 | 1.69 | 13.90 | 36.38 | 0 | 0 |
| 2 | 20.00 | 2.88 | 17.39 | 43.21 | 0 | 0 |
| 4 | 20.00 | 5.16 | 32.01 | 43.65 | 0 | 0 |

## Final release gates

| Gate | State | Evidence or blocker |
|---|---|---|
| Clean exact-source unit and GameTest gauntlet | PENDING | Run after grouped commits |
| Manual mutation controls | PENDING | `tools/manual-mutation.ps1` |
| Final 60 s warmup + 300 s 0/1/2/4 soak and JFR | PENDING | `tools/soak-suite.ps1` |
| Exact RC artifact contents, sizes, SHA-256 | PENDING | `tools/inspect-artifact.ps1` |
| Clean NeoForge 21.1.248 startup/shutdown | PENDING | `tools/startup-check.ps1` |
| PR, required CI, merge, main CI, tag CI | PENDING | external GitHub verification |
| Public prerelease assets, notes, and digest | PENDING | external GitHub verification |
| Discord delivery to authenticated `centalyx` | PENDING | only after public release verification |

## Reproduction commands

```powershell
.\gradlew.bat clean test --no-daemon --console=plain
.\gradlew.bat runGameTestServer --no-daemon --console=plain
.\gradlew.bat build --no-daemon --console=plain
.\tools\cold-discovery-two-boot.ps1 -ArtifactPath build\libs\baritonehelper-3.2.0-rc.1.jar -CommandPath cmd.exe -CommandArgument @('/d','/c','gradlew.bat runServer --no-daemon') -StatePath build\verification\cold-discovery\state.properties
.\tools\soak-suite.ps1 -ArtifactPath build\libs\baritonehelper-3.2.0-rc.1.jar -CommandPath cmd.exe -CommandArgument @('/d','/c','gradlew.bat runServer --no-daemon') -ReadyPattern SOAK_READY -JavaPath $env:BARITONEHELPER_JAVA -WarmupSeconds 60 -MeasureSeconds 300
.\tools\inspect-artifact.ps1 -ArtifactPath build\libs\baritonehelper-3.2.0-rc.1.jar
```

## Known limitations

- Protocol 3 clients are intentionally incompatible with protocol 4.
- Cross-dimension container opening and physical world-point selection remain
  unavailable; authoritative remote configuration, status, start/stop, storage
  assignment, and pickup remain supported.
- Final release URLs, hashes, long-soak values, and notification status are not
  reported until externally verified.

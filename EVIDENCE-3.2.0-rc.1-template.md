# Baritone Helper 3.2.0-rc.1 verification evidence template

This file is a blank evidence record. A gate is not verified until its command has run against the recorded source state and its output/artifact is attached. Missing runtime facilities must remain `UNVERIFIED`.

## Source state

| Field | Value |
|---|---|
| Commit (`tools/source-state`) | `[record exact commit]` |
| Branch | `[record branch or detached]` |
| `mod_version` | `[record exact value]` |
| Java runtime | `[record exact version]` |
| Worktree | `[clean / dirty — release gate requires clean]` |

## Gate results

| Gate | Hook | Result (`PASS` / `FAIL` / `UNVERIFIED`) | Evidence path or blocker |
|---|---|---|---|
| Cold discovery: 128 blocks | `runGameTestServer` / dedicated fixture | `[ ]` | `[ ]` |
| Cold discovery: 256 blocks | `runGameTestServer` / dedicated fixture | `[ ]` | `[ ]` |
| Cold discovery: 512 blocks | `runGameTestServer` / dedicated fixture | `[ ]` | `[ ]` |
| Cold discovery: two boots | `tools/cold-discovery-two-boot.ps1` | `[ ]` | `[ ]` |
| Ledger persistence | `Release32VerificationGameTests` | `[ ]` | `[ ]` |
| Request replay idempotence | `Release32VerificationGameTests` | `[ ]` | `[ ]` |
| Packed component lifecycle | `Release32VerificationGameTests` | `[ ]` | `[ ]` |
| Worker ticket lifecycle | `Release32VerificationGameTests` | `[ ]` | `[ ]` |
| Remote identity/dimension boundary | `Release32VerificationGameTests` | `[ ]` | `[ ]` |
| No-worker and 1/2/4-worker soak and JFR | `tools/soak-suite.ps1` | `[ ]` | `[ ]` |
| Artifact contents and metadata | `tools/inspect-artifact.ps1` | `[ ]` | `[ ]` |
| Dedicated-server startup/shutdown | `tools/startup-check.ps1` | `[ ]` | `[ ]` |
| Manual mutation kill | `tools/manual-mutation.ps1` | `[ ]` | `[ ]` |
| UI layout at 320x240, 640x360, 1280x720 | client runtime fixture | `[ ]` | `[ ]` |

## Commands run

```text
tools/source-state.ps1 -Json -OutputPath build/verification/source-state.json
gradlew.bat clean test --no-daemon --console=plain
gradlew.bat runGameTestServer --no-daemon --console=plain
gradlew.bat build --no-daemon --console=plain
tools/inspect-artifact.ps1 -ArtifactPath build/libs/baritonehelper-[version].jar -ExpectedSha256 [digest]
tools/startup-check.ps1 -ArtifactPath build/libs/baritonehelper-[version].jar -CommandPath [launcher] -CommandArgument [args] -ReadyPattern [marker]
tools/cold-discovery-two-boot.ps1 -ArtifactPath build/libs/baritonehelper-[version].jar -CommandPath [fixture] -CommandArgument [args] -StatePath [new state file]
tools/soak-suite.ps1 -ArtifactPath build/libs/baritonehelper-[version].jar -CommandPath [fixture] -CommandArgument [args] -ReadyPattern [marker] -JavaPath [java] -WarmupSeconds 60 -MeasureSeconds 300
tools/manual-mutation.ps1
```

## Blockers and attachments

- Runtime facilities unavailable: `[describe; leave affected gates UNVERIFIED]`
- Logs: `[paths]`
- JFR recording and summary: `[paths]`
- Artifact SHA-256: `[digest]`
- Reviewer notes: `[ ]`

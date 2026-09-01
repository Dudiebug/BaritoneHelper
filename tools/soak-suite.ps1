param(
    [string]$ArtifactPath,
    [string]$CommandPath = $env:BARITONEHELPER_SOAK_COMMAND,
    [string[]]$CommandArgument,
    [string]$ReadyPattern = 'SOAK_READY',
    [string]$JavaPath = $env:BARITONEHELPER_JAVA,
    [int]$WarmupSeconds = 60,
    [int]$MeasureSeconds = 300,
    [int]$TimeoutSlackSeconds = 90,
    [string]$EvidenceDirectory
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

if ([string]::IsNullOrWhiteSpace($CommandPath)) {
    throw 'UNVERIFIED: pass -CommandPath (or BARITONEHELPER_SOAK_COMMAND).'
}
if ($null -eq $CommandArgument -or $CommandArgument.Count -eq 0) {
    throw 'UNVERIFIED: pass the dedicated-server launcher arguments in -CommandArgument.'
}

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$runner = Join-Path $PSScriptRoot 'soak-jfr.ps1'
$evidenceRoot = if ([string]::IsNullOrWhiteSpace($EvidenceDirectory)) {
    Join-Path $repoRoot ('build\verification\soak\suite-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
} elseif ([IO.Path]::IsPathRooted($EvidenceDirectory)) {
    [IO.Path]::GetFullPath($EvidenceDirectory)
} else {
    [IO.Path]::GetFullPath((Join-Path $repoRoot $EvidenceDirectory))
}
New-Item -ItemType Directory -Path $evidenceRoot -Force | Out-Null
$worldPath = [IO.Path]::GetFullPath((Join-Path $repoRoot 'run\world'))
$worldEvidenceRoot = Join-Path $evidenceRoot 'worlds'
New-Item -ItemType Directory -Path $worldEvidenceRoot -Force | Out-Null

function Move-WorldToEvidence {
    param([Parameter(Mandatory)][string]$Label)
    if (-not (Test-Path -LiteralPath $worldPath -PathType Container)) { return }
    $source = (Resolve-Path -LiteralPath $worldPath).Path
    $destination = [IO.Path]::GetFullPath((Join-Path $worldEvidenceRoot $Label))
    if (-not $source.Equals($worldPath, [StringComparison]::OrdinalIgnoreCase) `
            -or -not $source.StartsWith($repoRoot + [IO.Path]::DirectorySeparatorChar,
                [StringComparison]::OrdinalIgnoreCase) `
            -or -not $destination.StartsWith(
                $worldEvidenceRoot + [IO.Path]::DirectorySeparatorChar,
                [StringComparison]::OrdinalIgnoreCase)) {
        throw 'Refusing to move an unverified soak world path.'
    }
    if (Test-Path -LiteralPath $destination) {
        throw "Refusing to overwrite preserved soak world: $destination"
    }
    Move-Item -LiteralPath $source -Destination $destination
}

$scenarios = @(0, 1, 2, 4)
$results = @()
Move-WorldToEvidence -Label 'pre-suite'
foreach ($workerCount in $scenarios) {
    $metricsPath = Join-Path $evidenceRoot "workers-$workerCount.json"
    $recordingPath = Join-Path $evidenceRoot "workers-$workerCount.jfr"
    & $runner -ArtifactPath $ArtifactPath -CommandPath $CommandPath `
        -CommandArgument $CommandArgument -ReadyPattern $ReadyPattern `
        -MetricsPath $metricsPath -WorkerCount $workerCount -JavaPath $JavaPath `
        -RecordingPath $recordingPath -WarmupSeconds $WarmupSeconds `
        -MeasureSeconds $MeasureSeconds -TimeoutSlackSeconds $TimeoutSlackSeconds -KeepLogs
    $results += [IO.File]::ReadAllText($metricsPath) | ConvertFrom-Json
    Move-WorldToEvidence -Label "workers-$workerCount"
}

$baseline = $results | Where-Object scenarioWorkers -eq 0 | Select-Object -First 1
if ($null -eq $baseline) { throw 'UNVERIFIED: the no-worker baseline is missing.' }
$hostSustainsTwentyTps = [double]$baseline.tps -ge 19.9
foreach ($metrics in $results) {
    $workers = [int]$metrics.scenarioWorkers
    if ([int]$metrics.viewTicketsMax -gt 169 * $workers `
            -or [int]$metrics.simulationTicketsMax -gt 25 * $workers `
            -or [int]$metrics.searchTicketsMax -gt 4 * $workers) {
        throw "UNVERIFIED: ticket bound exceeded in $workers-worker scenario."
    }
    if ($workers -eq 0) { continue }
    if ($hostSustainsTwentyTps) {
        if ([double]$metrics.tps -lt 19.9 -or [double]$metrics.msptP95 -ge 50.0) {
            throw "UNVERIFIED: $workers workers missed the 20 TPS / p95 < 50 ms gate."
        }
    } elseif ([double]$metrics.msptP95 -gt [double]$baseline.msptP95 * 1.20) {
        throw "UNVERIFIED: $workers workers regressed p95 MSPT by more than 20 percent."
    }
}

$summary = [ordered]@{
    verificationStatus = 'verified'
    generatedUtc = [DateTime]::UtcNow.ToString('O')
    warmupSeconds = $WarmupSeconds
    measuredSecondsPerScenario = $MeasureSeconds
    hostSustainsTwentyTps = $hostSustainsTwentyTps
    scenarios = $results
}
$summaryPath = Join-Path $evidenceRoot 'suite-summary.json'
[IO.File]::WriteAllText($summaryPath, ($summary | ConvertTo-Json -Depth 8) + "`n")
Write-Output "Soak suite passed: $summaryPath"

param(
    [string]$ArtifactPath,
    [string]$CommandPath = $env:BARITONEHELPER_SOAK_COMMAND,
    [string[]]$CommandArgument,
    [string]$ReadyPattern = $env:BARITONEHELPER_SOAK_READY_PATTERN,
    [string]$MetricsPath = $env:BARITONEHELPER_SOAK_METRICS,
    [ValidateSet(0, 1, 2, 4)]
    [int]$WorkerCount = 0,
    [string]$JavaPath = $env:BARITONEHELPER_JAVA,
    [string]$RecordingPath,
    [int]$WarmupSeconds = 60,
    [int]$MeasureSeconds = 300,
    [int]$TimeoutSlackSeconds = 60,
    [switch]$KeepLogs
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Resolve-MinecraftJavaProcess {
    param(
        [Parameter(Mandatory)]
        [Diagnostics.Process]$Launcher,
        [int]$TimeoutSeconds = 30
    )

    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    while ([DateTime]::UtcNow -lt $deadline) {
        $processes = @(Get-CimInstance Win32_Process |
                Select-Object ProcessId, ParentProcessId, Name, CommandLine)
        $descendantIds = [Collections.Generic.HashSet[uint32]]::new()
        [void]$descendantIds.Add([uint32]$Launcher.Id)
        do {
            $added = $false
            foreach ($candidate in $processes) {
                if ($descendantIds.Contains([uint32]$candidate.ParentProcessId) `
                        -and $descendantIds.Add([uint32]$candidate.ProcessId)) {
                    $added = $true
                }
            }
        } while ($added)

        $javaProcesses = @($processes | Where-Object {
                $descendantIds.Contains([uint32]$_.ProcessId) `
                    -and $_.Name -match '^javaw?\.exe$' `
                    -and $_.CommandLine -notmatch 'GradleDaemon'
            })
        $server = @($javaProcesses | Where-Object {
                $_.CommandLine -match 'forgeserverdev|devlaunch|BootstrapLauncher|launchTarget[^ ]*server'
            } | Sort-Object ProcessId -Descending | Select-Object -First 1)
        if ($server.Count -eq 1) {
            return Get-Process -Id $server[0].ProcessId -ErrorAction Stop
        }
        if ($javaProcesses.Count -eq 1) {
            return Get-Process -Id $javaProcesses[0].ProcessId -ErrorAction Stop
        }
        Start-Sleep -Milliseconds 250
    }
    throw "UNVERIFIED: could not resolve the Minecraft Java process below launcher PID $($Launcher.Id)."
}

function Read-SharedText {
    param([Parameter(Mandatory)][string]$Path)
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { return '' }
    $stream = [IO.FileStream]::new(
        $Path, [IO.FileMode]::Open, [IO.FileAccess]::Read, [IO.FileShare]::ReadWrite)
    try {
        $reader = [IO.StreamReader]::new($stream)
        try { return $reader.ReadToEnd() } finally { $reader.Dispose() }
    } finally {
        $stream.Dispose()
    }
}

if ([string]::IsNullOrWhiteSpace($CommandPath)) {
    throw 'UNVERIFIED: pass -CommandPath (or BARITONEHELPER_SOAK_COMMAND) for a long-running soak launcher.'
}
if ($null -eq $CommandArgument -or $CommandArgument.Count -eq 0) {
    throw 'UNVERIFIED: pass -CommandArgument values; placeholders include {artifact}, {jfr}, {metrics}, {warmup}, and {duration}.'
}
if ([string]::IsNullOrWhiteSpace($ReadyPattern)) {
    throw 'UNVERIFIED: pass -ReadyPattern (or BARITONEHELPER_SOAK_READY_PATTERN) emitted after the server is ready.'
}
if ([string]::IsNullOrWhiteSpace($MetricsPath)) {
    throw 'UNVERIFIED: pass -MetricsPath (or BARITONEHELPER_SOAK_METRICS) containing machine-readable soak metrics.'
}
if ($WarmupSeconds -lt 1 -or $MeasureSeconds -lt 1 -or $TimeoutSlackSeconds -lt 1) {
    throw 'WarmupSeconds, MeasureSeconds, and TimeoutSlackSeconds must be positive.'
}

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$artifactInspector = Join-Path $PSScriptRoot 'inspect-artifact.ps1'
& $artifactInspector -ArtifactPath $ArtifactPath | Out-Host
if ([string]::IsNullOrWhiteSpace($ArtifactPath)) {
    $properties = [IO.File]::ReadAllText((Join-Path $repoRoot 'gradle.properties'))
    $version = [Text.RegularExpressions.Regex]::Match(
        $properties, '(?m)^mod_version=(?<version>[^\r\n]+)').Groups['version'].Value.Trim()
    $ArtifactPath = Join-Path $repoRoot "build/libs/baritonehelper-$version.jar"
} elseif (-not [IO.Path]::IsPathRooted($ArtifactPath)) {
    $ArtifactPath = Join-Path $repoRoot $ArtifactPath
}
$ArtifactPath = (Resolve-Path -LiteralPath $ArtifactPath -ErrorAction Stop).Path
if (-not [IO.Path]::IsPathRooted($MetricsPath)) {
    $MetricsPath = Join-Path $repoRoot $MetricsPath
}
$MetricsPath = [IO.Path]::GetFullPath($MetricsPath)

$java = if ([string]::IsNullOrWhiteSpace($JavaPath)) {
    Get-Command java -ErrorAction SilentlyContinue
} else {
    Get-Command (Resolve-Path -LiteralPath $JavaPath -ErrorAction Stop).Path -ErrorAction Stop
}
if ($null -eq $java) {
    throw 'UNVERIFIED: Java is required; pass -JavaPath or set BARITONEHELPER_JAVA.'
}
$javaBin = Split-Path -Parent $java.Source
$jcmd = Get-Command (Join-Path $javaBin 'jcmd.exe') -ErrorAction SilentlyContinue
$jfr = Get-Command (Join-Path $javaBin 'jfr.exe') -ErrorAction SilentlyContinue
if ($null -eq $jcmd -or $null -eq $jfr) {
    throw "UNVERIFIED: Java, jcmd, and jfr must come from one JDK: $javaBin"
}
$javaVersion = (& $java.Source -version 2>&1 | Out-String)
if ($javaVersion -notmatch 'version "21(?:[\."-]|$)') {
    throw "Baritone Helper requires Java 21 for soak evidence; found: $($javaVersion.Trim())"
}
$jfrPath = if ([string]::IsNullOrWhiteSpace($RecordingPath)) {
    Join-Path $repoRoot "build/verification/soak/baritonehelper-$([Guid]::NewGuid().ToString('N')).jfr"
} elseif ([IO.Path]::IsPathRooted($RecordingPath)) {
    [IO.Path]::GetFullPath($RecordingPath)
} else {
    [IO.Path]::GetFullPath((Join-Path $repoRoot $RecordingPath))
}
if (Test-Path -LiteralPath $jfrPath) {
    throw "UNVERIFIED: JFR path already exists: $jfrPath"
}
$logRoot = Split-Path -Parent $jfrPath
New-Item -ItemType Directory -Path $logRoot -Force | Out-Null
$stdoutPath = "$jfrPath.stdout.log"
$stderrPath = "$jfrPath.stderr.log"
$expandedArguments = @($CommandArgument | ForEach-Object {
        $argument = [string]$_
        $argument.Replace('{artifact}', $ArtifactPath).Replace('{jfr}', $jfrPath).Replace(
            '{metrics}', $MetricsPath).Replace('{warmup}', [string]$WarmupSeconds).Replace(
            '{duration}', [string]$MeasureSeconds)
    })

$oldArtifact = $env:BARITONEHELPER_CANDIDATE_ARTIFACT
$oldMetrics = $env:BARITONEHELPER_SOAK_METRICS
$oldWorkers = $env:BARITONEHELPER_SOAK_WORKERS
$oldWarmup = $env:BARITONEHELPER_SOAK_WARMUP_SECONDS
$oldDuration = $env:BARITONEHELPER_SOAK_DURATION_SECONDS
$env:BARITONEHELPER_CANDIDATE_ARTIFACT = $ArtifactPath
$env:BARITONEHELPER_SOAK_METRICS = $MetricsPath
$env:BARITONEHELPER_SOAK_WORKERS = [string]$WorkerCount
$env:BARITONEHELPER_SOAK_WARMUP_SECONDS = [string]$WarmupSeconds
$env:BARITONEHELPER_SOAK_DURATION_SECONDS = [string]$MeasureSeconds
$process = $null
$minecraftJava = $null
$failed = $false
try {
    $process = Start-Process -FilePath $CommandPath -ArgumentList $expandedArguments -WorkingDirectory $repoRoot `
        -RedirectStandardOutput $stdoutPath -RedirectStandardError $stderrPath -WindowStyle Hidden -PassThru
    $readyDeadline = [DateTime]::UtcNow.AddSeconds(120)
    while ([DateTime]::UtcNow -lt $readyDeadline) {
        $stdout = Read-SharedText -Path $stdoutPath
        $stderr = Read-SharedText -Path $stderrPath
        $process.Refresh()
        if ($stdout -match $ReadyPattern -or $stderr -match $ReadyPattern) { break }
        if ($process.HasExited) {
            throw "UNVERIFIED: soak launcher exited before ready marker '$ReadyPattern'.`n$stdout`n$stderr"
        }
        Start-Sleep -Milliseconds 250
    }
    $process.Refresh()
    $stdout = Read-SharedText -Path $stdoutPath
    $stderr = Read-SharedText -Path $stderrPath
    if ($stdout -notmatch $ReadyPattern -and $stderr -notmatch $ReadyPattern) {
        throw "UNVERIFIED: soak launcher never emitted ready marker '$ReadyPattern'."
    }

    $minecraftJava = Resolve-MinecraftJavaProcess -Launcher $process
    Start-Sleep -Seconds $WarmupSeconds
    $jcmdOutput = & $jcmd.Source $minecraftJava.Id 'JFR.start' `
        "name=baritonehelper-soak" 'settings=profile' "duration=${MeasureSeconds}s" "filename=$jfrPath" 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "JFR.start failed ($LASTEXITCODE): $($jcmdOutput -join ' ')"
    }

    $deadline = [DateTime]::UtcNow.AddSeconds($MeasureSeconds + $TimeoutSlackSeconds)
    while ([DateTime]::UtcNow -lt $deadline) {
        $process.Refresh()
        if ($process.HasExited) { break }
        Start-Sleep -Seconds 1
    }
    $process.Refresh()
    if (-not $process.HasExited) {
        throw "UNVERIFIED: soak launcher did not perform a clean shutdown within measurement window."
    }
    if ($process.ExitCode -ne 0) {
        throw "Soak launcher exited $($process.ExitCode)."
    }
    $recordingMissing = -not (Test-Path -LiteralPath $jfrPath -PathType Leaf) `
            -or (Get-Item -LiteralPath $jfrPath).Length -le 0
    if ($recordingMissing) {
        throw 'UNVERIFIED: JFR recording is missing or empty.'
    }
    $jfrSummary = & $jfr.Source summary $jfrPath 2>&1
    $jfrSummaryText = $jfrSummary -join "`n"
    if ($LASTEXITCODE -ne 0 -or $jfrSummaryText -notmatch 'Version:' `
            -or $jfrSummaryText -notmatch 'Chunks:') {
        throw "UNVERIFIED: JFR summary failed: $($jfrSummary -join ' ')"
    }
    if (-not (Test-Path -LiteralPath $MetricsPath -PathType Leaf)) {
        throw "UNVERIFIED: soak metrics file is missing: $MetricsPath"
    }
    $metrics = [IO.File]::ReadAllText($MetricsPath) | ConvertFrom-Json
    if ($null -eq $metrics -or $metrics.verificationStatus -ne 'verified') {
        throw 'UNVERIFIED: metrics must set verificationStatus=verified after checking TPS/MSPT, progress, and ticket/heap bounds.'
    }
    if ([int]$metrics.measuredSeconds -lt $MeasureSeconds) {
        throw "UNVERIFIED: metrics measured only $($metrics.measuredSeconds) seconds; required $MeasureSeconds."
    }
    if ([int]$metrics.scenarioWorkers -ne $WorkerCount) {
        throw "UNVERIFIED: metrics describe $($metrics.scenarioWorkers) workers; expected $WorkerCount."
    }
    Write-Output "Soak/JFR harness passed: $jfrPath"
} catch {
    $failed = $true
    throw
} finally {
    if ($null -ne $minecraftJava) {
        $minecraftJava.Refresh()
        if (-not $minecraftJava.HasExited -and $failed) {
            Stop-Process -Id $minecraftJava.Id -Force -ErrorAction SilentlyContinue
        }
    }
    if ($null -ne $process) {
        $process.Refresh()
        if (-not $process.HasExited) {
            Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue
        }
    }
    if ($null -eq $oldArtifact) { Remove-Item Env:BARITONEHELPER_CANDIDATE_ARTIFACT -ErrorAction SilentlyContinue }
    else { $env:BARITONEHELPER_CANDIDATE_ARTIFACT = $oldArtifact }
    if ($null -eq $oldMetrics) { Remove-Item Env:BARITONEHELPER_SOAK_METRICS -ErrorAction SilentlyContinue }
    else { $env:BARITONEHELPER_SOAK_METRICS = $oldMetrics }
    if ($null -eq $oldWorkers) { Remove-Item Env:BARITONEHELPER_SOAK_WORKERS -ErrorAction SilentlyContinue }
    else { $env:BARITONEHELPER_SOAK_WORKERS = $oldWorkers }
    if ($null -eq $oldWarmup) { Remove-Item Env:BARITONEHELPER_SOAK_WARMUP_SECONDS -ErrorAction SilentlyContinue }
    else { $env:BARITONEHELPER_SOAK_WARMUP_SECONDS = $oldWarmup }
    if ($null -eq $oldDuration) { Remove-Item Env:BARITONEHELPER_SOAK_DURATION_SECONDS -ErrorAction SilentlyContinue }
    else { $env:BARITONEHELPER_SOAK_DURATION_SECONDS = $oldDuration }
    if ($failed -or $KeepLogs) {
        Write-Host "Soak logs: $stdoutPath and $stderrPath"
    } else {
        Remove-Item -LiteralPath $stdoutPath, $stderrPath -Force -ErrorAction SilentlyContinue
    }
}

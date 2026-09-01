param(
    [string]$ArtifactPath,
    [string]$CommandPath = $env:BARITONEHELPER_CANDIDATE_STARTUP_COMMAND,
    [string[]]$CommandArgument,
    [string]$ReadyPattern = $env:BARITONEHELPER_CANDIDATE_READY_PATTERN,
    [int]$TimeoutSeconds = 60,
    [switch]$KeepLogs
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

if ($TimeoutSeconds -lt 1 -or $TimeoutSeconds -gt 3600) {
    throw 'TimeoutSeconds must be between 1 and 3600.'
}
if ([string]::IsNullOrWhiteSpace($CommandPath)) {
    throw 'UNVERIFIED: pass -CommandPath (or BARITONEHELPER_CANDIDATE_STARTUP_COMMAND) for an exact-artifact launcher.'
}
if ($null -eq $CommandArgument -or $CommandArgument.Count -eq 0) {
    throw 'UNVERIFIED: pass -CommandArgument values; the launcher must receive the exact artifact path.'
}
if ([string]::IsNullOrWhiteSpace($ReadyPattern)) {
    throw 'UNVERIFIED: pass -ReadyPattern (or BARITONEHELPER_CANDIDATE_READY_PATTERN) emitted only after the candidate is ready.'
}

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$artifactInspector = Join-Path $PSScriptRoot 'inspect-artifact.ps1'
if (-not (Test-Path -LiteralPath $artifactInspector -PathType Leaf)) {
    throw "Artifact inspector is missing: $artifactInspector"
}

& $artifactInspector -ArtifactPath $ArtifactPath | Out-Host

function Read-SharedText {
    param([Parameter(Mandatory)][string]$Path)

    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        return ''
    }
    $stream = [IO.File]::Open(
        $Path,
        [IO.FileMode]::Open,
        [IO.FileAccess]::Read,
        [IO.FileShare]::ReadWrite)
    try {
        $reader = [IO.StreamReader]::new($stream)
        try {
            return $reader.ReadToEnd()
        } finally {
            $reader.Dispose()
        }
    } finally {
        $stream.Dispose()
    }
}

if ([string]::IsNullOrWhiteSpace($ArtifactPath)) {
    $properties = [IO.File]::ReadAllText((Join-Path $repoRoot 'gradle.properties'))
    $version = [Text.RegularExpressions.Regex]::Match(
        $properties, '(?m)^mod_version=(?<version>[^\r\n]+)').Groups['version'].Value.Trim()
    $ArtifactPath = Join-Path $repoRoot "build/libs/baritonehelper-$version.jar"
} elseif (-not [IO.Path]::IsPathRooted($ArtifactPath)) {
    $ArtifactPath = Join-Path $repoRoot $ArtifactPath
}
$ArtifactPath = (Resolve-Path -LiteralPath $ArtifactPath -ErrorAction Stop).Path

$logRoot = Join-Path $repoRoot 'build/verification/startup'
New-Item -ItemType Directory -Path $logRoot -Force | Out-Null
$runId = [Guid]::NewGuid().ToString('N')
$stdoutPath = Join-Path $logRoot "$runId.stdout.log"
$stderrPath = Join-Path $logRoot "$runId.stderr.log"
$expandedArguments = @($CommandArgument | ForEach-Object {
        $_.Replace('{artifact}', $ArtifactPath)
    })

$oldArtifact = $env:BARITONEHELPER_CANDIDATE_ARTIFACT
$oldMode = $env:BARITONEHELPER_STARTUP_CHECK
$env:BARITONEHELPER_CANDIDATE_ARTIFACT = $ArtifactPath
$env:BARITONEHELPER_STARTUP_CHECK = '1'
$process = $null
$ready = $false
$failed = $false
try {
    $process = Start-Process -FilePath $CommandPath -ArgumentList $expandedArguments -WorkingDirectory $repoRoot `
        -RedirectStandardOutput $stdoutPath -RedirectStandardError $stderrPath -WindowStyle Hidden -PassThru
    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    while ([DateTime]::UtcNow -lt $deadline) {
        $stdout = Read-SharedText -Path $stdoutPath
        $stderr = Read-SharedText -Path $stderrPath
        if ($stdout -match $ReadyPattern -or $stderr -match $ReadyPattern) {
            $ready = $true
        }
        $process.Refresh()
        if ($process.HasExited) {
            if (-not $ready) {
                throw "UNVERIFIED: launcher exited before ready marker '$ReadyPattern'.`n$stdout`n$stderr"
            }
            if ($process.ExitCode -ne 0) {
                throw "Candidate launcher exited $($process.ExitCode) after ready marker."
            }
            break
        }
        Start-Sleep -Milliseconds 250
    }

    $process.Refresh()
    if (-not $process.HasExited) {
        throw "UNVERIFIED: candidate launcher did not perform a clean shutdown within $TimeoutSeconds seconds."
    }
    if (-not $ready) {
        throw "UNVERIFIED: candidate launcher never emitted ready marker '$ReadyPattern'."
    }
    if ($process.ExitCode -ne 0) {
        throw "Candidate launcher exited $($process.ExitCode)."
    }
    Write-Output "Candidate artifact startup and clean-shutdown check passed: $ArtifactPath"
} catch {
    $failed = $true
    throw
} finally {
    if ($null -ne $process) {
        $process.Refresh()
        if (-not $process.HasExited) {
            Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue
        }
    }
    if ($null -eq $oldArtifact) { Remove-Item Env:BARITONEHELPER_CANDIDATE_ARTIFACT -ErrorAction SilentlyContinue }
    else { $env:BARITONEHELPER_CANDIDATE_ARTIFACT = $oldArtifact }
    if ($null -eq $oldMode) { Remove-Item Env:BARITONEHELPER_STARTUP_CHECK -ErrorAction SilentlyContinue }
    else { $env:BARITONEHELPER_STARTUP_CHECK = $oldMode }
    if (-not $KeepLogs -and -not $failed) {
        Remove-Item -LiteralPath $stdoutPath, $stderrPath -Force -ErrorAction SilentlyContinue
    } else {
        Write-Host "Startup logs: $stdoutPath and $stderrPath"
    }
}

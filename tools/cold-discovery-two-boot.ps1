param(
    [string]$ArtifactPath,
    [string]$CommandPath = $env:BARITONEHELPER_COLD_BOOT_COMMAND,
    [string[]]$CommandArgument,
    [string]$StatePath = $env:BARITONEHELPER_COLD_BOOT_STATE,
    [string]$Boot1Marker = 'COLD_BOOT_1_OK',
    [string]$Boot2Marker = 'COLD_BOOT_2_OK',
    [int]$TimeoutSeconds = 300,
    [switch]$KeepLogs
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

if ([string]::IsNullOrWhiteSpace($CommandPath)) {
    throw 'UNVERIFIED: pass -CommandPath (or BARITONEHELPER_COLD_BOOT_COMMAND) for a two-boot fixture launcher.'
}
if ($null -eq $CommandArgument -or $CommandArgument.Count -eq 0) {
    throw 'UNVERIFIED: pass -CommandArgument values; use {artifact}, {state}, and {boot} placeholders as needed.'
}
if ([string]::IsNullOrWhiteSpace($StatePath)) {
    throw 'UNVERIFIED: pass -StatePath (or BARITONEHELPER_COLD_BOOT_STATE) for persistent boot state.'
}
if ($TimeoutSeconds -lt 1 -or $TimeoutSeconds -gt 7200) {
    throw 'TimeoutSeconds must be between 1 and 7200.'
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

if (-not [IO.Path]::IsPathRooted($StatePath)) {
    $StatePath = Join-Path $repoRoot $StatePath
}
$StatePath = [IO.Path]::GetFullPath($StatePath)
if (Test-Path -LiteralPath $StatePath) {
    throw "State path already exists; refusing to reuse stale two-boot evidence: $StatePath"
}
$stateDirectory = Split-Path -Parent $StatePath
New-Item -ItemType Directory -Path $stateDirectory -Force | Out-Null

$logRoot = Join-Path $repoRoot 'build/verification/cold-discovery'
New-Item -ItemType Directory -Path $logRoot -Force | Out-Null
$runId = [Guid]::NewGuid().ToString('N')
$logs = @()

function Invoke-Boot {
    param([Parameter(Mandatory)][int]$BootNumber, [Parameter(Mandatory)][string]$Marker)

    $stdoutPath = Join-Path $logRoot "$runId.boot$BootNumber.stdout.log"
    $stderrPath = Join-Path $logRoot "$runId.boot$BootNumber.stderr.log"
    $script:logs += $stdoutPath
    $script:logs += $stderrPath
    $expandedArguments = @($CommandArgument | ForEach-Object {
            $_.Replace('{artifact}', $ArtifactPath).Replace('{state}', $StatePath).Replace('{boot}', [string]$BootNumber)
        })

    $oldArtifact = $env:BARITONEHELPER_CANDIDATE_ARTIFACT
    $oldState = $env:BARITONEHELPER_COLD_BOOT_STATE
    $oldBoot = $env:BARITONEHELPER_COLD_BOOT_NUMBER
    $env:BARITONEHELPER_CANDIDATE_ARTIFACT = $ArtifactPath
    $env:BARITONEHELPER_COLD_BOOT_STATE = $StatePath
    $env:BARITONEHELPER_COLD_BOOT_NUMBER = [string]$BootNumber
    $process = $null
    try {
        $process = Start-Process -FilePath $CommandPath -ArgumentList $expandedArguments -WorkingDirectory $repoRoot `
            -RedirectStandardOutput $stdoutPath -RedirectStandardError $stderrPath -WindowStyle Hidden -PassThru
        $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
        while ([DateTime]::UtcNow -lt $deadline) {
            $process.Refresh()
            if ($process.HasExited) { break }
            Start-Sleep -Milliseconds 250
        }
        $process.Refresh()
        $stdout = if (Test-Path -LiteralPath $stdoutPath) { [IO.File]::ReadAllText($stdoutPath) } else { '' }
        $stderr = if (Test-Path -LiteralPath $stderrPath) { [IO.File]::ReadAllText($stderrPath) } else { '' }
        if (-not $process.HasExited) {
            throw "UNVERIFIED: boot $BootNumber did not exit within $TimeoutSeconds seconds."
        }
        if ($process.ExitCode -ne 0) {
            throw "Cold-discovery boot $BootNumber exited $($process.ExitCode).`n$stdout`n$stderr"
        }
        $markerMissing = $stdout -notmatch [Text.RegularExpressions.Regex]::Escape($Marker) `
                -and $stderr -notmatch [Text.RegularExpressions.Regex]::Escape($Marker)
        if ($markerMissing) {
            throw "UNVERIFIED: boot $BootNumber did not emit marker '$Marker'."
        }
    } finally {
        if ($null -ne $process) {
            $process.Refresh()
            if (-not $process.HasExited) {
                Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue
            }
        }
        if ($null -eq $oldArtifact) { Remove-Item Env:BARITONEHELPER_CANDIDATE_ARTIFACT -ErrorAction SilentlyContinue }
        else { $env:BARITONEHELPER_CANDIDATE_ARTIFACT = $oldArtifact }
        if ($null -eq $oldState) { Remove-Item Env:BARITONEHELPER_COLD_BOOT_STATE -ErrorAction SilentlyContinue }
        else { $env:BARITONEHELPER_COLD_BOOT_STATE = $oldState }
        if ($null -eq $oldBoot) { Remove-Item Env:BARITONEHELPER_COLD_BOOT_NUMBER -ErrorAction SilentlyContinue }
        else { $env:BARITONEHELPER_COLD_BOOT_NUMBER = $oldBoot }
    }
}

try {
    Invoke-Boot -BootNumber 1 -Marker $Boot1Marker
    if (-not (Test-Path -LiteralPath $StatePath -PathType Leaf)) {
        throw "UNVERIFIED: boot 1 did not leave persistent state at $StatePath"
    }
    if ((Get-Item -LiteralPath $StatePath).Length -le 0) {
        throw "UNVERIFIED: boot 1 left an empty state file at $StatePath"
    }

    Invoke-Boot -BootNumber 2 -Marker $Boot2Marker
    $stateMissing = -not (Test-Path -LiteralPath $StatePath -PathType Leaf) `
            -or (Get-Item -LiteralPath $StatePath).Length -le 0
    if ($stateMissing) {
        throw "UNVERIFIED: boot 2 did not preserve non-empty state at $StatePath"
    }
    Write-Output "Cold-discovery two-boot harness passed: $StatePath"
} finally {
    if ($KeepLogs) {
        Write-Host "Cold-discovery logs: $($logs -join ', ')"
    } else {
        Remove-Item -LiteralPath $logs -Force -ErrorAction SilentlyContinue
    }
}

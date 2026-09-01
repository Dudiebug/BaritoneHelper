param(
    [switch]$RequireClean,
    [switch]$Json,
    [string]$OutputPath
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path

function Invoke-GitText {
    param([Parameter(Mandatory)][string[]]$Arguments)

    $gitOutput = & git -C $repoRoot @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "git $($Arguments -join ' ') failed ($LASTEXITCODE): $($gitOutput -join ' ')"
    }
    return ($gitOutput -join "`n").Trim()
}

$head = Invoke-GitText @('rev-parse', 'HEAD')
$branch = Invoke-GitText @('branch', '--show-current')
if ([string]::IsNullOrWhiteSpace($branch)) {
    $branch = '(detached)'
}

$statusText = Invoke-GitText @('status', '--porcelain=v1', '--untracked-files=all')
$status = @($statusText -split "`r?`n" |
    Where-Object { -not [string]::IsNullOrWhiteSpace($_) })

$propertiesPath = Join-Path $repoRoot 'gradle.properties'
if (-not (Test-Path -LiteralPath $propertiesPath -PathType Leaf)) {
    throw "gradle.properties is missing: $propertiesPath"
}
$properties = [IO.File]::ReadAllText($propertiesPath)
$versionMatches = [Text.RegularExpressions.Regex]::Matches(
    $properties, '(?m)^mod_version=(?<version>[^\r\n]+)')
if ($versionMatches.Count -ne 1) {
    throw "Expected exactly one mod_version entry; found $($versionMatches.Count)."
}
$modVersion = $versionMatches[0].Groups['version'].Value.Trim()
if ([string]::IsNullOrWhiteSpace($modVersion)) {
    throw 'mod_version is empty.'
}

if ($RequireClean -and $status.Count -ne 0) {
    throw "Source state is dirty; refusing clean verification.`n$($status -join "`n")"
}

$state = [ordered]@{
    repository = $repoRoot
    branch = $branch
    commit = $head
    modVersion = $modVersion
    clean = ($status.Count -eq 0)
    status = @($status)
}

if ($Json) {
    $rendered = $state | ConvertTo-Json -Depth 4
} else {
    $statusLine = if ($state.clean) { 'true' } else { 'false' }
    $renderedLines = @(
        "repository=$($state.repository)"
        "branch=$($state.branch)"
        "commit=$($state.commit)"
        "mod_version=$($state.modVersion)"
        "clean=$statusLine"
    )
    if (-not $state.clean) {
        $renderedLines += 'status:'
        $renderedLines += $state.status
    }
    $rendered = $renderedLines -join "`n"
}

if (-not [string]::IsNullOrWhiteSpace($OutputPath)) {
    $outputFile = if ([IO.Path]::IsPathRooted($OutputPath)) {
        [IO.Path]::GetFullPath($OutputPath)
    } else {
        [IO.Path]::GetFullPath((Join-Path $repoRoot $OutputPath))
    }
    $outputDirectory = Split-Path -Parent $outputFile
    if (-not (Test-Path -LiteralPath $outputDirectory -PathType Container)) {
        New-Item -ItemType Directory -Path $outputDirectory -Force | Out-Null
    }
    [IO.File]::WriteAllText($outputFile, "$rendered`n", [Text.UTF8Encoding]::new($false))
}

Write-Output $rendered

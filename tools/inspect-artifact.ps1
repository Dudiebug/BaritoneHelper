param(
    [string]$ArtifactPath,
    [string]$ExpectedSha256,
    [switch]$Json
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
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
$expectedName = "baritonehelper-$modVersion.jar"

if ([string]::IsNullOrWhiteSpace($ArtifactPath)) {
    $ArtifactPath = Join-Path $repoRoot "build/libs/$expectedName"
} elseif (-not [IO.Path]::IsPathRooted($ArtifactPath)) {
    $ArtifactPath = Join-Path $repoRoot $ArtifactPath
}
$artifact = (Resolve-Path -LiteralPath $ArtifactPath -ErrorAction Stop).Path
if ([IO.Path]::GetFileName($artifact) -ne $expectedName) {
    throw "Artifact name must be $expectedName; found $([IO.Path]::GetFileName($artifact))."
}
if (-not (Test-Path -LiteralPath $artifact -PathType Leaf)) {
    throw "Artifact is missing: $artifact"
}

Add-Type -AssemblyName System.IO.Compression.FileSystem
$requiredEntries = @(
    'META-INF/neoforge.mods.toml',
    'pack.mcmeta',
    'dev/dudie/baritonehelper/BaritoneHelper.class',
    'assets/baritonehelper/lang/en_us.json'
)
$forbiddenPrefix = 'dev/dudie/buddybot/'
$zip = [IO.Compression.ZipFile]::OpenRead($artifact)
try {
    $entryNames = @($zip.Entries | ForEach-Object { $_.FullName })
    foreach ($entry in $requiredEntries) {
        if ($entryNames -notcontains $entry) {
            throw "Artifact is missing required entry: $entry"
        }
    }
    if (@($entryNames | Where-Object { $_.StartsWith($forbiddenPrefix, [StringComparison]::Ordinal) }).Count -ne 0) {
        throw "Artifact contains removed buddybot production classes."
    }

    $metadataEntry = $zip.GetEntry('META-INF/neoforge.mods.toml')
    $metadataReader = [IO.StreamReader]::new($metadataEntry.Open(), [Text.Encoding]::UTF8)
    try {
        $metadata = $metadataReader.ReadToEnd()
    } finally {
        $metadataReader.Dispose()
    }
    $metadataVersion = [Text.RegularExpressions.Regex]::Match(
        $metadata, '(?m)^\s*version\s*=\s*"(?<version>[^"]+)"').Groups['version'].Value
    if ([string]::IsNullOrWhiteSpace($metadataVersion) -or $metadataVersion -ne $modVersion) {
        throw "Artifact metadata version does not match mod_version ($modVersion): '$metadataVersion'."
    }
    $license = [Text.RegularExpressions.Regex]::Match(
        $metadata, '(?m)^\s*license\s*=\s*"(?<license>[^"]+)"').Groups['license'].Value
    if ([string]::IsNullOrWhiteSpace($license)) {
        throw 'Artifact metadata does not declare a license.'
    }
} finally {
    $zip.Dispose()
}

$sha256 = [Security.Cryptography.SHA256]::Create()
try {
    $hash = ([BitConverter]::ToString($sha256.ComputeHash([IO.File]::ReadAllBytes($artifact))) -replace '-', '').ToLowerInvariant()
} finally {
    $sha256.Dispose()
}
$expectedHashProvided = -not [string]::IsNullOrWhiteSpace($ExpectedSha256)
if ($expectedHashProvided -and $hash -ne $ExpectedSha256.Trim().ToLowerInvariant()) {
    throw "Artifact SHA-256 mismatch: expected $ExpectedSha256, found $hash."
}

$result = [ordered]@{
    artifact = $artifact
    name = [IO.Path]::GetFileName($artifact)
    bytes = (Get-Item -LiteralPath $artifact).Length
    sha256 = $hash
    modVersion = $modVersion
    metadataVersion = $metadataVersion
    license = $license
    requiredEntries = $requiredEntries
}
if ($Json) {
    Write-Output ($result | ConvertTo-Json -Depth 4)
} else {
    Write-Output "artifact=$($result.artifact)"
    Write-Output "bytes=$($result.bytes)"
    Write-Output "sha256=$($result.sha256)"
    Write-Output "mod_version=$($result.modVersion)"
    Write-Output "metadata_version=$($result.metadataVersion)"
    Write-Output "license=$($result.license)"
    Write-Output 'artifact=verified'
}

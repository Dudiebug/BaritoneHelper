param(
    [ValidateSet('generation-fence', 'custom')]
    [string]$Mutation = 'generation-fence',
    [string]$MutationFile,
    [string]$Pattern,
    [string]$Replacement,
    [string[]]$Tests,
    [switch]$KeepTemp
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$javaCommand = $env:BARITONEHELPER_JAVA
if ([string]::IsNullOrWhiteSpace($javaCommand)) {
    $java = Get-Command java -ErrorAction SilentlyContinue
    if ($null -eq $java) {
        throw 'Java 21 was not found. Set BARITONEHELPER_JAVA to the Java 21 executable.'
    }
    $javaCommand = $java.Source
}
$javaVersion = (& $javaCommand -version 2>&1 | Out-String)
if ($javaVersion -notmatch 'version "21(?:[\."-]|$)') {
    throw "Baritone Helper requires Java 21; found: $($javaVersion.Trim())"
}

if ($Mutation -eq 'generation-fence') {
    $MutationFile = 'src/main/java/dev/dudie/baritonehelper/internal/baritone/behavior/PathingBehavior.java'
    $Pattern = 'if \(this\.inProgress != pathfinder\s+\|\| this\.calculationGeneration != generation\s+\|\| !goalsEquivalent\(this\.goal, goal\)\) \{'
    $Replacement = 'if (false) {'
    $Tests = @('dev.dudie.baritonehelper.Release31ParityGameTests')
} else {
    $missingCustomMutation = [string]::IsNullOrWhiteSpace($MutationFile) `
            -or [string]::IsNullOrWhiteSpace($Pattern) `
            -or $null -eq $Replacement `
            -or $null -eq $Tests `
            -or $Tests.Count -eq 0
    if ($missingCustomMutation) {
        throw 'Custom mutation requires -MutationFile, -Pattern, -Replacement, and at least one -Tests value.'
    }
}

$mutationRoot = Join-Path ([IO.Path]::GetTempPath()) (
    'baritonehelper-manual-mutation-' + [Guid]::NewGuid().ToString('N'))
$copyItems = @(
    'src',
    'gradle',
    'LICENSES',
    'THIRD_PARTY_NOTICES.md',
    'build.gradle',
    'gradle.properties',
    'settings.gradle',
    'gradlew',
    'gradlew.bat'
)

function Invoke-GradleTest {
    param([string[]]$Arguments)

    $wrapperJar = Join-Path $mutationRoot 'gradle/wrapper/gradle-wrapper.jar'
    $gradleArguments = @(
        '-classpath',
        $wrapperJar,
        'org.gradle.wrapper.GradleWrapperMain'
    ) + $Arguments + @('--no-daemon', '--console=plain')
    $output = & $javaCommand @gradleArguments 2>&1
    $exitCode = $LASTEXITCODE
    $output | Out-Host
    return $exitCode
}

New-Item -ItemType Directory -Path $mutationRoot -Force | Out-Null
try {
    foreach ($item in $copyItems) {
        $source = Join-Path $repoRoot $item
        if (-not (Test-Path -LiteralPath $source)) {
            throw "Mutation copy input is missing: $source"
        }
        Copy-Item -LiteralPath $source -Destination $mutationRoot -Recurse -Force
    }

    Push-Location $mutationRoot
    try {
        $baselineExit = Invoke-GradleTest @('test')
        if ($baselineExit -ne 0) {
            throw "Baseline tests are RED ($baselineExit); refusing to claim a mutation kill."
        }

        $mutationPath = [IO.Path]::GetFullPath((Join-Path $mutationRoot $MutationFile))
        $mutationRootPrefix = ([IO.Path]::GetFullPath($mutationRoot)).TrimEnd('\') + '\'
        if (-not $mutationPath.StartsWith($mutationRootPrefix, [StringComparison]::OrdinalIgnoreCase)) {
            throw "Mutation file must remain inside the isolated copy: $MutationFile"
        }
        if (-not (Test-Path -LiteralPath $mutationPath -PathType Leaf)) {
            throw "Mutation file is missing: $MutationFile"
        }
        $original = [IO.File]::ReadAllText($mutationPath)
        $matches = [Text.RegularExpressions.Regex]::Matches($original, $Pattern)
        if ($matches.Count -ne 1) {
            throw "Expected exactly one mutation match in $MutationFile; found $($matches.Count)."
        }
        $mutated = [Text.RegularExpressions.Regex]::Replace($original, $Pattern, $Replacement, 1)
        if ($mutated -eq $original) {
            throw 'Mutation replacement made no change.'
        }
        [IO.File]::WriteAllText($mutationPath, $mutated)

        $mutationArguments = @('test')
        foreach ($test in $Tests) {
            if ([string]::IsNullOrWhiteSpace($test)) { continue }
            $mutationArguments += @('--tests', $test)
        }
        $mutationExit = Invoke-GradleTest $mutationArguments
        if ($mutationExit -eq 0) {
            throw "Mutation '$Mutation' survived the selected tests."
        }
        Write-Host "Mutation '$Mutation' was killed by the selected tests."
    } finally {
        Pop-Location
    }
} finally {
    if (-not $KeepTemp -and (Test-Path -LiteralPath $mutationRoot)) {
        Remove-Item -LiteralPath $mutationRoot -Recurse -Force
    } else {
        Write-Host "Mutation copy retained at $mutationRoot"
    }
}

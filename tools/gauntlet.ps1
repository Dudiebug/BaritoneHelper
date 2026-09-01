Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$sourceState = Join-Path $PSScriptRoot 'source-state.ps1'
$artifactInspector = Join-Path $PSScriptRoot 'inspect-artifact.ps1'
$startupCheck = Join-Path $PSScriptRoot 'startup-check.ps1'
foreach ($requiredTool in @($sourceState, $artifactInspector, $startupCheck)) {
    if (-not (Test-Path -LiteralPath $requiredTool -PathType Leaf)) {
        throw "Required verification tool is missing: $requiredTool"
    }
}
& $sourceState -RequireClean | Out-Host

$javaCommand = $env:BARITONEHELPER_JAVA
if ([string]::IsNullOrWhiteSpace($javaCommand)) {
    $java = Get-Command java -ErrorAction SilentlyContinue
    if ($null -eq $java) {
        throw 'Java 21 was not found. Set BARITONEHELPER_JAVA to the Java 21 executable.'
    }
    $javaCommand = $java.Source
}

$javaVersion = (& $javaCommand -version 2>&1 | Out-String)
if ($javaVersion -notmatch 'version "21(?:[.\"-]|$)') {
    throw "Baritone Helper requires Java 21; found: $($javaVersion.Trim())"
}

$gradleWrapperJar = Join-Path $repoRoot 'gradle/wrapper/gradle-wrapper.jar'
if (-not (Test-Path -LiteralPath $gradleWrapperJar -PathType Leaf)) {
    throw "Gradle wrapper not found: $gradleWrapperJar"
}

Push-Location $repoRoot
try {
    $gradleArguments = @(
        '-classpath',
        $gradleWrapperJar,
        'org.gradle.wrapper.GradleWrapperMain',
        'clean',
        'test',
        'runGameTestServer',
        'build',
        '--no-daemon',
        '--console=plain'
    )
    & $javaCommand @gradleArguments
    if ($LASTEXITCODE -ne 0) {
        exit $LASTEXITCODE
    }

    $libs = Join-Path $repoRoot 'build/libs'
    $runtimeJars = @(Get-ChildItem -LiteralPath $libs -File -Filter 'baritonehelper-*.jar' |
        Where-Object { $_.Name -notlike '*-sources.jar' })
    $sourceJars = @(Get-ChildItem -LiteralPath $libs -File -Filter 'baritonehelper-*-sources.jar')
    if ($runtimeJars.Count -ne 1 -or $sourceJars.Count -ne 1) {
        throw 'Expected exactly one runtime JAR and one source JAR.'
    }

    $versionLine = Get-Content -LiteralPath 'gradle.properties' |
        Where-Object { $_ -match '^mod_version=' } |
        Select-Object -First 1
    $modVersion = ($versionLine -replace '^mod_version=', '').Trim()
    if ([string]::IsNullOrWhiteSpace($modVersion)) {
        throw 'mod_version is missing from gradle.properties.'
    }
    if ($runtimeJars[0].Name -ne "baritonehelper-$modVersion.jar" -or
        $sourceJars[0].Name -ne "baritonehelper-$modVersion-sources.jar") {
        throw 'Artifact names do not match mod_version.'
    }
    if ($runtimeJars[0].Length -le 0 -or $sourceJars[0].Length -le 0) {
        throw 'Release artifacts must be non-empty.'
    }

    & $artifactInspector -ArtifactPath $runtimeJars[0].FullName | Out-Host

    & $startupCheck `
        -ArtifactPath $runtimeJars[0].FullName `
        -CommandPath $env:BARITONEHELPER_CANDIDATE_STARTUP_COMMAND `
        -CommandArgument $env:BARITONEHELPER_CANDIDATE_STARTUP_ARGUMENT `
        -ReadyPattern $env:BARITONEHELPER_CANDIDATE_READY_PATTERN | Out-Host

    if (Test-Path -LiteralPath 'src/main/java/dev/dudie/buddybot') {
        throw 'Removed buddybot production package is still present.'
    }
    if (Test-Path -LiteralPath 'src/main/resources/data/buddybot') {
        throw 'Removed buddybot data resources are still present.'
    }
    if (-not (Test-Path -LiteralPath 'src/main/resources/assets/buddybot/models/item/buddy_bot.json' -PathType Leaf)) {
        throw 'The legacy buddybot item model alias is missing.'
    }

    $productionFiles = Get-ChildItem -LiteralPath 'src/main/java/dev/dudie/baritonehelper' -Recurse -File -Filter '*.java' |
        Where-Object { $_.FullName -notmatch '[\\/]gametest[\\/]' }
    $legacyPattern = 'BuddyBotTier|RescueController|FollowOwnerGoal|MeleeAttackGoal|FloatGoal|changeDimension\('
    foreach ($file in $productionFiles) {
        if ((Get-Content -LiteralPath $file.FullName -Raw) -match $legacyPattern) {
            throw "Removed tier, rescue, combat, following, or dimension-transfer code remains: $($file.FullName)"
        }
    }
}
finally {
    Pop-Location
}

Write-Host 'Baritone Helper PowerShell gauntlet passed.'

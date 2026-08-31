param(
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
if ($javaVersion -notmatch 'version "21(?:[.\"-]|$)') {
    throw "Baritone Helper requires Java 21; found: $($javaVersion.Trim())"
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

New-Item -ItemType Directory -Path $mutationRoot -Force | Out-Null
try {
    foreach ($item in $copyItems) {
        Copy-Item -LiteralPath (Join-Path $repoRoot $item) -Destination $mutationRoot -Recurse -Force
    }

    Push-Location $mutationRoot
    try {
        $gradleWrapperJar = Join-Path $mutationRoot 'gradle/wrapper/gradle-wrapper.jar'
        $gradleArguments = @(
            '-classpath',
            $gradleWrapperJar,
            'org.gradle.wrapper.GradleWrapperMain',
            'test',
            '--no-daemon',
            '--console=plain'
        )
        & $javaCommand @gradleArguments
        $baselineExit = $LASTEXITCODE
        if ($baselineExit -ne 0) {
            throw "Baseline tests are RED ($baselineExit); refusing to claim a mutation kill."
        }

        $pathingFile = Join-Path $mutationRoot 'src/main/java/dev/dudie/baritonehelper/internal/baritone/behavior/PathingBehavior.java'
        $original = [IO.File]::ReadAllText($pathingFile)
        $pattern = 'if \(this\.inProgress != pathfinder\s+\|\| this\.calculationGeneration != generation\s+\|\| !Objects\.equals\(this\.goal, goal\)\) \{'
        $matches = [Text.RegularExpressions.Regex]::Matches($original, $pattern)
        if ($matches.Count -ne 1) {
            throw "Expected exactly one generation-fence guard; found $($matches.Count)."
        }
        $mutated = [Text.RegularExpressions.Regex]::Replace($original, $pattern, 'if (false) {', 1)
        [IO.File]::WriteAllText($pathingFile, $mutated)

        $mutationArguments = @(
            '-classpath',
            $gradleWrapperJar,
            'org.gradle.wrapper.GradleWrapperMain',
            'test',
            '--tests',
            'dev.dudie.baritonehelper.Release31ParityGameTests',
            '--no-daemon',
            '--console=plain'
        )
        & $javaCommand @mutationArguments
        $mutationExit = $LASTEXITCODE
        if ($mutationExit -eq 0) {
            throw 'Generation-fence mutation survived the parity tests.'
        }
        Write-Host 'Generation-fence mutation was killed by the parity tests.'
    }
    finally {
        Pop-Location
    }
}
finally {
    if (-not $KeepTemp -and (Test-Path -LiteralPath $mutationRoot)) {
        Remove-Item -LiteralPath $mutationRoot -Recurse -Force
    }
    else {
        Write-Host "Mutation copy retained at $mutationRoot"
    }
}

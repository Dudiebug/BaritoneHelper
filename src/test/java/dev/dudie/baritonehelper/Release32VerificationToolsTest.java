package dev.dudie.baritonehelper;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Executable contract for the fail-closed release verification hooks. */
final class Release32VerificationToolsTest {
    @Test
    void powershellHooksFailClosedAndUseStrictMode() throws IOException {
        for (String name : new String[] {
                "gauntlet.ps1",
                "manual-mutation.ps1",
                "source-state.ps1",
                "inspect-artifact.ps1",
                "startup-check.ps1",
                "cold-discovery-two-boot.ps1",
                "soak-jfr.ps1",
                "soak-suite.ps1"}) {
            String source = read("tools/" + name);
            assertTrue(source.contains("Set-StrictMode -Version Latest"), name);
            assertTrue(source.contains("$ErrorActionPreference = 'Stop'"), name);
        }
    }

    @Test
    void sourceStateAndArtifactHooksCaptureIdentityAndRejectMismatches() throws IOException {
        String sourceState = read("tools/source-state.ps1");
        String artifact = read("tools/inspect-artifact.ps1");
        assertTrue(sourceState.contains("rev-parse"), "source-state must capture HEAD");
        assertTrue(sourceState.contains("status"), "source-state must capture worktree status");
        assertTrue(sourceState.contains("RequireClean"));
        assertTrue(sourceState.contains("mod_version"));
        assertTrue(artifact.contains("ExpectedSha256"));
        assertTrue(artifact.contains("META-INF/neoforge.mods.toml"));
        assertTrue(artifact.contains("pack.mcmeta"));
        assertTrue(artifact.contains("BaritoneHelper.class"));
        assertTrue(artifact.contains("metadataVersion -ne $modVersion"));
        assertTrue(artifact.contains("buddybot production classes"));
    }

    @Test
    void powershellVersionDetectionAcceptsCrLfFiles() throws IOException {
        for (String name : new String[] {
                "source-state.ps1",
                "inspect-artifact.ps1",
                "startup-check.ps1",
                "cold-discovery-two-boot.ps1",
                "soak-jfr.ps1"}) {
            String source = read("tools/" + name);
            assertTrue(source.contains("(?m)^mod_version=(?<version>[^\\r\\n]+)"), name);
            assertFalse(source.contains("(?m)^mod_version=(?<version>[^\\r\\n]+)$"), name);
        }
    }

    @Test
    void lifecycleHarnessesRequireExternalEvidenceBeforeReportingPass() throws IOException {
        String startup = read("tools/startup-check.ps1");
        String cold = read("tools/cold-discovery-two-boot.ps1");
        String soak = read("tools/soak-jfr.ps1");
        String soakSuite = read("tools/soak-suite.ps1");
        String mutation = read("tools/manual-mutation.ps1");
        String gauntlet = read("tools/gauntlet.ps1");

        assertTrue(startup.contains("UNVERIFIED"));
        assertTrue(startup.contains("clean-shutdown"));
        assertTrue(startup.contains("[IO.FileShare]::ReadWrite"));
        assertTrue(cold.contains("Boot1Marker"));
        assertTrue(cold.contains("Boot2Marker"));
        assertTrue(cold.contains("StatePath"));
        assertTrue(cold.contains("Length -le 0"));
        String coldRunner = read(
                "src/main/java/dev/dudie/baritonehelper/verification/ColdDiscoveryHarness.java");
        assertTrue(coldRunner.contains("level.hasChunkAt(target)"));
        assertTrue(coldRunner.indexOf("level.hasChunkAt(target)")
                < coldRunner.indexOf("worker.startJob()"));
        assertTrue(coldRunner.contains("coverage != CoverageState.UNKNOWN"));
        assertTrue(coldRunner.contains("REQUIRED_MOVEMENT_SQ"));
        assertTrue(coldRunner.contains("COLD_BOOT_1_OK"));
        assertTrue(coldRunner.contains("COLD_BOOT_2_OK"));
        assertTrue(soak.contains("JFR.start"));
        assertTrue(soak.contains("jfr.Source summary"));
        assertTrue(soak.contains("verificationStatus -ne 'verified'"));
        assertTrue(soak.contains("BARITONEHELPER_SOAK_METRICS = $MetricsPath"));
        assertTrue(soak.contains("BARITONEHELPER_SOAK_WORKERS = [string]$WorkerCount"));
        assertTrue(soak.contains("Resolve-MinecraftJavaProcess"));
        assertTrue(soak.contains("$minecraftJava.Id 'JFR.start'"));
        String soakRunner = read(
                "src/main/java/dev/dudie/baritonehelper/verification/SoakHarness.java");
        assertTrue(soakRunner.contains("SOAK_READY"));
        assertTrue(soakRunner.contains("msptP95"));
        assertTrue(soakRunner.contains("maxPathQueueDepth"));
        assertTrue(soakRunner.contains("loadedChunksStart"));
        assertTrue(soakRunner.contains("workerCompleted"));
        assertTrue(soakSuite.contains("@(0, 1, 2, 4)"));
        assertTrue(soakSuite.contains("msptP95"));
        assertTrue(soakSuite.contains("1.20"));
        assertTrue(soakSuite.contains("viewTicketsMax"));
        assertTrue(soakSuite.contains("Move-Item -LiteralPath"));
        assertTrue(mutation.contains("Baseline tests are RED"));
        assertTrue(mutation.contains("selected tests"));
        assertTrue(mutation.contains("Expected exactly one mutation match"));
        assertTrue(gauntlet.contains("-RequireClean"));
        assertTrue(gauntlet.contains("startupCheck"));
    }

    @Test
    void shellHooksHaveFailFastAndEquivalentEvidenceMarkers() throws IOException {
        for (String name : new String[] {
                "gauntlet.sh",
                "source-state.sh",
                "inspect-artifact.sh",
                "startup-check.sh"}) {
            assertTrue(read("tools/" + name).contains("set -euo pipefail"), name);
        }
        assertTrue(read("tools/source-state.sh").contains("--require-clean"));
        assertTrue(read("tools/inspect-artifact.sh").contains("sha256"));
        assertTrue(read("tools/startup-check.sh").contains("UNVERIFIED"));
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(Path.of(relativePath));
    }
}

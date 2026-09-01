package dev.dudie.baritonehelper;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ReleaseCandidateWorkflowTest {
    @Test
    void releaseIsManualVerifiedPrereleaseAndNeverLatest() throws IOException {
        String workflow = Files.readString(Path.of(".github/workflows/release.yml"));

        assertFalse(workflow.contains("tags: ['v*.*.*']"),
                "pushing a tag must not publish before tag CI is verified");
        assertTrue(workflow.contains("workflow_dispatch:"));
        assertTrue(workflow.contains("default: v3.2.0-rc.1"));
        assertTrue(workflow.contains("RELEASE_TAG: ${{ inputs.tag }}"));
        assertTrue(workflow.contains("TAG=\"$RELEASE_TAG\""));
        assertFalse(workflow.contains("TAG=\"${{ github.event.inputs.tag }}\""));
        assertTrue(workflow.contains("gh release view \"$TAG\""));
        assertTrue(workflow.contains("--verify-tag"));
        assertTrue(workflow.contains("--prerelease"));
        assertTrue(workflow.contains("--notes-file RELEASE_NOTES-3.2.0-rc.1.md"));
        assertTrue(workflow.contains("build/libs/SHA256SUMS"));
        assertTrue(workflow.contains("baritonehelper-$VERSION-sources.jar"));
        assertFalse(workflow.contains("--latest"));
    }
}

package dev.dudie.baritonehelper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.dudie.baritonehelper.internal.baritone.cache.CoverageState;
import dev.dudie.baritonehelper.internal.baritone.cache.TargetCoverageLedger;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TargetCoverageLedgerTest {
    @Test
    void preciseMutationDirtiesOnlyAffectedTargetAndKeepsOtherKnownLocations() {
        TargetCoverageLedger ledger = new TargetCoverageLedger();
        long chunk = 12L;
        long diamond = 301L;
        long iron = 302L;
        ledger.publish("diamond_ore", chunk, Set.of(diamond));
        ledger.publish("iron_ore", chunk, Set.of(iron));

        ledger.recordBlockChange(chunk, diamond, "diamond_ore", "air");

        assertEquals(CoverageState.DIRTY, ledger.state("diamond_ore", chunk));
        assertTrue(ledger.allLocations("diamond_ore").isEmpty());
        assertEquals(CoverageState.SCANNED, ledger.state("iron_ore", chunk));
        assertEquals(Set.of(iron), ledger.allLocations("iron_ore"));
    }

    @Test
    void coverageIsTargetAwareDeduplicatedReplaceableAndDirtyable() {
        TargetCoverageLedger ledger = new TargetCoverageLedger();
        long chunk = 42L;

        assertEquals(CoverageState.UNKNOWN, ledger.state("minecraft:diamond_ore", chunk));
        assertTrue(ledger.beginScan("minecraft:diamond_ore", chunk));
        assertFalse(ledger.beginScan("minecraft:diamond_ore", chunk));
        assertEquals(CoverageState.SCANNING, ledger.state("minecraft:diamond_ore", chunk));
        assertEquals(CoverageState.UNKNOWN, ledger.state("minecraft:gold_ore", chunk));

        ledger.publish("minecraft:diamond_ore", chunk, List.of(11L, 12L, 12L));
        assertEquals(CoverageState.SCANNED, ledger.state("minecraft:diamond_ore", chunk));
        assertEquals(Set.of(11L, 12L), ledger.locations("minecraft:diamond_ore", chunk));

        ledger.markDirty(chunk);
        assertEquals(CoverageState.DIRTY, ledger.state("minecraft:diamond_ore", chunk));
        assertTrue(ledger.allLocations("minecraft:diamond_ore").isEmpty(),
                "dirty observations must not be trusted as current targets");
        assertTrue(ledger.beginScan("minecraft:diamond_ore", chunk));
        ledger.publish("minecraft:diamond_ore", chunk, List.of(13L));
        assertEquals(Set.of(13L), ledger.locations("minecraft:diamond_ore", chunk));
    }

    @Test
    void snapshotsConvertInterruptedScansToDirty() {
        TargetCoverageLedger ledger = new TargetCoverageLedger();
        ledger.beginScan("minecraft:coal_ore", 7L);

        TargetCoverageLedger restored = new TargetCoverageLedger();
        restored.restore(ledger.snapshot());

        assertEquals(CoverageState.DIRTY, restored.state("minecraft:coal_ore", 7L));
    }

    @Test
    void chunkRevisionRejectsScanCapturedBeforeMutation() {
        TargetCoverageLedger ledger = new TargetCoverageLedger();
        long revision = ledger.beginScanRevision("minecraft:iron_ore", 9L);

        ledger.markDirty(9L);

        assertFalse(ledger.publishIfRevision("minecraft:iron_ore", 9L, revision, List.of(99L)));
        assertEquals(CoverageState.DIRTY, ledger.state("minecraft:iron_ore", 9L));
        assertTrue(ledger.allLocations("minecraft:iron_ore").isEmpty());

        long current = ledger.beginScanRevision("minecraft:iron_ore", 9L);
        assertTrue(ledger.publishIfRevision("minecraft:iron_ore", 9L, current, List.of(100L)));
        assertEquals(Set.of(100L), ledger.allLocations("minecraft:iron_ore"));
    }

    @Test
    void cancelledScanCannotPublishOverReplacementWithSameChunkRevision() {
        TargetCoverageLedger ledger = new TargetCoverageLedger();
        long staleLease = ledger.beginScanRevision("minecraft:emerald_ore", 10L);

        ledger.abortScan("minecraft:emerald_ore", 10L, staleLease);
        long replacementLease = ledger.beginScanRevision("minecraft:emerald_ore", 10L);

        assertFalse(ledger.publishIfRevision(
                "minecraft:emerald_ore", 10L, staleLease, List.of(200L)));
        assertEquals(CoverageState.SCANNING, ledger.state("minecraft:emerald_ore", 10L));
        assertTrue(ledger.publishIfRevision(
                "minecraft:emerald_ore", 10L, replacementLease, List.of(201L)));
        assertEquals(Set.of(201L), ledger.locations("minecraft:emerald_ore", 10L));
    }
}

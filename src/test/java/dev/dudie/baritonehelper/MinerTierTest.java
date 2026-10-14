package dev.dudie.baritonehelper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.dudie.baritonehelper.worker.MinerTier;
import dev.dudie.baritonehelper.worker.WorkerJobConfiguration;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class MinerTierTest {
    @Test
    void tiersAdvanceInOrderAndEndAtPrecision() {
        assertEquals(Optional.of(MinerTier.BRASS), MinerTier.ANDESITE.next());
        assertEquals(Optional.of(MinerTier.PRECISION), MinerTier.BRASS.next());
        assertEquals(Optional.empty(), MinerTier.PRECISION.next());
    }

    @Test
    void capabilitiesStrictlyImproveWithTier() {
        MinerTier[] tiers = MinerTier.values();
        for (int i = 1; i < tiers.length; i++) {
            assertTrue(tiers[i].maxHorizontalRadius() > tiers[i - 1].maxHorizontalRadius());
            assertTrue(tiers[i].maxVerticalRadius() > tiers[i - 1].maxVerticalRadius());
            assertTrue(tiers[i].breakSpeedMultiplier() > tiers[i - 1].breakSpeedMultiplier());
        }
        assertFalse(MinerTier.ANDESITE.allowsRoam());
        assertFalse(MinerTier.ANDESITE.worksWhileOwnerOffline());
        assertFalse(MinerTier.ANDESITE.acceptsCargoUpgrade());
        assertTrue(MinerTier.PRECISION.allowsRoam());
    }

    @Test
    void precisionMatchesTheLegacyUncappedWorker() {
        assertEquals(WorkerJobConfiguration.MAX_HORIZONTAL_RADIUS, MinerTier.PRECISION.maxHorizontalRadius());
        assertEquals(WorkerJobConfiguration.MAX_VERTICAL_RADIUS, MinerTier.PRECISION.maxVerticalRadius());
        assertEquals(1.0F, MinerTier.PRECISION.breakSpeedMultiplier());
    }

    @Test
    void unknownSerializedTierLoadsAsPrecision() {
        assertEquals(MinerTier.PRECISION, MinerTier.fromSerialized(""));
        assertEquals(MinerTier.PRECISION, MinerTier.fromSerialized(null));
        assertEquals(MinerTier.BRASS, MinerTier.fromSerialized("brass"));
    }
}

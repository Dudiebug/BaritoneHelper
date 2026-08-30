package dev.dudie.buddybot.logic;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Random;
import org.junit.jupiter.api.Test;

class RescueMathTest {
    @Test
    void choosesHighestFiniteFootprintLanding() {
        assertEquals(71.5, RescueMath.highestLandingY(64, 71.5, Double.NaN, 68));
        assertTrue(Double.isNaN(RescueMath.highestLandingY(Double.NaN, Double.NEGATIVE_INFINITY)));
    }

    @Test
    void requiresThreeOfEightDropsGreaterThanFour() {
        assertFalse(RescueMath.isDangerousCliff(new double[]{5, 5, 4, 0, 0, 0, 0, 0}));
        assertTrue(RescueMath.isDangerousCliff(new double[]{5, 4.01, 99, 0, 0, 0, 0, 0}));
        assertFalse(RescueMath.isDangerousCliff(new double[]{5, 5, 5}));
    }

    @Test
    void solvesReachableLowArcAndRejectsImpossibleShot() {
        var from = new Vector3d(0, 2, 0);
        var to = new Vector3d(20, 5, 0);
        var velocity = RescueMath.ballisticVelocity(from, to, 1.5, 0.03).orElseThrow();
        assertEquals(1.5, velocity.length(), 1e-9);
        double time = 20 / velocity.x();
        double y = from.y() + velocity.y() * time - 0.5 * 0.03 * time * time;
        assertEquals(to.y(), y, 1e-7);
        assertTrue(RescueMath.ballisticVelocity(from, new Vector3d(500, 100, 0), 1.0, 0.03).isEmpty());
    }

    @Test
    void randomizedHorizontalShotsIntersectTargets() {
        var random = new Random(0xBADD1E);
        for (int i = 0; i < 250; i++) {
            double distance = 2 + random.nextDouble() * 24;
            double height = -3 + random.nextDouble() * 6;
            var velocity = RescueMath.ballisticVelocity(
                    new Vector3d(0, 0, 0), new Vector3d(distance, height, 0), 1.8, 0.03).orElseThrow();
            double time = distance / velocity.x();
            assertEquals(height, velocity.y() * time - 0.015 * time * time, 1e-7);
        }
    }
}

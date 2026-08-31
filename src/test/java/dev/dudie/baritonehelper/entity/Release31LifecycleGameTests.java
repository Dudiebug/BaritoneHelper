package dev.dudie.baritonehelper.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class Release31LifecycleGameTests {
    @Test
    void radiusTwelveWindowIsExactlyTheCenteredTwentyFiveByTwentyFiveSet() {
        int centerX = 37;
        int centerZ = -19;
        Set<Long> expected = new HashSet<>();
        for (int x = centerX - 12; x <= centerX + 12; x++) {
            for (int z = centerZ - 12; z <= centerZ + 12; z++) {
                expected.add(WorkerChunkWindow.pack(x, z));
            }
        }

        Set<Long> actual = WorkerChunkWindow.around(centerX, centerZ, 12);

        assertEquals(625, expected.size(), "a radius-12 Chebyshev window has 625 chunks");
        assertEquals(expected, actual,
                "the worker ticket window must be centered and contain no extra chunks");
    }
}

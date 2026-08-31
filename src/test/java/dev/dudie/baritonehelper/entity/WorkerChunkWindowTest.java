package dev.dudie.baritonehelper.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

final class WorkerChunkWindowTest {
    @Test
    void radiusTwelveIsAPlayerStyleTwentyFiveByTwentyFiveWindow() {
        Set<Long> chunks = WorkerChunkWindow.around(7, -3, 12);

        assertEquals(625, chunks.size());
        assertTrue(chunks.contains(WorkerChunkWindow.pack(7, -3)));
        assertTrue(chunks.contains(WorkerChunkWindow.pack(-5, -15)));
        assertTrue(chunks.contains(WorkerChunkWindow.pack(19, 9)));
    }

    @Test
    void radiusZeroContainsOnlyTheCenter() {
        assertEquals(
                Set.of(WorkerChunkWindow.pack(-4, 11)),
                WorkerChunkWindow.around(-4, 11, 0));
    }
}

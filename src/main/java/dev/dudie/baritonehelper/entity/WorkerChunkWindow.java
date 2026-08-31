package dev.dudie.baritonehelper.entity;

import java.util.LinkedHashSet;
import java.util.Set;

final class WorkerChunkWindow {
    private WorkerChunkWindow() {}

    static Set<Long> around(int centerX, int centerZ, int radius) {
        if (radius < 0) throw new IllegalArgumentException("radius must be non-negative");
        int diameter = radius * 2 + 1;
        Set<Long> chunks = new LinkedHashSet<>(diameter * diameter);
        for (int x = centerX - radius; x <= centerX + radius; x++) {
            for (int z = centerZ - radius; z <= centerZ + radius; z++) {
                chunks.add(pack(x, z));
            }
        }
        return chunks;
    }

    static long pack(int x, int z) {
        return (x & 0xffffffffL) | ((z & 0xffffffffL) << 32);
    }
}

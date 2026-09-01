package dev.dudie.baritonehelper.internal.baritone.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class WorldScannerCaptureBudgetTest {
    @Test
    void periodicWaiterKeepsItsTurnAcrossMineProcessScanCadence() {
        WorldScanner.CaptureBudget budget = new WorldScanner.CaptureBudget();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        assertEquals(1, budget.acquire(first, 0));
        assertEquals(0, budget.acquire(second, 0));

        assertEquals(0, budget.acquire(first, 5),
                "the earlier-ticking worker must not delete the queued turn");
        assertEquals(1, budget.acquire(second, 5));
        assertEquals(1, budget.acquire(first, 10));
        assertEquals(0, budget.acquire(second, 10));
    }

    @Test
    void abandonedTurnExpiresAfterBoundedDelay() {
        WorldScanner.CaptureBudget budget = new WorldScanner.CaptureBudget();
        UUID active = UUID.randomUUID();
        UUID abandoned = UUID.randomUUID();

        assertEquals(1, budget.acquire(active, 0));
        assertEquals(0, budget.acquire(abandoned, 0));
        assertEquals(0, budget.acquire(active, 5));
        assertEquals(1, budget.acquire(active, 25));
    }
}

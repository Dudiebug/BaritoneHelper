package dev.dudie.baritonehelper.internal.baritone;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * Runtime boundary for the relocated LGPL engine.  Keeping the logger and
 * executor here removes the PlayerEngine application dependency while leaving
 * the upstream engine sources usable by the worker.
 */
public final class InternalBaritoneRuntime {
    public static final String MOD_ID = "baritonehelper";
    public static final String MOD_NAME = "Baritone Helper";
    public static final Logger LOGGER = LoggerFactory.getLogger("BaritoneHelper/Engine");
    /** Small bounded pool: path calculations never run on the server tick thread. */
    private static final int PATH_WORKER_COUNT = Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors() - 1));
    /** Leave room for queued calculations without limiting the number of workers. */
    private static final int PATH_QUEUE_CAPACITY = Math.max(PATH_WORKER_COUNT * 2, 32);
    private static final ThreadPoolExecutor PATH_EXECUTOR = new ThreadPoolExecutor(
        PATH_WORKER_COUNT,
        PATH_WORKER_COUNT,
        0L,
        TimeUnit.MILLISECONDS,
        new ArrayBlockingQueue<>(PATH_QUEUE_CAPACITY, true),
        runnable -> {
            Thread thread = new Thread(runnable, "baritone-helper-pathing");
            thread.setDaemon(true);
            return thread;
        },
        new ThreadPoolExecutor.AbortPolicy()
    );
    /** Discovery cannot starve latency-sensitive A* work. */
    private static final int SCAN_WORKER_COUNT = Math.max(1, Math.min(2, Runtime.getRuntime().availableProcessors() - 1));
    private static final int SCAN_QUEUE_CAPACITY = Math.max(SCAN_WORKER_COUNT * 2, 32);
    private static final ThreadPoolExecutor SCAN_EXECUTOR = new ThreadPoolExecutor(
        SCAN_WORKER_COUNT,
        SCAN_WORKER_COUNT,
        0L,
        TimeUnit.MILLISECONDS,
        new ArrayBlockingQueue<>(SCAN_QUEUE_CAPACITY, true),
        runnable -> {
            Thread thread = new Thread(runnable, "baritone-helper-scanner");
            thread.setDaemon(true);
            return thread;
        },
        new ThreadPoolExecutor.AbortPolicy()
    );
    private static final AtomicLong PATH_CANCELLATIONS = new AtomicLong();
    private static final AtomicLong SCAN_CANCELLATIONS = new AtomicLong();
    public static final Set<Item> WATER_BUCKETS = Set.of(Items.WATER_BUCKET);
    public static final Set<Item> EMPTY_BUCKETS = Set.of(Items.BUCKET);

    private InternalBaritoneRuntime() {
    }

    public static ExecutorService getExecutor() {
        return PATH_EXECUTOR;
    }

    public static ThreadPoolExecutor getScannerExecutor() {
        return SCAN_EXECUTOR;
    }

    public static int pathQueueDepth() {
        return PATH_EXECUTOR.getQueue().size();
    }

    public static int scannerQueueDepth() {
        return SCAN_EXECUTOR.getQueue().size();
    }

    public static int pathQueueCapacity() {
        return PATH_QUEUE_CAPACITY;
    }

    public static int scannerQueueCapacity() {
        return SCAN_QUEUE_CAPACITY;
    }

    public static void recordPathCancellation() {
        PATH_CANCELLATIONS.incrementAndGet();
    }

    public static void recordScanCancellation() {
        SCAN_CANCELLATIONS.incrementAndGet();
    }

    public static long pathCancellationCount() {
        return PATH_CANCELLATIONS.get();
    }

    public static long scanCancellationCount() {
        return SCAN_CANCELLATIONS.get();
    }

    public static void shutdown() {
        PATH_EXECUTOR.shutdownNow();
        SCAN_EXECUTOR.shutdownNow();
    }
}

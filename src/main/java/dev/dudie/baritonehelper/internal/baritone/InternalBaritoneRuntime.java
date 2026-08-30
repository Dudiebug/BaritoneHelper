package dev.dudie.baritonehelper.internal.baritone;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
    private static final ExecutorService PATH_EXECUTOR = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "baritone-helper-pathing");
        thread.setDaemon(true);
        return thread;
    });
    public static final Set<Item> WATER_BUCKETS = Set.of(Items.WATER_BUCKET);
    public static final Set<Item> EMPTY_BUCKETS = Set.of(Items.BUCKET);

    private InternalBaritoneRuntime() {
    }

    public static ExecutorService getExecutor() {
        return PATH_EXECUTOR;
    }

    public static void shutdown() {
        PATH_EXECUTOR.shutdownNow();
    }
}

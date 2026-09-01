package dev.dudie.baritonehelper.verification;

import dev.dudie.baritonehelper.BaritoneHelper;
import dev.dudie.baritonehelper.entity.WorkerEntity;
import dev.dudie.baritonehelper.internal.baritone.InternalBaritoneRuntime;
import dev.dudie.baritonehelper.worker.WorkerActionResult;
import java.io.IOException;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Opt-in dedicated-server soak fixture and metrics recorder. */
@EventBusSubscriber(modid = BaritoneHelper.MOD_ID)
public final class SoakHarness {
    private static final String METRICS_ENV = "BARITONEHELPER_SOAK_METRICS";
    private static final String WORKERS_ENV = "BARITONEHELPER_SOAK_WORKERS";
    private static final String WARMUP_ENV = "BARITONEHELPER_SOAK_WARMUP_SECONDS";
    private static final String DURATION_ENV = "BARITONEHELPER_SOAK_DURATION_SECONDS";
    private static final int POST_MEASUREMENT_TICKS = 100;
    private static Run active;
    private static long tickStartedNanos;

    private SoakHarness() {
    }

    @SubscribeEvent
    public static void serverStarted(ServerStartedEvent event) {
        String metricsValue = System.getenv(METRICS_ENV);
        if (metricsValue == null || metricsValue.isBlank()) return;
        MinecraftServer server = event.getServer();
        Path metricsPath = Path.of(metricsValue).toAbsolutePath().normalize();
        try {
            int workers = parseWorkerCount(System.getenv(WORKERS_ENV));
            int warmupSeconds = positive(System.getenv(WARMUP_ENV), 60, "warmup");
            int durationSeconds = positive(System.getenv(DURATION_ENV), 300, "duration");
            ServerLevel level = server.overworld();
            List<WorkerEntity> entities = createWorkers(level, workers);
            long ready = System.nanoTime();
            active = new Run(server, level, metricsPath, workers, warmupSeconds,
                    durationSeconds, ready, entities);
            InternalBaritoneRuntime.LOGGER.info(
                    "SOAK_READY workers={} warmupSeconds={} durationSeconds={} metrics={}",
                    workers, warmupSeconds, durationSeconds, metricsPath);
        } catch (RuntimeException | IOException exception) {
            fail(server, metricsPath, exception.toString());
        }
    }

    @SubscribeEvent
    public static void serverTickPre(ServerTickEvent.Pre event) {
        Run run = active;
        if (run == null || run.server != event.getServer()) return;
        tickStartedNanos = System.nanoTime();
    }

    @SubscribeEvent
    public static void serverTickPost(ServerTickEvent.Post event) {
        Run run = active;
        if (run == null || run.server != event.getServer()) return;
        long now = System.nanoTime();
        if (now >= run.measurementStartNanos && now < run.measurementEndNanos) {
            if (!run.measurementStarted) run.beginMeasurement();
            run.sample(Math.max(0L, now - tickStartedNanos));
            return;
        }
        if (now >= run.measurementEndNanos && !run.metricsWritten) {
            run.finishMeasurement(now);
            return;
        }
        if (run.metricsWritten && ++run.postMeasurementTicks >= POST_MEASUREMENT_TICKS) {
            active = null;
            run.server.halt(false);
        }
    }

    private static List<WorkerEntity> createWorkers(ServerLevel level, int count) throws IOException {
        List<WorkerEntity> workers = new ArrayList<>(count);
        if (count == 0) return workers;
        int[][] offsets = {{0, 0}, {512, 0}, {-512, 0}, {0, 512}};
        BlockPos spawn = level.getSharedSpawnPos();
        int floorY = Math.max(96, Math.min(240, spawn.getY() + 16));
        ResourceLocation target = BuiltInRegistries.BLOCK.getKey(Blocks.BLUE_WOOL);
        for (int index = 0; index < count; index++) {
            BlockPos center = new BlockPos(
                    spawn.getX() + offsets[index][0], floorY + 1,
                    spawn.getZ() + offsets[index][1]);
            buildFixture(level, center);
            WorkerEntity worker = BaritoneHelper.BARITONE_HELPER_ENTITY.get().create(level);
            if (worker == null) throw new IOException("worker entity factory returned null");
            worker.setUUID(UUID.randomUUID());
            worker.setOwnerUUID(UUID.randomUUID());
            worker.setPersistenceRequired();
            worker.moveTo(center.getX() + 0.5D, center.getY(), center.getZ() + 0.5D,
                    0.0F, 0.0F);
            worker.configureTarget(target, center);
            worker.setRequestedAmount(1_000_000, true);
            worker.setWorkArea(center, 32, 8);
            if (!level.addFreshEntity(worker)) throw new IOException("worker spawn rejected");
            WorkerActionResult result = worker.startJob();
            if (result != WorkerActionResult.STARTED) {
                throw new IOException("worker " + index + " failed to start: " + result);
            }
            workers.add(worker);
        }
        return workers;
    }

    private static void buildFixture(ServerLevel level, BlockPos center) {
        for (int xOffset = -20; xOffset <= 20; xOffset++) {
            for (int zOffset = -20; zOffset <= 20; zOffset++) {
                BlockPos feet = center.offset(xOffset, 0, zOffset);
                level.setBlockAndUpdate(feet.below(), Blocks.STONE.defaultBlockState());
                level.setBlockAndUpdate(feet, Blocks.AIR.defaultBlockState());
                level.setBlockAndUpdate(feet.above(), Blocks.AIR.defaultBlockState());
                if ((xOffset & 1) == 0 && (zOffset & 1) == 0
                        && (xOffset != 0 || zOffset != 0)) {
                    level.setBlockAndUpdate(feet, Blocks.BLUE_WOOL.defaultBlockState());
                }
            }
        }
    }

    private static int parseWorkerCount(String value) {
        int count = value == null || value.isBlank() ? 0 : Integer.parseInt(value);
        if (count != 0 && count != 1 && count != 2 && count != 4) {
            throw new IllegalArgumentException("workers must be 0, 1, 2, or 4");
        }
        return count;
    }

    private static int positive(String value, int fallback, String label) {
        int parsed = value == null || value.isBlank() ? fallback : Integer.parseInt(value);
        if (parsed < 1 || parsed > 7_200) {
            throw new IllegalArgumentException(label + " seconds must be between 1 and 7200");
        }
        return parsed;
    }

    private static void fail(MinecraftServer server, Path metricsPath, String reason) {
        active = null;
        try {
            writeAtomically(metricsPath, "{\n  \"verificationStatus\": \"failed\",\n"
                    + "  \"failure\": \"" + json(reason) + "\"\n}\n");
        } catch (IOException ignored) {
            // The log remains authoritative if the metrics path also fails.
        }
        InternalBaritoneRuntime.LOGGER.error("SOAK_FAILED {}", reason);
        server.halt(false);
    }

    private static void writeAtomically(Path path, String content) throws IOException {
        Path parent = path.getParent();
        if (parent != null) Files.createDirectories(parent);
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        Files.writeString(temporary, content, StandardCharsets.UTF_8);
        try {
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ignored) {
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String json(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\r", "\\r").replace("\n", "\\n");
    }

    private static long garbageCollections() {
        return ManagementFactory.getGarbageCollectorMXBeans().stream()
                .mapToLong(GarbageCollectorMXBean::getCollectionCount)
                .filter(value -> value >= 0L).sum();
    }

    private static long garbageCollectionMillis() {
        return ManagementFactory.getGarbageCollectorMXBeans().stream()
                .mapToLong(GarbageCollectorMXBean::getCollectionTime)
                .filter(value -> value >= 0L).sum();
    }

    private static long usedHeap() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    private static double percentile(long[] sorted, double percentile) {
        if (sorted.length == 0) return 0.0D;
        int index = Math.max(0, Math.min(sorted.length - 1,
                (int) Math.ceil(sorted.length * percentile) - 1));
        return sorted[index] / 1_000_000.0D;
    }

    private static String decimal(double value) {
        return String.format(Locale.ROOT, "%.4f", value);
    }

    private static final class Run {
        private final MinecraftServer server;
        private final ServerLevel level;
        private final Path metricsPath;
        private final int workerCount;
        private final int warmupSeconds;
        private final int durationSeconds;
        private final long measurementStartNanos;
        private final long measurementEndNanos;
        private final List<WorkerEntity> workers;
        private final List<BlockPos> workerStarts;
        private final List<Long> tickNanos = new ArrayList<>();
        private boolean measurementStarted;
        private boolean metricsWritten;
        private int postMeasurementTicks;
        private int maxPathQueue;
        private int maxScannerQueue;
        private int startLoadedChunks;
        private int endLoadedChunks;
        private int maxLoadedChunks;
        private int startViewTickets;
        private int endViewTickets;
        private int maxViewTickets;
        private int startSimulationTickets;
        private int endSimulationTickets;
        private int maxSimulationTickets;
        private int startSearchTickets;
        private int endSearchTickets;
        private int maxSearchTickets;
        private long heapStart;
        private long heapEnd;
        private long heapMinimum = Long.MAX_VALUE;
        private long heapMaximum;
        private long gcCountStart;
        private long gcMillisStart;
        private long pathCancellationsStart;
        private long scanCancellationsStart;

        private Run(
                MinecraftServer server,
                ServerLevel level,
                Path metricsPath,
                int workerCount,
                int warmupSeconds,
                int durationSeconds,
                long readyNanos,
                List<WorkerEntity> workers) throws IOException {
            if (Files.exists(metricsPath)) {
                throw new IOException("metrics path already exists: " + metricsPath);
            }
            this.server = server;
            this.level = level;
            this.metricsPath = metricsPath;
            this.workerCount = workerCount;
            this.warmupSeconds = warmupSeconds;
            this.durationSeconds = durationSeconds;
            this.measurementStartNanos = readyNanos + warmupSeconds * 1_000_000_000L;
            this.measurementEndNanos = measurementStartNanos + durationSeconds * 1_000_000_000L;
            this.workers = List.copyOf(workers);
            this.workerStarts = workers.stream().map(WorkerEntity::blockPosition).toList();
        }

        private void beginMeasurement() {
            measurementStarted = true;
            startLoadedChunks = level.getChunkSource().getLoadedChunksCount();
            startViewTickets = sumViewTickets();
            startSimulationTickets = sumSimulationTickets();
            startSearchTickets = sumSearchTickets();
            heapStart = usedHeap();
            gcCountStart = garbageCollections();
            gcMillisStart = garbageCollectionMillis();
            pathCancellationsStart = InternalBaritoneRuntime.pathCancellationCount();
            scanCancellationsStart = InternalBaritoneRuntime.scanCancellationCount();
        }

        private void sample(long tickDurationNanos) {
            tickNanos.add(tickDurationNanos);
            maxPathQueue = Math.max(maxPathQueue, InternalBaritoneRuntime.pathQueueDepth());
            maxScannerQueue = Math.max(maxScannerQueue, InternalBaritoneRuntime.scannerQueueDepth());
            maxLoadedChunks = Math.max(maxLoadedChunks,
                    level.getChunkSource().getLoadedChunksCount());
            maxViewTickets = Math.max(maxViewTickets, sumViewTickets());
            maxSimulationTickets = Math.max(maxSimulationTickets, sumSimulationTickets());
            maxSearchTickets = Math.max(maxSearchTickets, sumSearchTickets());
            long heap = usedHeap();
            heapMinimum = Math.min(heapMinimum, heap);
            heapMaximum = Math.max(heapMaximum, heap);
        }

        private void finishMeasurement(long now) {
            if (!measurementStarted) beginMeasurement();
            metricsWritten = true;
            endLoadedChunks = level.getChunkSource().getLoadedChunksCount();
            endViewTickets = sumViewTickets();
            endSimulationTickets = sumSimulationTickets();
            endSearchTickets = sumSearchTickets();
            heapEnd = usedHeap();
            long[] sorted = tickNanos.stream().mapToLong(Long::longValue).sorted().toArray();
            double elapsedSeconds = Math.max(0.001D,
                    (Math.min(now, measurementEndNanos) - measurementStartNanos) / 1_000_000_000.0D);
            double tps = sorted.length / elapsedSeconds;
            double p50 = percentile(sorted, 0.50D);
            double p95 = percentile(sorted, 0.95D);
            double p99 = percentile(sorted, 0.99D);
            boolean fairProgress = true;
            List<Integer> completed = new ArrayList<>(workerCount);
            List<Double> movementSq = new ArrayList<>(workerCount);
            List<Double> workerP95 = new ArrayList<>(workerCount);
            for (int index = 0; index < workers.size(); index++) {
                WorkerEntity worker = workers.get(index);
                int progress = worker.completedBlockCount();
                double movement = workerStarts.get(index).distSqr(worker.blockPosition());
                completed.add(progress);
                movementSq.add(movement);
                workerP95.add(worker.workerTickP95Nanos() / 1_000_000.0D);
                fairProgress &= progress > 0 || movement >= 64.0D;
            }
            boolean queuesBounded = maxPathQueue <= InternalBaritoneRuntime.pathQueueCapacity()
                    && maxScannerQueue <= InternalBaritoneRuntime.scannerQueueCapacity();
            boolean verified = p95 < 50.0D && queuesBounded && fairProgress;
            String output = jsonMetrics(tps, p50, p95, p99, fairProgress,
                    queuesBounded, completed, movementSq, workerP95);
            try {
                writeAtomically(metricsPath, output);
                InternalBaritoneRuntime.LOGGER.info(
                        "SOAK_COMPLETE workers={} status={} ticks={} tps={} p95Ms={} "
                                + "pathQueue={} scannerQueue={} completed={}",
                        workerCount, verified ? "verified" : "failed", sorted.length,
                        decimal(tps), decimal(p95), maxPathQueue, maxScannerQueue, completed);
            } catch (IOException exception) {
                fail(server, metricsPath, exception.toString());
            }
        }

        private String jsonMetrics(
                double tps,
                double p50,
                double p95,
                double p99,
                boolean fairProgress,
                boolean queuesBounded,
                List<Integer> completed,
                List<Double> movementSq,
                List<Double> workerP95) {
            long gcCount = garbageCollections() - gcCountStart;
            long gcMillis = garbageCollectionMillis() - gcMillisStart;
            long pathCancellations = InternalBaritoneRuntime.pathCancellationCount()
                    - pathCancellationsStart;
            long scanCancellations = InternalBaritoneRuntime.scanCancellationCount()
                    - scanCancellationsStart;
            boolean verified = p95 < 50.0D && queuesBounded && fairProgress;
            return "{\n"
                    + "  \"verificationStatus\": \"" + (verified ? "verified" : "failed") + "\",\n"
                    + "  \"scenarioWorkers\": " + workerCount + ",\n"
                    + "  \"warmupSeconds\": " + warmupSeconds + ",\n"
                    + "  \"measuredSeconds\": " + durationSeconds + ",\n"
                    + "  \"tickSamples\": " + tickNanos.size() + ",\n"
                    + "  \"tps\": " + decimal(tps) + ",\n"
                    + "  \"msptP50\": " + decimal(p50) + ",\n"
                    + "  \"msptP95\": " + decimal(p95) + ",\n"
                    + "  \"msptP99\": " + decimal(p99) + ",\n"
                    + "  \"maxPathQueueDepth\": " + maxPathQueue + ",\n"
                    + "  \"pathQueueCapacity\": " + InternalBaritoneRuntime.pathQueueCapacity() + ",\n"
                    + "  \"maxScannerQueueDepth\": " + maxScannerQueue + ",\n"
                    + "  \"scannerQueueCapacity\": " + InternalBaritoneRuntime.scannerQueueCapacity() + ",\n"
                    + "  \"pathCancellations\": " + pathCancellations + ",\n"
                    + "  \"scanCancellations\": " + scanCancellations + ",\n"
                    + "  \"loadedChunksStart\": " + startLoadedChunks + ",\n"
                    + "  \"loadedChunksEnd\": " + endLoadedChunks + ",\n"
                    + "  \"loadedChunksMax\": " + maxLoadedChunks + ",\n"
                    + "  \"viewTicketsStart\": " + startViewTickets + ",\n"
                    + "  \"viewTicketsEnd\": " + endViewTickets + ",\n"
                    + "  \"viewTicketsMax\": " + maxViewTickets + ",\n"
                    + "  \"simulationTicketsStart\": " + startSimulationTickets + ",\n"
                    + "  \"simulationTicketsEnd\": " + endSimulationTickets + ",\n"
                    + "  \"simulationTicketsMax\": " + maxSimulationTickets + ",\n"
                    + "  \"searchTicketsStart\": " + startSearchTickets + ",\n"
                    + "  \"searchTicketsEnd\": " + endSearchTickets + ",\n"
                    + "  \"searchTicketsMax\": " + maxSearchTickets + ",\n"
                    + "  \"heapUsedStartBytes\": " + heapStart + ",\n"
                    + "  \"heapUsedEndBytes\": " + heapEnd + ",\n"
                    + "  \"heapUsedMinBytes\": " + (heapMinimum == Long.MAX_VALUE ? heapEnd : heapMinimum) + ",\n"
                    + "  \"heapUsedMaxBytes\": " + heapMaximum + ",\n"
                    + "  \"gcCollections\": " + gcCount + ",\n"
                    + "  \"gcTimeMillis\": " + gcMillis + ",\n"
                    + "  \"fairProgress\": " + fairProgress + ",\n"
                    + "  \"queuesBounded\": " + queuesBounded + ",\n"
                    + "  \"workerCompleted\": " + completed + ",\n"
                    + "  \"workerMovementDistanceSq\": " + movementSq + ",\n"
                    + "  \"workerTickP95Ms\": " + workerP95 + ",\n"
                    + "  \"availableProcessors\": " + Runtime.getRuntime().availableProcessors() + ",\n"
                    + "  \"maxHeapBytes\": " + Runtime.getRuntime().maxMemory() + "\n"
                    + "}\n";
        }

        private int sumViewTickets() {
            return workers.stream().mapToInt(WorkerEntity::workerTicketCount).sum();
        }

        private int sumSimulationTickets() {
            return workers.stream().mapToInt(WorkerEntity::simulationTicketCount).sum();
        }

        private int sumSearchTickets() {
            return workers.stream().mapToInt(WorkerEntity::searchTicketCount).sum();
        }
    }
}

package dev.dudie.baritonehelper.verification;

import dev.dudie.baritonehelper.BaritoneHelper;
import dev.dudie.baritonehelper.entity.WorkerEntity;
import dev.dudie.baritonehelper.internal.baritone.InternalBaritoneRuntime;
import dev.dudie.baritonehelper.internal.baritone.cache.CoverageState;
import dev.dudie.baritonehelper.internal.baritone.cache.SharedWorldKnowledge;
import dev.dudie.baritonehelper.worker.WorkerActionResult;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Opt-in two-process dedicated-server acceptance harness. */
@EventBusSubscriber(modid = BaritoneHelper.MOD_ID)
public final class ColdDiscoveryHarness {
    private static final String STATE_ENV = "BARITONEHELPER_COLD_BOOT_STATE";
    private static final String BOOT_ENV = "BARITONEHELPER_COLD_BOOT_NUMBER";
    private static final int TARGET_DISTANCE = 256;
    private static final int TIMEOUT_TICKS = 4_800;
    private static final double REQUIRED_MOVEMENT_SQ = 128.0D * 128.0D;
    private static Preparation preparation;
    private static VerificationStart verificationStart;
    private static Verification verification;

    private ColdDiscoveryHarness() {
    }

    @SubscribeEvent
    public static void serverStarted(ServerStartedEvent event) {
        String stateValue = System.getenv(STATE_ENV);
        String bootValue = System.getenv(BOOT_ENV);
        if (stateValue == null || stateValue.isBlank() || bootValue == null || bootValue.isBlank()) return;
        Path statePath = Path.of(stateValue).toAbsolutePath().normalize();
        try {
            switch (Integer.parseInt(bootValue)) {
                case 1 -> prepare(event.getServer(), statePath);
                case 2 -> verify(event.getServer(), statePath);
                default -> fail(event.getServer(), statePath, "unsupported boot number " + bootValue);
            }
        } catch (RuntimeException | IOException exception) {
            fail(event.getServer(), statePath, exception.toString());
        }
    }

    @SubscribeEvent
    public static void serverTick(ServerTickEvent.Post event) {
        Preparation pending = preparation;
        if (pending != null && pending.server() == event.getServer()) {
            finishPreparation(pending);
            return;
        }
        VerificationStart pendingStart = verificationStart;
        if (pendingStart != null && pendingStart.server() == event.getServer()) {
            finishVerificationStart(pendingStart);
            return;
        }
        Verification current = verification;
        if (current == null || current.server() != event.getServer()) return;
        WorkerEntity worker = current.worker();
        int elapsed = worker.tickAge() - current.startTick();
        BlockPos end = worker.blockPosition();
        boolean targetRemoved = !BuiltInRegistries.BLOCK.getKey(
                current.level().getBlockState(current.target()).getBlock()).equals(current.targetId());
        if (worker.completedBlockCount() >= 1 && targetRemoved) {
            double movementSq = current.start().distSqr(end);
            if (movementSq < REQUIRED_MOVEMENT_SQ) {
                fail(current.server(), current.statePath(),
                        "target changed without required physical movement: distanceSq=" + movementSq);
                return;
            }
            Properties state = current.state();
            state.setProperty("boot2Verified", "true");
            state.setProperty("targetChunkUnloadedBeforeStart", "true");
            state.setProperty("coverageUnknownBeforeStart", "true");
            state.setProperty("completed", Integer.toString(worker.completedBlockCount()));
            state.setProperty("elapsedTicks", Integer.toString(elapsed));
            state.setProperty("end", packed(end));
            state.setProperty("movementDistanceSq", Double.toString(movementSq));
            try {
                writeState(current.statePath(), state);
                current.server().saveEverything(true, true, true);
                InternalBaritoneRuntime.LOGGER.info(
                        "COLD_BOOT_2_OK unloadedBeforeStart=true coverageBeforeStart=UNKNOWN "
                                + "completed={} elapsedTicks={} start={} end={} movementDistanceSq={}",
                        worker.completedBlockCount(), elapsed, current.start(), end, movementSq);
                verification = null;
                current.server().halt(false);
            } catch (IOException exception) {
                fail(current.server(), current.statePath(), exception.toString());
            }
            return;
        }
        if (!worker.isAlive()) {
            fail(current.server(), current.statePath(), "worker disappeared during verification");
        } else if (elapsed > TIMEOUT_TICKS) {
            fail(current.server(), current.statePath(),
                    "verification timed out: completed=" + worker.completedBlockCount()
                            + ", start=" + current.start() + ", current=" + end
                            + ", telemetry=" + worker.searchTelemetry());
        }
    }

    private static void prepare(MinecraftServer server, Path statePath) throws IOException {
        if (Files.exists(statePath)) throw new IOException("boot state already exists: " + statePath);
        ServerLevel level = server.overworld();
        BlockPos spawn = level.getSharedSpawnPos();
        int floorY = Math.max(96, Math.min(240, spawn.getY() + 16));
        BlockPos start = new BlockPos(spawn.getX(), floorY + 1, spawn.getZ());
        BlockPos target = start.offset(TARGET_DISTANCE, 0, 0);
        buildCorridor(level, start, target);

        UUID workerId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        WorkerEntity worker = BaritoneHelper.BARITONE_HELPER_ENTITY.get().create(level);
        if (worker == null) throw new IllegalStateException("worker entity factory returned null");
        worker.setUUID(workerId);
        worker.setOwnerUUID(ownerId);
        worker.setPersistenceRequired();
        worker.moveTo(start.getX() + 0.5D, start.getY(), start.getZ() + 0.5D, 0.0F, 0.0F);
        BlockPos center = start.offset(TARGET_DISTANCE / 2, 0, 0);
        ResourceLocation targetId = BuiltInRegistries.BLOCK.getKey(Blocks.BLUE_WOOL);
        worker.configureTarget(targetId, center);
        worker.setRequestedAmount(1, false);
        worker.setWorkArea(center, 128, 8);
        if (!level.addFreshEntity(worker)) throw new IllegalStateException("worker spawn rejected");
        worker.releaseWorkerTickets();

        long targetChunk = new ChunkPos(target).toLong();
        CoverageState coverage = SharedWorldKnowledge.get(level).ledger().state(
                targetId.toString(), targetChunk);
        if (coverage != CoverageState.UNKNOWN) {
            throw new IllegalStateException("boot 1 target coverage was " + coverage);
        }

        Properties state = new Properties();
        state.setProperty("schema", "1");
        state.setProperty("workerUuid", workerId.toString());
        state.setProperty("ownerUuid", ownerId.toString());
        state.setProperty("dimension", level.dimension().location().toString());
        state.setProperty("start", packed(start));
        state.setProperty("target", packed(target));
        state.setProperty("targetBlock", targetId.toString());
        state.setProperty("targetDistance", Integer.toString(TARGET_DISTANCE));
        state.setProperty("boot1Coverage", coverage.name());
        preparation = new Preparation(server, level, worker, target, targetId,
                statePath, state, server.getTickCount());
        InternalBaritoneRuntime.LOGGER.info(
                "Cold-discovery boot 1 fixture staged; waiting for entity persistence worker={} target={}",
                workerId, target);
    }

    private static void finishPreparation(Preparation pending) {
        if (pending.server().getTickCount() - pending.startTick() < 5) return;
        if (pending.level().getEntity(pending.worker().getUUID()) != pending.worker()
                || !pending.worker().isAlive()) {
            fail(pending.server(), pending.statePath(),
                    "worker was not integrated before boot 1 save");
            return;
        }
        CoverageState coverage = SharedWorldKnowledge.get(pending.level()).ledger().state(
                pending.targetId().toString(), new ChunkPos(pending.target()).toLong());
        if (coverage != CoverageState.UNKNOWN) {
            fail(pending.server(), pending.statePath(),
                    "boot 1 coverage changed before save: " + coverage);
            return;
        }
        try {
            pending.server().saveEverything(true, true, true);
            pending.state().setProperty("boot1Prepared", "true");
            writeState(pending.statePath(), pending.state());
            InternalBaritoneRuntime.LOGGER.info(
                    "COLD_BOOT_1_OK worker={} start={} target={} targetChunk={} coverage={}",
                    pending.worker().getUUID(), pending.worker().blockPosition(), pending.target(),
                    new ChunkPos(pending.target()), coverage);
            preparation = null;
            pending.server().halt(false);
        } catch (IOException exception) {
            fail(pending.server(), pending.statePath(), exception.toString());
        }
    }

    private static void verify(MinecraftServer server, Path statePath) throws IOException {
        Properties state = readState(statePath);
        if (!"1".equals(state.getProperty("schema"))) {
            throw new IllegalStateException("unsupported harness state schema");
        }
        ServerLevel level = server.overworld();
        UUID workerId = UUID.fromString(required(state, "workerUuid"));
        BlockPos start = unpack(required(state, "start"));
        BlockPos target = unpack(required(state, "target"));
        ResourceLocation targetId = ResourceLocation.parse(required(state, "targetBlock"));
        verificationStart = new VerificationStart(server, level, workerId, start, target,
                targetId, statePath, state, server.getTickCount());
        InternalBaritoneRuntime.LOGGER.info(
                "Cold-discovery boot 2 loaded; waiting for saved entity integration worker={}", workerId);
    }

    private static void finishVerificationStart(VerificationStart pending) {
        if (pending.server().getTickCount() - pending.startTick() < 5) return;
        ServerLevel level = pending.level();
        BlockPos target = pending.target();
        if (level.hasChunkAt(target)) {
            fail(pending.server(), pending.statePath(),
                    "target chunk was loaded before job start: " + new ChunkPos(target));
            return;
        }
        CoverageState coverage = SharedWorldKnowledge.get(level).ledger().state(
                pending.targetId().toString(), new ChunkPos(target).toLong());
        if (coverage != CoverageState.UNKNOWN) {
            fail(pending.server(), pending.statePath(),
                    "coverage before job start was " + coverage);
            return;
        }
        if (!(level.getEntity(pending.workerId()) instanceof WorkerEntity worker)) {
            fail(pending.server(), pending.statePath(),
                    "saved worker was not loaded at spawn: " + pending.workerId());
            return;
        }
        WorkerActionResult result = worker.startJob();
        if (result != WorkerActionResult.STARTED) {
            fail(pending.server(), pending.statePath(), "job did not start: " + result);
            return;
        }
        Properties state = pending.state();
        state.setProperty("boot2StartResult", result.name());
        state.setProperty("targetChunkUnloadedBeforeStart", "true");
        state.setProperty("coverageUnknownBeforeStart", "true");
        try {
            writeState(pending.statePath(), state);
            verification = new Verification(pending.server(), level, worker, pending.start(), target,
                    pending.targetId(), worker.tickAge(), pending.statePath(), state);
            verificationStart = null;
            InternalBaritoneRuntime.LOGGER.info(
                    "COLD_BOOT_2_STARTED worker={} targetChunkUnloaded=true coverage=UNKNOWN start={} target={}",
                    pending.workerId(), pending.start(), target);
        } catch (IOException exception) {
            fail(pending.server(), pending.statePath(), exception.toString());
        }
    }

    private static void buildCorridor(ServerLevel level, BlockPos start, BlockPos target) {
        for (int x = start.getX(); x <= target.getX(); x++) {
            for (int zOffset = -2; zOffset <= 2; zOffset++) {
                BlockPos floor = new BlockPos(x, start.getY() - 1, start.getZ() + zOffset);
                level.setBlockAndUpdate(floor, Blocks.STONE.defaultBlockState());
                for (int yOffset = 0; yOffset <= 2; yOffset++) {
                    level.setBlockAndUpdate(floor.above(1 + yOffset), Blocks.AIR.defaultBlockState());
                }
            }
            for (int side : new int[] {-3, 3}) {
                BlockPos wall = new BlockPos(x, start.getY(), start.getZ() + side);
                for (int yOffset = 0; yOffset <= 3; yOffset++) {
                    level.setBlockAndUpdate(wall.above(yOffset), Blocks.BEDROCK.defaultBlockState());
                }
            }
        }
        for (int zOffset = -3; zOffset <= 3; zOffset++) {
            BlockPos back = new BlockPos(start.getX() - 1, start.getY(), start.getZ() + zOffset);
            for (int yOffset = 0; yOffset <= 3; yOffset++) {
                level.setBlockAndUpdate(back.above(yOffset), Blocks.BEDROCK.defaultBlockState());
            }
        }
        level.setBlockAndUpdate(target, Blocks.BLUE_WOOL.defaultBlockState());
    }

    private static void fail(MinecraftServer server, Path statePath, String reason) {
        preparation = null;
        verificationStart = null;
        verification = null;
        try {
            Properties state = Files.exists(statePath) ? readState(statePath) : new Properties();
            state.setProperty("failure", reason);
            writeState(statePath, state);
        } catch (IOException ignored) {
            // The log remains the authoritative failure signal if state persistence also fails.
        }
        InternalBaritoneRuntime.LOGGER.error("COLD_DISCOVERY_FAILED {}", reason);
        server.halt(false);
    }

    private static Properties readState(Path path) throws IOException {
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(path)) {
            properties.load(input);
        }
        return properties;
    }

    private static void writeState(Path path, Properties properties) throws IOException {
        Path parent = path.getParent();
        if (parent != null) Files.createDirectories(parent);
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        try (OutputStream output = Files.newOutputStream(temporary)) {
            properties.store(output, "Baritone Helper cold-discovery evidence");
        }
        try {
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ignored) {
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String required(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) throw new IllegalStateException("missing state field " + key);
        return value;
    }

    private static String packed(BlockPos position) {
        return Long.toString(position.asLong());
    }

    private static BlockPos unpack(String value) {
        return BlockPos.of(Long.parseLong(value));
    }

    private record Verification(
            MinecraftServer server,
            ServerLevel level,
            WorkerEntity worker,
            BlockPos start,
            BlockPos target,
            ResourceLocation targetId,
            int startTick,
            Path statePath,
            Properties state) {
    }

    private record Preparation(
            MinecraftServer server,
            ServerLevel level,
            WorkerEntity worker,
            BlockPos target,
            ResourceLocation targetId,
            Path statePath,
            Properties state,
            int startTick) {
    }

    private record VerificationStart(
            MinecraftServer server,
            ServerLevel level,
            UUID workerId,
            BlockPos start,
            BlockPos target,
            ResourceLocation targetId,
            Path statePath,
            Properties state,
            int startTick) {
    }
}

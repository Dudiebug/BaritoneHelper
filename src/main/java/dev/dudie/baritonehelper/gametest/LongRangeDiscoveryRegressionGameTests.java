package dev.dudie.baritonehelper.gametest;

import com.mojang.logging.LogUtils;
import dev.dudie.baritonehelper.BaritoneHelper;
import dev.dudie.baritonehelper.entity.WorkerEntity;
import dev.dudie.baritonehelper.internal.baritone.api.utils.BetterBlockPos;
import dev.dudie.baritonehelper.internal.baritone.api.utils.RotationUtils;
import dev.dudie.baritonehelper.internal.baritone.api.utils.input.Input;
import dev.dudie.baritonehelper.internal.baritone.pathing.movement.MovementHelper;
import dev.dudie.baritonehelper.worker.NoWorkZone;
import dev.dudie.baritonehelper.worker.NoWorkZoneMode;
import dev.dudie.baritonehelper.worker.WorkerActionResult;
import dev.dudie.baritonehelper.worker.WorkerPlanner;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(BaritoneHelper.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LongRangeDiscoveryRegressionGameTests {
    private static final BlockPos WORKER = new BlockPos(1, 2, 1);
    // Relative to this GameTest position, the acceptance worker represents
    // the requested world position (0, 64, 0).
    private static final BlockPos ACCEPTANCE_WORKER = new BlockPos(1, 40, 1);

    private LongRangeDiscoveryRegressionGameTests() {
    }

    @GameTest(
            templateNamespace = "minecraft",
            template = "empty",
            batch = "zzLongRangeDiscoveryUnloaded",
            timeoutTicks = 12000)
    public static void unloadedFrontierChunkIsRetriedAndCollected(GameTestHelper helper) {
        clearExistingWorkers(helper);
        clearPath(helper, 1, 96, -64, 64);
        buildFloor(helper, 1, 96, -64, 64);
        BlockPos target = new BlockPos(96, 2, 1);
        helper.setBlock(target, Blocks.REDSTONE_BLOCK);
        WorkerEntity worker = spawnWorker(helper);
        BlockPos center = helper.absolutePos(WORKER);
        worker.configureTarget(blockId(Blocks.REDSTONE_BLOCK), center);
        worker.setWorkArea(center, 128, 64);

        boolean[] started = {false};
        helper.succeedWhen(() -> {
            ChunkPos targetChunk = new ChunkPos(helper.absolutePos(target));
            helper.assertTrue(
                    helper.getLevel().getChunkSource().getChunkNow(targetChunk.x, targetChunk.z) != null,
                    "target chunk inside the 12-chunk worker view must load without a player");
            if (!started[0]) {
                helper.assertValueEqual(worker.startJob().name(), "STARTED", "job starts");
                started[0] = true;
            }
            assertCollected(helper, worker, target, blockId(Blocks.REDSTONE_BLOCK));
        });
    }

    @GameTest(
            templateNamespace = "minecraft",
            template = "empty",
            batch = "zzLongRangeDiscoveryOccluded",
            timeoutTicks = 12000)
    public static void occludedTargetUsesReachableFutureInteractionPosition(GameTestHelper helper) {
        clearExistingWorkers(helper);
        clearPath(helper, 1, 12, -4, 5);
        buildFloor(helper, 1, 12, -4, 5);
        for (int z = 0; z <= 2; z++) {
            helper.setBlock(4, 2, z, Blocks.BEDROCK);
            helper.setBlock(4, 3, z, Blocks.BEDROCK);
        }
        BlockPos target = new BlockPos(10, 2, 1);
        helper.setBlock(target, Blocks.REDSTONE_BLOCK);
        WorkerEntity worker = spawnWorker(helper);
        BlockPos absoluteTarget = helper.absolutePos(target);
        helper.assertFalse(
                WorkerPlanner.hasLineOfSight(helper.getLevel(), worker, absoluteTarget),
                "the target must not be visible from the worker's current position");
        helper.assertTrue(
                WorkerPlanner.nearestWorkPosition(helper.getLevel(), worker, absoluteTarget).isPresent(),
                "the target must have a future interaction position");

        worker.beginCollection(blockId(Blocks.REDSTONE_BLOCK), helper.absolutePos(WORKER));

        helper.succeedWhen(() -> assertCollected(helper, worker, target,
                blockId(Blocks.REDSTONE_BLOCK)));
    }

    @GameTest(templateNamespace = "minecraft", template = "empty",
            batch = "zzLongRangeDiscoveryRange04", timeoutTicks = 12000)
    public static void targetAtFourBlocksIsCollected(GameTestHelper helper) {
        runDistanceCase(helper, 4, 8, Blocks.REDSTONE_BLOCK);
    }

    @GameTest(templateNamespace = "minecraft", template = "empty",
            batch = "zzLongRangeDiscoveryRange16", timeoutTicks = 12000)
    public static void targetAtSixteenBlocksIsCollected(GameTestHelper helper) {
        runDistanceCase(helper, 16, 16, Blocks.EMERALD_BLOCK);
    }

    @GameTest(templateNamespace = "minecraft", template = "empty",
            batch = "zzLongRangeDiscoveryRange32", timeoutTicks = 12000)
    public static void targetAtThirtyTwoBlocksIsCollected(GameTestHelper helper) {
        runDistanceCase(helper, 32, 32, Blocks.DIAMOND_BLOCK);
    }

    @GameTest(templateNamespace = "minecraft", template = "empty",
            batch = "zzLongRangeDiscoveryRange64", timeoutTicks = 12000)
    public static void targetAtSixtyFourBlocksIsCollected(GameTestHelper helper) {
        runDistanceCase(helper, 64, 64, Blocks.LAPIS_BLOCK);
    }

    @GameTest(templateNamespace = "minecraft", template = "empty",
            batch = "zzLongRangeDiscoveryRange128", timeoutTicks = 24000)
    public static void targetAtOneHundredTwentyEightBlocksIsCollected(GameTestHelper helper) {
        runDistanceCase(helper, 128, 128, Blocks.GOLD_BLOCK);
    }

    @GameTest(templateNamespace = "minecraft", template = "empty",
            batch = "zzLongRangeDiscoveryCorner", timeoutTicks = 12000)
    public static void targetAroundCornerIsCollected(GameTestHelper helper) {
        clearExistingWorkers(helper);
        clearVolume(helper, 1, 12, 2, 4, -3, 4);
        buildFloor(helper, 1, 12, -3, 4);
        for (int y = 2; y <= 3; y++) {
            for (int z = -1; z <= 1; z++) helper.setBlock(4, y, z, Blocks.BEDROCK);
            for (int x = 4; x <= 7; x++) helper.setBlock(x, y, 0, Blocks.BEDROCK);
        }
        BlockPos target = new BlockPos(9, 2, 1);
        helper.setBlock(target, Blocks.REDSTONE_BLOCK);
        WorkerEntity worker = spawnWorker(helper);
        BlockPos absoluteTarget = helper.absolutePos(target);
        helper.assertFalse(WorkerPlanner.hasLineOfSight(helper.getLevel(), worker, absoluteTarget),
                "corner target must start outside direct line of sight");
        worker.beginCollection(blockId(Blocks.REDSTONE_BLOCK), helper.absolutePos(WORKER));

        helper.succeedWhen(() -> assertCollected(helper, worker, target,
                blockId(Blocks.REDSTONE_BLOCK)));
    }

    @GameTest(templateNamespace = "minecraft", template = "empty",
            batch = "zzLongRangeDiscoveryUnderground", timeoutTicks = 12000)
    public static void undergroundTunnelTargetIsCollected(GameTestHelper helper) {
        clearExistingWorkers(helper);
        clearVolume(helper, 1, 12, 2, 5, -3, 4);
        buildFloor(helper, 1, 12, -3, 4);
        for (int z = 0; z <= 2; z++) {
            helper.setBlock(5, 2, z, Blocks.BEDROCK);
            helper.setBlock(5, 3, z, Blocks.BEDROCK);
        }
        for (int x = 6; x <= 11; x++) {
            for (int z = 0; z <= 2; z++) helper.setBlock(x, 4, z, Blocks.STONE);
        }
        BlockPos target = new BlockPos(10, 2, 1);
        helper.setBlock(target, Blocks.REDSTONE_BLOCK);
        WorkerEntity worker = spawnWorker(helper);
        helper.assertFalse(WorkerPlanner.hasLineOfSight(
                helper.getLevel(), worker, helper.absolutePos(target)),
                "underground target must start occluded by the tunnel entrance");
        worker.beginCollection(blockId(Blocks.REDSTONE_BLOCK), helper.absolutePos(WORKER));

        helper.succeedWhen(() -> assertCollected(helper, worker, target,
                blockId(Blocks.REDSTONE_BLOCK)));
    }

    @GameTest(templateNamespace = "minecraft", template = "empty",
            batch = "zzLongRangeDiscoveryVertical", timeoutTicks = 12000)
    public static void verticalTargetInsideRadiusIsCollected(GameTestHelper helper) {
        clearExistingWorkers(helper);
        clearVolume(helper, 1, 8, 2, 7, 0, 2);
        buildFloor(helper, 1, 8, 0, 2);
        BlockPos target = new BlockPos(7, 5, 1);
        helper.setBlock(target, Blocks.REDSTONE_BLOCK);
        WorkerEntity worker = spawnWorker(helper);
        worker.configureTarget(blockId(Blocks.REDSTONE_BLOCK), helper.absolutePos(WORKER));
        worker.setWorkArea(helper.absolutePos(WORKER), 16, 8);
        helper.assertValueEqual(worker.startJob(), WorkerActionResult.STARTED, "vertical job starts");

        helper.succeedWhen(() -> assertCollected(helper, worker, target,
                blockId(Blocks.REDSTONE_BLOCK)));
    }

    @GameTest(templateNamespace = "minecraft", template = "empty",
            batch = "zzLongRangeDiscoveryBoundary", timeoutTicks = 450)
    public static void targetOutsideHorizontalRadiusRemainsUntouched(GameTestHelper helper) {
        clearExistingWorkers(helper);
        clearVolume(helper, 1, 12, 2, 4, -2, 3);
        buildFloor(helper, 1, 12, -2, 3);
        BlockPos target = new BlockPos(10, 2, 1);
        BlockPos verticalTarget = new BlockPos(2, 11, 1);
        helper.setBlock(target, Blocks.REDSTONE_BLOCK);
        helper.setBlock(verticalTarget, Blocks.REDSTONE_BLOCK);
        WorkerEntity worker = spawnWorker(helper);
        worker.configureTarget(blockId(Blocks.REDSTONE_BLOCK), helper.absolutePos(WORKER));
        worker.setWorkArea(helper.absolutePos(WORKER), 8, 8);
        worker.startJob();

        helper.runAfterDelay(320, () -> {
            helper.assertTrue(helper.getBlockState(target).is(Blocks.REDSTONE_BLOCK),
                    "target nine blocks from center must remain outside radius eight");
            helper.assertTrue(helper.getBlockState(verticalTarget).is(Blocks.REDSTONE_BLOCK),
                    "target nine Y levels from center must remain outside vertical radius eight");
            helper.assertValueEqual(worker.completedBlockCount(), 0, "outside-radius progress");
            worker.stopJob();
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = "minecraft", template = "empty",
            batch = "zzLongRangeDiscoveryNoWork", timeoutTicks = 450)
    public static void targetInsideNoModifyZoneRemainsUntouched(GameTestHelper helper) {
        clearExistingWorkers(helper);
        clearVolume(helper, 1, 10, 2, 4, -2, 3);
        buildFloor(helper, 1, 10, -2, 3);
        List<BlockPos> targets = List.of(new BlockPos(7, 2, 1), new BlockPos(10, 2, 1));
        targets.forEach(target -> helper.setBlock(target, Blocks.REDSTONE_BLOCK));
        WorkerEntity worker = spawnWorker(helper);
        worker.addNoWorkZone(new NoWorkZone(
                helper.getLevel().dimension().location().toString(),
                helper.absolutePos(targets.get(0)), 1, 2));
        worker.addNoWorkZone(new NoWorkZone(
                UUID.randomUUID(), "no-enter",
                helper.getLevel().dimension().location().toString(),
                helper.absolutePos(targets.get(1)), 1, 2,
                NoWorkZoneMode.NO_ENTER, true));
        worker.beginCollection(blockId(Blocks.REDSTONE_BLOCK), helper.absolutePos(WORKER));

        helper.runAfterDelay(320, () -> {
            helper.assertTrue(targets.stream()
                            .allMatch(target -> helper.getBlockState(target).is(Blocks.REDSTONE_BLOCK)),
                    "NO_MODIFY and NO_ENTER targets must remain untouched");
            helper.assertValueEqual(worker.completedBlockCount(), 0, "no-work-zone progress");
            worker.stopJob();
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = "minecraft", template = "empty",
            batch = "zzLongRangeDiscoveryMultiple", timeoutTicks = 12000)
    public static void multipleTargetsUseCachedCandidatesAcrossCollections(GameTestHelper helper) {
        clearExistingWorkers(helper);
        clearVolume(helper, 1, 14, 2, 4, -2, 3);
        buildFloor(helper, 1, 14, -2, 3);
        BlockPos unreachable = new BlockPos(5, 2, 1);
        helper.setBlock(unreachable, Blocks.REDSTONE_BLOCK);
        for (var direction : net.minecraft.core.Direction.values()) {
            helper.setBlock(unreachable.relative(direction), Blocks.BEDROCK);
        }
        List<BlockPos> targets = List.of(new BlockPos(9, 2, 1), new BlockPos(13, 2, 1));
        targets.forEach(target -> helper.setBlock(target, Blocks.REDSTONE_BLOCK));
        WorkerEntity worker = spawnWorker(helper);
        worker.configureTarget(blockId(Blocks.REDSTONE_BLOCK), helper.absolutePos(WORKER));
        worker.setWorkArea(helper.absolutePos(WORKER), 16, 8);
        worker.startJob();

        helper.succeedWhen(() -> {
            helper.assertTrue(targets.stream().allMatch(target -> helper.getBlockState(target).isAir()),
                    "both farther reachable candidates must be collected; "
                            + diagnostic(helper, worker, targets.get(0), blockId(Blocks.REDSTONE_BLOCK)));
            helper.assertTrue(helper.getBlockState(unreachable).is(Blocks.REDSTONE_BLOCK),
                    "the closer bedrock-sealed candidate must remain untouched");
            worker.stopJob();
            helper.assertValueEqual(worker.searchTicketCount(), 0, "released cached-search tickets");
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = "minecraft", template = "empty",
            batch = "zzLongRangeDiscoveryRestart", timeoutTicks = 12000)
    public static void stoppedAndRestartedSearchStillFindsFullRadiusTarget(GameTestHelper helper) {
        clearExistingWorkers(helper);
        clearVolume(helper, 1, 68, 2, 4, -3, 4);
        buildFloor(helper, 1, 68, -3, 4);
        BlockPos target = new BlockPos(65, 2, 1);
        helper.setBlock(target, Blocks.REDSTONE_BLOCK);
        WorkerEntity worker = spawnWorker(helper);
        worker.configureTarget(blockId(Blocks.REDSTONE_BLOCK), helper.absolutePos(WORKER));
        worker.setWorkArea(helper.absolutePos(WORKER), 64, 16);
        worker.startJob();

        helper.runAfterDelay(20, () -> {
            helper.assertValueEqual(worker.stopJob(), WorkerActionResult.STOPPED, "search stops");
            helper.assertValueEqual(worker.searchTicketCount(), 0, "stop releases search tickets");
            helper.assertValueEqual(worker.startJob(), WorkerActionResult.STARTED, "search restarts");
            helper.succeedWhen(() -> assertCollected(helper, worker, target,
                    blockId(Blocks.REDSTONE_BLOCK)));
        });
    }

    @GameTest(templateNamespace = "minecraft", template = "empty",
            batch = "zzLongRangeDiscoveryOffline", timeoutTicks = 12000)
    public static void offlineOwnerDoesNotStopDistantDiscovery(GameTestHelper helper) {
        clearExistingWorkers(helper);
        clearVolume(helper, 1, 52, 2, 4, -3, 4);
        buildFloor(helper, 1, 52, -3, 4);
        BlockPos target = new BlockPos(49, 2, 1);
        helper.setBlock(target, Blocks.REDSTONE_BLOCK);
        WorkerEntity worker = spawnWorker(helper);
        var offlineOwner = helper.makeMockPlayer(GameType.SURVIVAL);
        worker.bindTo(offlineOwner);
        helper.assertTrue(helper.getLevel().getServer().getPlayerList()
                        .getPlayer(offlineOwner.getUUID()) == null,
                "bound owner must not be in the online player list");
        worker.configureTarget(blockId(Blocks.REDSTONE_BLOCK), helper.absolutePos(WORKER));
        worker.setWorkArea(helper.absolutePos(WORKER), 64, 16);
        worker.startJob();

        helper.succeedWhen(() -> assertCollected(helper, worker, target,
                blockId(Blocks.REDSTONE_BLOCK)));
    }

    @GameTest(templateNamespace = "minecraft", template = "empty",
            batch = "zzLongRangeDiscoveryAcceptance", timeoutTicks = 64000)
    public static void acceptanceSequenceCollectsNearAndFarTargets(GameTestHelper helper) {
        runAcceptanceSequence(helper, true);
    }

    @GameTest(templateNamespace = "minecraft", template = "empty",
            batch = "zzLongRangeDiscoveryAcceptanceNoNear", timeoutTicks = 64000)
    public static void acceptanceSequenceWorksWithoutNearbyTarget(GameTestHelper helper) {
        runAcceptanceSequence(helper, false);
    }

    private static void runDistanceCase(
            GameTestHelper helper,
            int distance,
            int horizontalRadius,
            net.minecraft.world.level.block.Block targetBlock) {
        clearExistingWorkers(helper);
        int targetX = WORKER.getX() + distance;
        clearVolume(helper, 1, targetX + 2, 2, 4, -2, 3);
        buildFloor(helper, 1, targetX + 2, -2, 3);
        BlockPos target = new BlockPos(targetX, 2, WORKER.getZ());
        helper.setBlock(target, targetBlock);
        WorkerEntity worker = spawnWorker(helper);
        worker.configureTarget(blockId(targetBlock), helper.absolutePos(WORKER));
        worker.setWorkArea(helper.absolutePos(WORKER), horizontalRadius, 16);
        helper.assertValueEqual(worker.startJob(), WorkerActionResult.STARTED,
                "distance-" + distance + " job starts");
        helper.succeedWhen(() -> assertCollected(
                helper, worker, target, blockId(targetBlock)));
    }

    private static void runAcceptanceSequence(GameTestHelper helper, boolean includeNearby) {
        clearExistingWorkers(helper);
        // GameTest places its tiny "empty" templates close together. Separate
        // the required iron-ore scenarios vertically so each 64-block vertical
        // radius excludes neighboring fixtures while the worker remains in the
        // template's ticking chunk columns. Relative coordinates stay exact.
        BlockPos isolation = new BlockPos(0, includeNearby ? 80 : 160, 0);
        buildAcceptanceRoute(helper, isolation);

        List<BlockPos> allTargets = List.of(
                new BlockPos(4, 40, 1).offset(isolation),   // A = (3,64,0)
                new BlockPos(25, 40, 1).offset(isolation),  // B = (24,64,0)
                new BlockPos(49, 34, 21).offset(isolation), // C = (48,58,20)
                new BlockPos(81, 16, -9).offset(isolation), // D = (80,40,-10)
                new BlockPos(111, 6, 41).offset(isolation));// E = (110,30,40)
        List<BlockPos> expected = includeNearby ? allTargets : allTargets.subList(1, allTargets.size());
        expected.forEach(target -> helper.setBlock(target, Blocks.IRON_ORE));
        BlockPos outside = new BlockPos(131, 40, 1).offset(isolation);
        helper.setBlock(outside, Blocks.IRON_ORE);

        BlockPos workerPosition = ACCEPTANCE_WORKER.offset(isolation);
        WorkerEntity worker = spawnWorker(helper, workerPosition);
        worker.ensureWorkerTickets();
        ResourceLocation targetId = blockId(Blocks.IRON_ORE);
        worker.configureTarget(targetId, helper.absolutePos(workerPosition));
        worker.setWorkArea(helper.absolutePos(workerPosition), 128, 64);

        helper.runAfterDelay(25, () -> {
            for (BlockPos target : allTargets.subList(2, allTargets.size())) {
                ChunkPos chunk = new ChunkPos(helper.absolutePos(target));
                helper.assertTrue(helper.getLevel().getChunkSource().getChunkNow(chunk.x, chunk.z) != null,
                        "the persistent 12-chunk worker view must load acceptance chunk: " + chunk);
            }
            helper.assertValueEqual(worker.startJob(), WorkerActionResult.STARTED,
                    "acceptance job starts");
        });
        for (int delay : new int[]{250, 1_000, 2_500, 4_000, 5_500, 7_500,
                9_000, 11_000, 15_000, 18_000, 24_000, 28_000}) {
            helper.runAfterDelay(delay, () -> assertTicketBudget(helper, worker));
        }
        helper.runAfterDelay(26, () -> helper.succeedWhen(() -> {
            BlockPos remaining = expected.stream()
                    .filter(target -> !helper.getBlockState(target).isAir())
                    .findFirst().orElse(null);
            helper.assertTrue(remaining == null,
                    remaining == null ? "all acceptance targets collected"
                            : diagnostic(helper, worker, remaining, targetId));
            helper.assertTrue(helper.getBlockState(outside).is(Blocks.IRON_ORE),
                    "target outside radius 128 must remain untouched");
            assertTicketBudget(helper, worker);
            LogUtils.getLogger().info("[Baritone Helper/Search Benchmark] maxScanMs={}",
                    worker.maxSearchTickNanos() / 1_000_000.0);
            worker.stopJob();
            helper.assertValueEqual(worker.searchTicketCount(), 0,
                    "acceptance stop releases search tickets");
            helper.assertValueEqual(worker.workerTicketCount(), WorkerEntity.MAX_WORKER_TICKETS,
                    "acceptance stop retains the worker view");
        }));
    }

    private static void assertTicketBudget(GameTestHelper helper, WorkerEntity worker) {
        helper.assertTrue(worker.searchTicketCount() <= 4,
                "search tickets exceed frontier budget: " + worker.searchTicketCount());
        helper.assertTrue(worker.totalTicketCount() <= WorkerEntity.MAX_WORKER_TICKETS,
                "total tickets exceed worker ceiling: " + worker.totalTicketCount());
        helper.assertTrue(worker.maxSearchTickNanos() < 50_000_000L,
                "search scan exceeded 50 ms: " + (worker.maxSearchTickNanos() / 1_000_000.0) + " ms");
    }

    private static void assertCollected(
            GameTestHelper helper,
            WorkerEntity worker,
            BlockPos target,
            ResourceLocation targetId) {
        helper.assertTrue(helper.getBlockState(target).isAir(),
                diagnostic(helper, worker, target, targetId));
        assertTicketBudget(helper, worker);
        worker.stopJob();
        helper.assertValueEqual(worker.searchTicketCount(), 0, "released search tickets");
        helper.assertValueEqual(worker.workerTicketCount(), WorkerEntity.MAX_WORKER_TICKETS,
                "stopped worker retains loaded view");
        helper.succeed();
    }

    private static WorkerEntity spawnWorker(GameTestHelper helper) {
        return spawnWorker(helper, WORKER);
    }

    private static WorkerEntity spawnWorker(GameTestHelper helper, BlockPos position) {
        helper.setBlock(position.below(), Blocks.STONE);
        return helper.spawn(
                BaritoneHelper.BARITONE_HELPER_ENTITY.get(),
                position.getX(),
                position.getY(),
                position.getZ());
    }

    private static void clearExistingWorkers(GameTestHelper helper) {
        for (var existing : helper.getLevel().getAllEntities()) {
            if (existing instanceof WorkerEntity worker) {
                worker.discard();
            }
        }
    }

    private static void buildFloor(
            GameTestHelper helper,
            int minX,
            int maxX,
            int minZ,
            int maxZ) {
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                helper.setBlock(x, 1, z, Blocks.STONE);
            }
        }
    }

    private static void buildAcceptanceRoute(GameTestHelper helper, BlockPos offset) {
        // A broad terraced surface makes every exact acceptance coordinate
        // reachable regardless of which discovered target wins first.
        for (int x = 1; x <= 132; x++) {
            int feetY;
            if (x <= 25) feetY = 40;
            else if (x <= 49) feetY = interpolatedY(40, 34, x - 25, 24);
            else if (x <= 81) feetY = interpolatedY(34, 16, x - 49, 32);
            else if (x <= 111) feetY = interpolatedY(16, 6, x - 81, 30);
            else feetY = 6;
            for (int z = -12; z <= 42; z++) {
                int worldX = x + offset.getX();
                int worldY = feetY + offset.getY();
                int worldZ = z + offset.getZ();
                helper.setBlock(worldX, worldY - 1, worldZ, Blocks.STONE);
                helper.setBlock(worldX, worldY, worldZ, Blocks.AIR);
                helper.setBlock(worldX, worldY + 1, worldZ, Blocks.AIR);
                helper.setBlock(worldX, worldY + 2, worldZ, Blocks.AIR);
            }
        }
    }

    private static int interpolatedY(int startY, int endY, int index, int length) {
        if (length == 0) return endY;
        return (int) Math.round(startY + (endY - startY) * (index / (double) length));
    }

    private static void clearPath(
            GameTestHelper helper,
            int minX,
            int maxX,
            int minZ,
            int maxZ) {
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                helper.setBlock(x, 2, z, Blocks.AIR);
                helper.setBlock(x, 3, z, Blocks.AIR);
            }
        }
    }

    private static void clearVolume(
            GameTestHelper helper,
            int minX,
            int maxX,
            int minY,
            int maxY,
            int minZ,
            int maxZ) {
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) helper.setBlock(x, y, z, Blocks.AIR);
            }
        }
    }

    private static ResourceLocation blockId(net.minecraft.world.level.block.Block block) {
        return BuiltInRegistries.BLOCK.getKey(block);
    }

    private static String diagnostic(GameTestHelper helper, WorkerEntity worker, BlockPos target) {
        return diagnostic(helper, worker, target, blockId(Blocks.REDSTONE_BLOCK));
    }

    private static String diagnostic(
            GameTestHelper helper,
            WorkerEntity worker,
            BlockPos target,
            ResourceLocation targetId) {
        BlockPos absoluteTarget = helper.absolutePos(target);
        return "target=" + target
                + ", absoluteTarget=" + absoluteTarget
                + ", state=" + helper.getLevel().getBlockState(absoluteTarget)
                + ", center=" + worker.workAreaCenter()
                + ", targetId=" + worker.targetBlockId()
                + ", noModify=" + worker.isInsideNoModify(absoluteTarget)
                + ", noEnter=" + worker.isInsideNoEnter(absoluteTarget)
                + ", collectable=" + WorkerPlanner.isCollectable(
                        helper.getLevel(), worker, absoluteTarget, targetId)
                + ", worker=" + worker.position()
                + ", yRot=" + worker.getYRot()
                + ", xRot=" + worker.getXRot()
                + ", selected=" + worker.baritoneEngine().getEntityContext().getSelectedBlock()
                + ", click=" + worker.baritoneEngine().getInputOverrideHandler()
                        .isInputForcedDown(Input.CLICK_LEFT)
                + ", passable=" + MovementHelper.canWalkThrough(
                        worker.baritoneEngine().getEntityContext(), new BetterBlockPos(absoluteTarget))
                + ", reachable=" + RotationUtils.reachable(worker, absoluteTarget, 4.5)
                + ", xxa=" + worker.xxa
                + ", zza=" + worker.zza
                + ", onGround=" + worker.onGround()
                + ", delta=" + worker.getDeltaMovement()
                + ", job=" + worker.job()
                + ", activity=" + worker.activity()
                + ", reason=" + worker.blockReason()
                + ", managerMining=" + worker.interactionManagerMining()
                + ", breakProgress=" + worker.blockBreakingProgress()
                + ", tickets=" + worker.workerTicketCount()
                + ", searchTickets=" + worker.searchTicketCount()
                + ", maxSearchMs=" + (worker.maxSearchTickNanos() / 1_000_000.0)
                + ", pathStatus=" + worker.pathingStatus()
                + ", pathNode=" + worker.currentPathNode()
                + ", pathLength=" + worker.currentPathLength()
                + ", pathCost=" + worker.currentPathCost()
                + ", chunkNow=" + (helper.getLevel().getChunkSource().getChunkNow(
                        new ChunkPos(absoluteTarget).x, new ChunkPos(absoluteTarget).z) != null);
    }
}

package dev.dudie.baritonehelper.gametest;

import com.mojang.logging.LogUtils;
import dev.dudie.baritonehelper.BaritoneHelper;
import dev.dudie.baritonehelper.entity.WorkerEntity;
import dev.dudie.baritonehelper.internal.baritone.api.utils.BlockUtils;
import dev.dudie.baritonehelper.internal.baritone.cache.CoverageState;
import dev.dudie.baritonehelper.internal.baritone.cache.SharedWorldKnowledge;
import dev.dudie.baritonehelper.internal.baritone.cache.WorldKnowledgeEvents;
import dev.dudie.baritonehelper.worker.NoWorkZone;
import dev.dudie.baritonehelper.worker.NoWorkZoneMode;
import dev.dudie.baritonehelper.worker.SearchMode;
import dev.dudie.baritonehelper.worker.WorkerActionResult;
import dev.dudie.baritonehelper.worker.WorkerBlockReason;
import dev.dudie.baritonehelper.worker.WorkerJob;
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
    private static final int LANE_STRIDE = 512;
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
        BlockPos offset = scenarioOffset(1);
        clearPath(helper, offset, 1, 96, -64, 64);
        buildFloor(helper, offset, 1, 96, -64, 64);
        BlockPos target = new BlockPos(96, 2, 1).offset(offset);
        helper.setBlock(target, Blocks.REDSTONE_BLOCK);
        BlockPos workerPosition = WORKER.offset(offset);
        WorkerEntity worker = spawnWorker(helper, workerPosition);
        BlockPos center = helper.absolutePos(workerPosition);
        worker.configureTarget(blockId(Blocks.REDSTONE_BLOCK), center);
        worker.setWorkArea(center, 128, 64);

        helper.assertValueEqual(worker.startJob().name(), "STARTED", "job starts");
        helper.succeedWhen(() -> {
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
        BlockPos offset = scenarioOffset(2);
        clearPath(helper, offset, 1, 12, -4, 5);
        buildFloor(helper, offset, 1, 12, -4, 5);
        var targetBlock = Blocks.MAGENTA_GLAZED_TERRACOTTA;
        for (int z = 0; z <= 2; z++) {
            helper.setBlock(new BlockPos(4, 2, z).offset(offset), Blocks.BEDROCK);
            helper.setBlock(new BlockPos(4, 3, z).offset(offset), Blocks.BEDROCK);
        }
        BlockPos target = new BlockPos(10, 2, 1).offset(offset);
        helper.setBlock(target, targetBlock);
        BlockPos workerPosition = WORKER.offset(offset);
        WorkerEntity worker = spawnWorker(helper, workerPosition);
        BlockPos absoluteTarget = helper.absolutePos(target);
        helper.assertFalse(
                WorkerPlanner.hasLineOfSight(helper.getLevel(), worker, absoluteTarget),
                "the target must not be visible from the worker's current position");
        helper.assertTrue(
                WorkerPlanner.nearestWorkPosition(helper.getLevel(), worker, absoluteTarget).isPresent(),
                "the target must have a future interaction position");

        worker.setWorkArea(helper.absolutePos(workerPosition), 16, 8);
        worker.beginCollection(blockId(targetBlock), helper.absolutePos(workerPosition));

        helper.succeedWhen(() -> assertCollected(helper, worker, target, blockId(targetBlock)));
    }

    @GameTest(templateNamespace = "minecraft", template = "empty",
            batch = "zzLongRangeDiscoveryRange04", timeoutTicks = 12000)
    public static void targetAtFourBlocksIsCollected(GameTestHelper helper) {
        runDistanceCase(helper, 3, 4, 4, Blocks.LIME_CONCRETE);
    }

    @GameTest(templateNamespace = "minecraft", template = "empty",
            batch = "zzLongRangeDiscoveryRange16", timeoutTicks = 12000)
    public static void targetAtSixteenBlocksIsCollected(GameTestHelper helper) {
        runDistanceCase(helper, 4, 16, 8, Blocks.EMERALD_BLOCK);
    }

    @GameTest(templateNamespace = "minecraft", template = "empty",
            batch = "zzLongRangeDiscoveryRange32", timeoutTicks = 12000)
    public static void targetAtThirtyTwoBlocksIsCollected(GameTestHelper helper) {
        runDistanceCase(helper, 5, 32, 16, Blocks.DIAMOND_BLOCK);
    }

    @GameTest(templateNamespace = "minecraft", template = "empty",
            batch = "zzLongRangeDiscoveryRange64", timeoutTicks = 12000)
    public static void targetAtSixtyFourBlocksIsCollected(GameTestHelper helper) {
        runDistanceCase(helper, 6, 64, 32, Blocks.LAPIS_BLOCK);
    }

    @GameTest(templateNamespace = "minecraft", template = "empty",
            batch = "zzLongRangeDiscoveryRange128", timeoutTicks = 24000)
    public static void targetAtOneHundredTwentyEightBlocksIsCollected(GameTestHelper helper) {
        runDistanceCase(helper, 7, 128, 64, Blocks.NETHERITE_BLOCK);
    }

    @GameTest(templateNamespace = "minecraft", template = "empty",
            batch = "zzLongRangeDiscoveryRange256", timeoutTicks = 64000)
    public static void targetAtTwoHundredFiftySixBlocksIsCollected(GameTestHelper helper) {
        runPrecoveredDistanceCase(helper, 20, 256, Blocks.PRISMARINE_BRICKS);
    }

    @GameTest(templateNamespace = "minecraft", template = "empty",
            batch = "zzLongRangeDiscoveryRange512", timeoutTicks = 128000)
    public static void targetAtFiveHundredTwelveBlocksIsCollected(GameTestHelper helper) {
        runPrecoveredDistanceCase(helper, 22, 512, Blocks.END_STONE_BRICKS);
    }

    @GameTest(templateNamespace = "minecraft", template = "empty",
            batch = "zzLongRangeDiscoveryEmpty", timeoutTicks = 12000)
    public static void emptyWorkAreaCompletesOnlyAfterExhaustiveCoverage(GameTestHelper helper) {
        clearExistingWorkers(helper);
        BlockPos offset = scenarioOffset(23);
        buildFloor(helper, offset, -8, 10, -8, 10);
        BlockPos workerPosition = WORKER.offset(offset);
        WorkerEntity worker = spawnWorker(helper, workerPosition);
        BlockPos workCenter = helper.absolutePos(workerPosition);
        ResourceLocation targetId = blockId(Blocks.BLUE_ICE);
        worker.configureTarget(targetId, workCenter);
        worker.setWorkArea(workCenter, 8, 8);
        helper.assertValueEqual(worker.startJob(), WorkerActionResult.STARTED,
                "empty-area job starts");

        helper.succeedWhen(() -> {
            helper.assertValueEqual(worker.job(), WorkerJob.BLOCKED,
                    "empty exhaustive search reaches a terminal job state");
            helper.assertValueEqual(worker.blockReason(), WorkerBlockReason.NO_MATCHING_BLOCKS,
                    "empty exhaustive search reports no matching blocks");
            assertEveryEligibleChunkScanned(helper, targetId, workCenter, 8);
        });
    }

    @GameTest(templateNamespace = "minecraft", template = "empty",
            batch = "zzLongRangeDiscoveryRoamLate", timeoutTicks = 24000)
    public static void roamDiscoversTargetIntroducedAfterStart(GameTestHelper helper) {
        clearExistingWorkers(helper);
        BlockPos offset = scenarioOffset(24);
        buildFloor(helper, offset, -64, 64, -64, 64);
        BlockPos workerPosition = WORKER.offset(offset);
        BlockPos target = new BlockPos(48, 2, 1).offset(offset);
        BlockPos absoluteTarget = helper.absolutePos(target);
        ResourceLocation targetId = blockId(Blocks.CHISELED_TUFF_BRICKS);
        WorkerEntity worker = spawnWorker(helper, workerPosition);
        worker.configureTarget(targetId, helper.absolutePos(workerPosition));
        worker.setSearchMode(SearchMode.ROAM);
        worker.baritoneEngine().settings().exploreMaintainY
                .set(helper.absolutePos(workerPosition).getY());
        SharedWorldKnowledge.get(helper.getLevel()).ledger().markScanned(
                coverageKey(targetId), new ChunkPos(absoluteTarget).toLong());
        helper.assertValueEqual(worker.startJob(), WorkerActionResult.STARTED,
                "roam job starts without a known target");

        helper.runAfterDelay(200, () -> {
            helper.setBlock(target, Blocks.CHISELED_TUFF_BRICKS);
            WorldKnowledgeEvents.markDirty(helper.getLevel(), absoluteTarget);
            helper.succeedWhen(() -> assertCollected(helper, worker, target, targetId));
        });
    }

    @GameTest(templateNamespace = "minecraft", template = "empty",
            batch = "zzLongRangeDiscoveryCorner", timeoutTicks = 12000)
    public static void targetAroundCornerIsCollected(GameTestHelper helper) {
        clearExistingWorkers(helper);
        BlockPos offset = scenarioOffset(8);
        clearVolume(helper, offset, 1, 12, 2, 4, -3, 4);
        buildFloor(helper, offset, 1, 12, -3, 4);
        for (int y = 2; y <= 3; y++) {
            for (int z = -1; z <= 1; z++) {
                helper.setBlock(new BlockPos(4, y, z).offset(offset), Blocks.BEDROCK);
            }
            for (int x = 4; x <= 7; x++) {
                helper.setBlock(new BlockPos(x, y, 0).offset(offset), Blocks.BEDROCK);
            }
        }
        var targetBlock = Blocks.ORANGE_GLAZED_TERRACOTTA;
        BlockPos target = new BlockPos(9, 2, 1).offset(offset);
        helper.setBlock(target, targetBlock);
        BlockPos workerPosition = WORKER.offset(offset);
        WorkerEntity worker = spawnWorker(helper, workerPosition);
        BlockPos absoluteTarget = helper.absolutePos(target);
        helper.assertFalse(WorkerPlanner.hasLineOfSight(helper.getLevel(), worker, absoluteTarget),
                "corner target must start outside direct line of sight");
        worker.setWorkArea(helper.absolutePos(workerPosition), 16, 8);
        worker.beginCollection(blockId(targetBlock), helper.absolutePos(workerPosition));

        helper.succeedWhen(() -> assertCollected(helper, worker, target, blockId(targetBlock)));
    }

    @GameTest(templateNamespace = "minecraft", template = "empty",
            batch = "zzLongRangeDiscoveryUnderground", timeoutTicks = 12000)
    public static void undergroundTunnelTargetIsCollected(GameTestHelper helper) {
        clearExistingWorkers(helper);
        BlockPos offset = scenarioOffset(9);
        clearVolume(helper, offset, 1, 12, 2, 5, -3, 4);
        buildFloor(helper, offset, 1, 12, -3, 4);
        for (int z = 0; z <= 2; z++) {
            helper.setBlock(new BlockPos(5, 2, z).offset(offset), Blocks.BEDROCK);
            helper.setBlock(new BlockPos(5, 3, z).offset(offset), Blocks.BEDROCK);
        }
        for (int x = 6; x <= 11; x++) {
            for (int z = 0; z <= 2; z++) {
                helper.setBlock(new BlockPos(x, 4, z).offset(offset), Blocks.STONE);
            }
        }
        var targetBlock = Blocks.CYAN_GLAZED_TERRACOTTA;
        BlockPos target = new BlockPos(10, 2, 1).offset(offset);
        helper.setBlock(target, targetBlock);
        BlockPos workerPosition = WORKER.offset(offset);
        WorkerEntity worker = spawnWorker(helper, workerPosition);
        helper.assertFalse(WorkerPlanner.hasLineOfSight(
                helper.getLevel(), worker, helper.absolutePos(target)),
                "underground target must start occluded by the tunnel entrance");
        worker.setWorkArea(helper.absolutePos(workerPosition), 16, 8);
        worker.beginCollection(blockId(targetBlock), helper.absolutePos(workerPosition));

        helper.succeedWhen(() -> assertCollected(helper, worker, target, blockId(targetBlock)));
    }

    @GameTest(templateNamespace = "minecraft", template = "empty",
            batch = "zzLongRangeDiscoveryVertical", timeoutTicks = 12000)
    public static void verticalTargetInsideRadiusIsCollected(GameTestHelper helper) {
        clearExistingWorkers(helper);
        BlockPos offset = scenarioOffset(10);
        clearVolume(helper, offset, 1, 8, 2, 7, 0, 2);
        buildFloor(helper, offset, 1, 8, 0, 2);
        var targetBlock = Blocks.BONE_BLOCK;
        BlockPos target = new BlockPos(7, 5, 1).offset(offset);
        helper.setBlock(target, targetBlock);
        BlockPos workerPosition = WORKER.offset(offset);
        WorkerEntity worker = spawnWorker(helper, workerPosition);
        worker.configureTarget(blockId(targetBlock), helper.absolutePos(workerPosition));
        worker.setWorkArea(helper.absolutePos(workerPosition), 16, 8);
        helper.assertValueEqual(worker.startJob(), WorkerActionResult.STARTED, "vertical job starts");

        helper.succeedWhen(() -> assertCollected(helper, worker, target, blockId(targetBlock)));
    }

    @GameTest(templateNamespace = "minecraft", template = "empty",
            batch = "zzLongRangeDiscoveryBoundary", timeoutTicks = 450)
    public static void targetOutsideHorizontalRadiusRemainsUntouched(GameTestHelper helper) {
        clearExistingWorkers(helper);
        BlockPos offset = scenarioOffset(11);
        clearVolume(helper, offset, 1, 12, 2, 4, -2, 3);
        buildFloor(helper, offset, 1, 12, -2, 3);
        var targetBlock = Blocks.PURPLE_CONCRETE;
        BlockPos target = new BlockPos(10, 2, 1).offset(offset);
        BlockPos verticalTarget = new BlockPos(2, 11, 1).offset(offset);
        helper.setBlock(target, targetBlock);
        helper.setBlock(verticalTarget, targetBlock);
        BlockPos workerPosition = WORKER.offset(offset);
        WorkerEntity worker = spawnWorker(helper, workerPosition);
        worker.configureTarget(blockId(targetBlock), helper.absolutePos(workerPosition));
        worker.setWorkArea(helper.absolutePos(workerPosition), 8, 8);
        worker.startJob();

        helper.runAfterDelay(320, () -> {
            helper.assertTrue(helper.getBlockState(target).is(targetBlock),
                    "target nine blocks from center must remain outside radius eight");
            helper.assertTrue(helper.getBlockState(verticalTarget).is(targetBlock),
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
        BlockPos offset = scenarioOffset(12);
        clearVolume(helper, offset, 1, 10, 2, 4, -2, 3);
        buildFloor(helper, offset, 1, 10, -2, 3);
        List<BlockPos> targets = List.of(
                new BlockPos(7, 2, 1).offset(offset),
                new BlockPos(10, 2, 1).offset(offset));
        targets.forEach(target -> helper.setBlock(target, Blocks.PURPUR_BLOCK));
        BlockPos workerPosition = WORKER.offset(offset);
        WorkerEntity worker = spawnWorker(helper, workerPosition);
        worker.addNoWorkZone(new NoWorkZone(
                helper.getLevel().dimension().location().toString(),
                helper.absolutePos(targets.get(0)), 1, 2));
        worker.addNoWorkZone(new NoWorkZone(
                UUID.randomUUID(), "no-enter",
                helper.getLevel().dimension().location().toString(),
                helper.absolutePos(targets.get(1)), 1, 2,
                NoWorkZoneMode.NO_ENTER, true));
        worker.setWorkArea(helper.absolutePos(workerPosition), 16, 8);
        worker.beginCollection(blockId(Blocks.PURPUR_BLOCK), helper.absolutePos(workerPosition));

        helper.runAfterDelay(320, () -> {
            helper.assertTrue(targets.stream()
                            .allMatch(target -> helper.getBlockState(target).is(Blocks.PURPUR_BLOCK)),
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
        BlockPos offset = scenarioOffset(13);
        clearVolume(helper, offset, 1, 14, 2, 4, -2, 3);
        buildFloor(helper, offset, 1, 14, -2, 3);
        var targetBlock = Blocks.AMETHYST_BLOCK;
        BlockPos unreachable = new BlockPos(5, 2, 1).offset(offset);
        helper.setBlock(unreachable, targetBlock);
        for (var direction : net.minecraft.core.Direction.values()) {
            helper.setBlock(unreachable.relative(direction), Blocks.BEDROCK);
        }
        List<BlockPos> targets = List.of(
                new BlockPos(9, 2, 1).offset(offset),
                new BlockPos(13, 2, 1).offset(offset));
        targets.forEach(target -> helper.setBlock(target, targetBlock));
        BlockPos workerPosition = WORKER.offset(offset);
        WorkerEntity worker = spawnWorker(helper, workerPosition);
        worker.configureTarget(blockId(targetBlock), helper.absolutePos(workerPosition));
        worker.setWorkArea(helper.absolutePos(workerPosition), 16, 8);
        worker.startJob();

        helper.succeedWhen(() -> {
            helper.assertTrue(targets.stream().allMatch(target -> helper.getBlockState(target).isAir()),
                    "both farther reachable candidates must be collected; "
                            + diagnostic(helper, worker, targets.get(0), blockId(targetBlock)));
            helper.assertTrue(helper.getBlockState(unreachable).is(targetBlock),
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
        BlockPos offset = scenarioOffset(14);
        clearVolume(helper, offset, 1, 68, 2, 4, -3, 4);
        buildFloor(helper, offset, 1, 68, -3, 4);
        var targetBlock = Blocks.BRICKS;
        BlockPos target = new BlockPos(65, 2, 1).offset(offset);
        helper.setBlock(target, targetBlock);
        BlockPos workerPosition = WORKER.offset(offset);
        WorkerEntity worker = spawnWorker(helper, workerPosition);
        worker.configureTarget(blockId(targetBlock), helper.absolutePos(workerPosition));
        worker.setWorkArea(helper.absolutePos(workerPosition), 64, 16);
        worker.startJob();

        helper.runAfterDelay(20, () -> {
            helper.assertValueEqual(worker.stopJob(), WorkerActionResult.STOPPED, "search stops");
            helper.assertValueEqual(worker.searchTicketCount(), 0, "stop releases search tickets");
            helper.assertValueEqual(worker.startJob(), WorkerActionResult.STARTED, "search restarts");
            helper.succeedWhen(() -> assertCollected(helper, worker, target, blockId(targetBlock)));
        });
    }

    @GameTest(templateNamespace = "minecraft", template = "empty",
            batch = "zzLongRangeDiscoveryOffline", timeoutTicks = 12000)
    public static void offlineOwnerDoesNotStopDistantDiscovery(GameTestHelper helper) {
        clearExistingWorkers(helper);
        BlockPos offset = scenarioOffset(15);
        clearVolume(helper, offset, 1, 52, 2, 4, -3, 4);
        buildFloor(helper, offset, 1, 52, -3, 4);
        var targetBlock = Blocks.RAW_COPPER_BLOCK;
        BlockPos target = new BlockPos(49, 2, 1).offset(offset);
        helper.setBlock(target, targetBlock);
        BlockPos workerPosition = WORKER.offset(offset);
        WorkerEntity worker = spawnWorker(helper, workerPosition);
        var offlineOwner = helper.makeMockPlayer(GameType.SURVIVAL);
        worker.bindTo(offlineOwner);
        helper.assertTrue(helper.getLevel().getServer().getPlayerList()
                        .getPlayer(offlineOwner.getUUID()) == null,
                "bound owner must not be in the online player list");
        worker.configureTarget(blockId(targetBlock), helper.absolutePos(workerPosition));
        worker.setWorkArea(helper.absolutePos(workerPosition), 64, 16);
        worker.startJob();

        helper.succeedWhen(() -> assertCollected(helper, worker, target, blockId(targetBlock)));
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
            int lane,
            int distance,
            int horizontalRadius,
            net.minecraft.world.level.block.Block targetBlock) {
        clearExistingWorkers(helper);
        BlockPos offset = scenarioOffset(lane);
        int targetX = WORKER.getX() + distance;
        clearVolume(helper, offset, 1, targetX + 2, 2, 4, -2, 3);
        BlockPos workCenter = WORKER.offset(distance / 2, 0, 0);
        buildFloor(
                helper,
                offset,
                workCenter.getX() - horizontalRadius,
                workCenter.getX() + horizontalRadius,
                workCenter.getZ() - horizontalRadius,
                workCenter.getZ() + horizontalRadius);
        BlockPos target = new BlockPos(targetX, 2, WORKER.getZ()).offset(offset);
        helper.setBlock(target, targetBlock);
        BlockPos workerPosition = WORKER.offset(offset);
        WorkerEntity worker = spawnWorker(helper, workerPosition);
        worker.configureTarget(blockId(targetBlock), helper.absolutePos(workerPosition));
        worker.setWorkArea(helper.absolutePos(workCenter.offset(offset)), horizontalRadius, 16);
        helper.assertValueEqual(worker.startJob(), WorkerActionResult.STARTED,
                "distance-" + distance + " job starts");
        helper.succeedWhen(() -> assertCollected(
                helper, worker, target, blockId(targetBlock)));
    }

    private static void runPrecoveredDistanceCase(
            GameTestHelper helper,
            int lane,
            int distance,
            net.minecraft.world.level.block.Block targetBlock) {
        clearExistingWorkers(helper);
        BlockPos offset = scenarioOffset(lane);
        int targetX = WORKER.getX() + distance;
        clearVolume(helper, offset, 1, targetX + 2, 2, 4, 0, 2);
        buildUnbreakableFloor(helper, offset, 1, targetX + 2, 0, 2);
        BlockPos target = new BlockPos(targetX, 2, WORKER.getZ()).offset(offset);
        helper.setBlock(target, targetBlock);
        BlockPos workerPosition = WORKER.offset(offset);
        WorkerEntity worker = spawnWorker(helper, workerPosition);
        helper.setBlock(workerPosition.below(), Blocks.BEDROCK);
        BlockPos workCenter = WORKER.offset(distance / 2, 0, 0).offset(offset);
        int workRadius = distance / 2 + 16;
        ResourceLocation targetId = blockId(targetBlock);
        primeEmptyCoverageExceptTarget(
                helper,
                targetId,
                helper.absolutePos(workCenter),
                workRadius,
                helper.absolutePos(workerPosition),
                helper.absolutePos(target));
        worker.configureTarget(targetId, helper.absolutePos(workerPosition));
        worker.setWorkArea(helper.absolutePos(workCenter), workRadius, 16);
        helper.assertValueEqual(worker.startJob(), WorkerActionResult.STARTED,
                "distance-" + distance + " job starts");
        helper.succeedWhen(() -> assertCollected(helper, worker, target, targetId));
    }

    private static void primeEmptyCoverageExceptTarget(
            GameTestHelper helper,
            ResourceLocation target,
            BlockPos center,
            int radius,
            BlockPos workerPosition,
            BlockPos targetPosition) {
        var ledger = SharedWorldKnowledge.get(helper.getLevel()).ledger();
        long targetChunk = new ChunkPos(targetPosition).toLong();
        int minChunkX = Math.floorDiv(center.getX() - radius, 16);
        int maxChunkX = Math.floorDiv(center.getX() + radius, 16);
        int minChunkZ = Math.floorDiv(center.getZ() - radius, 16);
        int maxChunkZ = Math.floorDiv(center.getZ() + radius, 16);
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                long chunk = ChunkPos.asLong(chunkX, chunkZ);
                ledger.markScanned(coverageKey(target), chunk);
            }
        }
        int workerChunkX = new ChunkPos(workerPosition).x;
        ChunkPos targetChunkPos = new ChunkPos(targetChunk);
        int step = Integer.compare(targetChunkPos.x, workerChunkX);
        for (int chunkX = workerChunkX; ; chunkX += step) {
            ledger.markDirty(ChunkPos.asLong(chunkX, targetChunkPos.z));
            if (chunkX == targetChunkPos.x) break;
        }
    }

    private static void assertEveryEligibleChunkScanned(
            GameTestHelper helper,
            ResourceLocation target,
            BlockPos center,
            int radius) {
        var ledger = SharedWorldKnowledge.get(helper.getLevel()).ledger();
        int minChunkX = Math.floorDiv(center.getX() - radius, 16);
        int maxChunkX = Math.floorDiv(center.getX() + radius, 16);
        int minChunkZ = Math.floorDiv(center.getZ() - radius, 16);
        int maxChunkZ = Math.floorDiv(center.getZ() + radius, 16);
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                ChunkPos chunk = new ChunkPos(chunkX, chunkZ);
                int nearestX = Math.max(chunk.getMinBlockX(),
                        Math.min(chunk.getMaxBlockX(), center.getX()));
                int nearestZ = Math.max(chunk.getMinBlockZ(),
                        Math.min(chunk.getMaxBlockZ(), center.getZ()));
                long dx = (long) nearestX - center.getX();
                long dz = (long) nearestZ - center.getZ();
                if (dx * dx + dz * dz > (long) radius * radius) continue;
                helper.assertValueEqual(
                        ledger.state(coverageKey(target), chunk.toLong()),
                        CoverageState.SCANNED,
                        "eligible chunk " + chunk + " coverage");
            }
        }
    }

    private static void runAcceptanceSequence(GameTestHelper helper, boolean includeNearby) {
        clearExistingWorkers(helper);
        // GameTest places tiny templates close together. Separate the two
        // same-target acceptance scenarios vertically and into dedicated lanes.
        BlockPos isolation = new BlockPos(
                0,
                includeNearby ? 80 : 160,
                (includeNearby ? 16 : 17) * LANE_STRIDE);
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
        ResourceLocation targetId = blockId(Blocks.IRON_ORE);
        worker.configureTarget(targetId, helper.absolutePos(workerPosition));
        worker.setWorkArea(helper.absolutePos(workerPosition), 128, 64);
        helper.assertValueEqual(worker.startJob(), WorkerActionResult.STARTED,
                "acceptance job starts");

        helper.runAfterDelay(25, () -> assertTicketBudget(helper, worker));
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
            helper.assertValueEqual(worker.workerTicketCount(), 0,
                    "acceptance stop releases the worker view");
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
        helper.assertValueEqual(worker.workerTicketCount(), 0,
                "stopped worker releases loaded view");
        helper.succeed();
    }

    private static WorkerEntity spawnWorker(GameTestHelper helper, BlockPos position) {
        helper.setBlock(position.below(), Blocks.STONE);
        // GameTestHelper mutates chunks directly and therefore bypasses the
        // NeoForge placement/break events used by production invalidation.
        // Model an uncertain fixture mutation before each isolated scenario.
        BlockPos absolute = helper.absolutePos(position);
        int centerChunkX = Math.floorDiv(absolute.getX(), 16);
        int centerChunkZ = Math.floorDiv(absolute.getZ(), 16);
        for (int dx = -9; dx <= 9; dx++) {
            for (int dz = -9; dz <= 9; dz++) {
                SharedWorldKnowledge.get(helper.getLevel()).cachedWorld()
                        .markDirty(ChunkPos.asLong(centerChunkX + dx, centerChunkZ + dz));
            }
        }
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
            BlockPos offset,
            int minX,
            int maxX,
            int minZ,
            int maxZ) {
        for (int x = minX - 16; x <= maxX + 16; x++) {
            for (int z = minZ - 16; z <= maxZ + 16; z++) {
                helper.setBlock(new BlockPos(x, 1, z).offset(offset), Blocks.STONE);
                helper.setBlock(new BlockPos(x, 2, z).offset(offset), Blocks.AIR);
                helper.setBlock(new BlockPos(x, 3, z).offset(offset), Blocks.AIR);
                helper.setBlock(new BlockPos(x, 4, z).offset(offset), Blocks.AIR);
            }
        }
    }

    private static void buildUnbreakableFloor(
            GameTestHelper helper,
            BlockPos offset,
            int minX,
            int maxX,
            int minZ,
            int maxZ) {
        buildFloor(helper, offset, minX, maxX, minZ, maxZ);
        for (int x = minX - 16; x <= maxX + 16; x++) {
            for (int z = minZ - 16; z <= maxZ + 16; z++) {
                helper.setBlock(new BlockPos(x, 1, z).offset(offset), Blocks.BEDROCK);
            }
        }
    }

    private static void buildAcceptanceRoute(GameTestHelper helper, BlockPos offset) {
        // A broad terraced surface makes every exact acceptance coordinate
        // reachable regardless of which discovered target wins first.
        for (int x = -15; x <= 148; x++) {
            int feetY;
            if (x <= 25) feetY = 40;
            else if (x <= 49) feetY = interpolatedY(40, 34, x - 25, 24);
            else if (x <= 81) feetY = interpolatedY(34, 16, x - 49, 32);
            else if (x <= 111) feetY = interpolatedY(16, 6, x - 81, 30);
            else feetY = 6;
            for (int z = -28; z <= 58; z++) {
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
            BlockPos offset,
            int minX,
            int maxX,
            int minZ,
            int maxZ) {
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                helper.setBlock(new BlockPos(x, 2, z).offset(offset), Blocks.AIR);
                helper.setBlock(new BlockPos(x, 3, z).offset(offset), Blocks.AIR);
            }
        }
    }

    private static void clearVolume(
            GameTestHelper helper,
            BlockPos offset,
            int minX,
            int maxX,
            int minY,
            int maxY,
            int minZ,
            int maxZ) {
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    helper.setBlock(new BlockPos(x, y, z).offset(offset), Blocks.AIR);
                }
            }
        }
    }

    private static BlockPos scenarioOffset(int lane) {
        return new BlockPos(0, 0, lane * LANE_STRIDE);
    }

    private static ResourceLocation blockId(net.minecraft.world.level.block.Block block) {
        return BuiltInRegistries.BLOCK.getKey(block);
    }

    private static String coverageKey(ResourceLocation target) {
        return BlockUtils.blockToString(BuiltInRegistries.BLOCK.get(target));
    }

    private static String diagnostic(
            GameTestHelper helper,
            WorkerEntity worker,
            BlockPos target,
            ResourceLocation targetId) {
        BlockPos absoluteTarget = helper.absolutePos(target);
        return "target=" + absoluteTarget
                + ", state=" + helper.getLevel().getBlockState(absoluteTarget)
                + ", center=" + worker.workAreaCenter()
                + ", canEnter=" + worker.canEnterAt(absoluteTarget)
                + ", canModify=" + worker.canModifyAt(absoluteTarget)
                + ", mobGriefing=" + helper.getLevel().getGameRules().getBoolean(
                        net.minecraft.world.level.GameRules.RULE_MOBGRIEFING)
                + ", collectable=" + WorkerPlanner.isCollectable(
                        helper.getLevel(), worker, absoluteTarget, targetId)
                + ", worker=" + worker.position()
                + ", selected=" + worker.baritoneEngine().getEntityContext().getSelectedBlock()
                + ", xxa=" + worker.xxa
                + ", zza=" + worker.zza
                + ", job=" + worker.job()
                + ", activity=" + worker.activity()
                + ", reason=" + worker.blockReason()
                + ", managerMining=" + worker.interactionManagerMining()
                + ", breakProgress=" + worker.blockBreakingProgress()
                + ", tickets=" + worker.workerTicketCount()
                + ", searchTickets=" + worker.searchTicketCount()
                + ", pathStatus=" + worker.pathingStatus()
                + ", pathNode=" + worker.currentPathNode()
                + ", pathLength=" + worker.currentPathLength()
                + ", telemetry=" + worker.searchTelemetry()
                + ", mine=" + worker.mineProcessDiagnostic()
                + ", targetCoverage=" + SharedWorldKnowledge.get(helper.getLevel()).cachedWorld()
                        .coverage(BlockUtils.blockToString(BuiltInRegistries.BLOCK.get(targetId)),
                                new ChunkPos(absoluteTarget).toLong())
                + ", chunkNow=" + (helper.getLevel().getChunkSource().getChunkNow(
                        new ChunkPos(absoluteTarget).x, new ChunkPos(absoluteTarget).z) != null);
    }
}

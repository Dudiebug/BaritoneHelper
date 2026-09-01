package dev.dudie.baritonehelper.gametest;

import dev.dudie.baritonehelper.BaritoneHelper;
import dev.dudie.baritonehelper.BaritoneHelperDataComponents;
import dev.dudie.baritonehelper.entity.WorkerEntity;
import dev.dudie.baritonehelper.internal.baritone.cache.SharedWorldKnowledge;
import dev.dudie.baritonehelper.worker.PackedWorkerData;
import dev.dudie.baritonehelper.worker.WorkerActionResult;
import dev.dudie.baritonehelper.worker.WorkerBlockReason;
import dev.dudie.baritonehelper.worker.WorkerJob;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(BaritoneHelper.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BaritoneHelperGameTests {
    private static final BlockPos WORKER_POS = new BlockPos(1, 2, 1);
    private static final BlockPos NEAR_TARGET = new BlockPos(2, 2, 1);
    private static final int ASYNC_TIMEOUT_TICKS = 64_000;
    private static final int MAX_FAILURE_DIAGNOSTIC_LENGTH = 800;

    private BaritoneHelperGameTests() {
    }

    @GameTest(templateNamespace = "minecraft", template = "empty",
            batch = "zzzBaritoneHelperPersistence")
    public static void ownerInventoryJobCargoStorageAndExclusionsPersistWithoutTierData(
            GameTestHelper helper) {
        supportWorker(helper);
        WorkerEntity original = spawnWorker(helper);
        var owner = helper.makeMockPlayer(GameType.SURVIVAL);
        original.bindTo(owner);
        original.setItem(0, new ItemStack(Items.IRON_INGOT, 17));

        owner.setItemInHand(
                InteractionHand.MAIN_HAND,
                new ItemStack(BaritoneHelper.CARGO_UPGRADE.get()));
        original.mobInteract(owner, InteractionHand.MAIN_HAND);

        BlockPos origin = helper.absolutePos(NEAR_TARGET);
        BlockPos storage = helper.absolutePos(new BlockPos(2, 1, 2));
        ResourceLocation ironBlock = blockId(Blocks.IRON_BLOCK);
        ResourceLocation dirt = blockId(Blocks.DIRT);
        original.setWorkArea(helper.absolutePos(WORKER_POS), 8, 8);
        original.beginCollection(ironBlock, origin);
        original.assignStorage(helper.getLevel(), storage);
        original.toggleExclusion(dirt);
        original.ensureWorkerTickets();

        CompoundTag tag = new CompoundTag();
        original.addAdditionalSaveData(tag);
        tag.putString("BuddyTier", "MK3");
        tag.putInt("RescueCooldown", 1200);
        tag.put("TemporaryBlocks", new net.minecraft.nbt.ListTag());

        WorkerEntity restored =
                BaritoneHelper.BARITONE_HELPER_ENTITY.get().create(helper.getLevel());
        helper.assertTrue(restored != null, "registered helper type must create an entity");
        restored.readAdditionalSaveData(tag);

        helper.assertValueEqual(
                restored.getOwnerUUID(), owner.getUUID(), "persisted owner UUID");
        helper.assertValueEqual(
                restored.getItem(0).getCount(), 17, "persisted inventory count");
        helper.assertValueEqual(
                restored.getContainerSize(), WorkerEntity.EXPANDED_SLOTS, "cargo capacity");
        helper.assertValueEqual(restored.job(), WorkerJob.COLLECT, "persisted worker job");
        helper.assertValueEqual(
                restored.targetBlockId().orElseThrow(), ironBlock, "persisted target");
        helper.assertValueEqual(restored.jobOrigin(), origin, "persisted work origin");
        helper.assertValueEqual(
                restored.storagePosition().orElseThrow(), storage, "persisted storage");
        helper.assertTrue(restored.isExcluded(dirt), "persisted exclusion");
        helper.assertTrue(
                tag.getLongArray("WorkerTicketChunks").length > 0,
                "worker ticket coordinates must persist");

        CompoundTag rewritten = new CompoundTag();
        restored.addAdditionalSaveData(rewritten);
        helper.assertFalse(
                rewritten.contains("BuddyTier"), "legacy tier data must be ignored");
        helper.assertFalse(
                rewritten.contains("RescueCooldown"),
                "legacy rescue cooldown must be ignored");
        helper.assertFalse(
                rewritten.contains("TemporaryBlocks"),
                "legacy rescue blocks must be ignored");
        original.releaseWorkerTickets();
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void allGameplayDamageLeavesHealthUnchanged(GameTestHelper helper) {
        supportWorker(helper);
        WorkerEntity worker = spawnWorker(helper);
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 3, 2, 1);
        float health = worker.getHealth();

        worker.hurt(helper.getLevel().damageSources().generic(), 8.0F);
        worker.hurt(helper.getLevel().damageSources().mobAttack(zombie), 8.0F);
        worker.hurt(helper.getLevel().damageSources().fall(), 8.0F);
        worker.hurt(helper.getLevel().damageSources().inFire(), 8.0F);
        worker.hurt(helper.getLevel().damageSources().explosion(zombie, zombie), 8.0F);

        helper.assertValueEqual(worker.getHealth(), health, "helper health");
        helper.assertTrue(worker.isAlive(), "helper must remain alive");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void hostileMobsCannotSelectHelperAsAttackTarget(GameTestHelper helper) {
        supportWorker(helper);
        WorkerEntity worker = spawnWorker(helper);
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 3, 2, 1);

        helper.assertFalse(worker.isAttackable(), "helper must not be attackable");
        helper.assertFalse(
                worker.canBeSeenAsEnemy(), "helper must not be eligible as an enemy");
        helper.assertFalse(zombie.canAttack(worker), "hostile mob must reject helper target");
        helper.succeed();
    }

    @GameTest(
            templateNamespace = "minecraft",
            template = "empty",
            timeoutTicks = 100)
    public static void idleHelperNeitherFollowsNorChangesDimensionsWithOwner(
            GameTestHelper helper) {
        supportWorker(helper);
        WorkerEntity worker = spawnWorker(helper);
        ServerPlayer owner = helper.makeMockServerPlayerInLevel();
        worker.bindTo(owner);
        Vec3 start = worker.position();

        ServerLevel nether = Objects.requireNonNull(
                helper.getLevel().getServer().getLevel(Level.NETHER),
                "Nether level must exist during GameTests");
        owner.changeDimension(new DimensionTransition(
                nether,
                new Vec3(0.5, 80.0, 0.5),
                Vec3.ZERO,
                0.0F,
                0.0F,
                DimensionTransition.DO_NOTHING));

        helper.runAfterDelay(40, () -> {
            helper.assertValueEqual(owner.level(), nether, "owner dimension");
            helper.assertValueEqual(
                    worker.level(), helper.getLevel(), "helper dimension");
            helper.assertTrue(
                    worker.position().distanceToSqr(start) < 0.01,
                    "idle helper must stay where it was placed");
            helper.succeed();
        });
    }

    @GameTest(
            templateNamespace = "minecraft",
            template = "empty",
            batch = "zzPickupLifecycle",
            timeoutTicks = 40)
    public static void ownerDismissalPacksContentsOnceReturnsHelperAndReleasesTickets(
            GameTestHelper helper) {
        supportWorker(helper);
        WorkerEntity worker = spawnWorker(helper);
        var owner = helper.makeMockServerPlayerInLevel();
        worker.bindTo(owner);
        owner.getData(BaritoneHelper.ACTIVE_WORKER).set(
                worker.getUUID(), helper.getLevel().dimension().location().toString(), worker.blockPosition());
        worker.setItem(0, new ItemStack(Items.DIAMOND, 3));
        worker.ensureWorkerTickets();
        helper.assertValueEqual(worker.workerTicketCount(), 0,
                "idle helper must not hold chunk tickets");
        owner.setShiftKeyDown(true);
        worker.mobInteract(owner, InteractionHand.MAIN_HAND);

        helper.runAfterDelay(1, () -> {
            helper.assertFalse(worker.isAlive(), "dismissed helper must be removed");
            helper.assertValueEqual(
                    owner.getInventory().countItem(BaritoneHelper.BARITONE_HELPER.get()),
                    1,
                    "returned base Baritone Helper item");
            ItemStack packedStack = ItemStack.EMPTY;
            for (int slot = 0; slot < owner.getInventory().getContainerSize(); slot++) {
                ItemStack candidate = owner.getInventory().getItem(slot);
                if (candidate.get(BaritoneHelperDataComponents.PACKED_WORKER.get()) != null) {
                    packedStack = candidate;
                    break;
                }
            }
            PackedWorkerData packed = packedStack.get(BaritoneHelperDataComponents.PACKED_WORKER.get());
            helper.assertTrue(packed != null && packed.isPlaceable(), "returned item is committed packed worker");
            NonNullList<ItemStack> restored = NonNullList.withSize(WorkerEntity.EXPANDED_SLOTS, ItemStack.EMPTY);
            ContainerHelper.loadAllItems(packed.persistentData(), restored, helper.getLevel().registryAccess());
            helper.assertValueEqual(restored.get(0).getCount(), 3, "packed inventory diamonds");
            helper.assertValueEqual(worker.workerTicketCount(), 0, "released worker tickets");
            helper.succeed();
        });
    }

    @GameTest(
            templateNamespace = "minecraft",
            template = "empty",
            batch = "zzzBaritoneHelperOffline",
            timeoutTicks = ASYNC_TIMEOUT_TICKS)
    public static void helperCollectsConfiguredBlockWhileOwnerIsOffline(
            GameTestHelper helper) {
        supportWorker(helper);
        helper.setBlock(NEAR_TARGET, Blocks.IRON_BLOCK);
        BlockPos storagePos = new BlockPos(2, 1, 2);
        helper.setBlock(storagePos, Blocks.CHEST);
        Container storage = containerAt(helper, storagePos);
        WorkerEntity worker = spawnWorker(helper);
        var offlineOwner = helper.makeMockPlayer(GameType.SURVIVAL);
        worker.bindTo(offlineOwner);
        worker.configureTarget(
                blockId(Blocks.IRON_BLOCK), helper.absolutePos(NEAR_TARGET));
        // Keep this acceptance fixture isolated from neighboring GameTest structures.
        worker.setWorkArea(helper.absolutePos(WORKER_POS), 2, 8);
        worker.setRequestedAmount(1, false);
        worker.assignStorage(helper.getLevel(), helper.absolutePos(storagePos));
        helper.assertValueEqual(worker.startJob(), WorkerActionResult.STARTED,
                "offline collection starts");
        helper.assertTrue(worker.workerTicketCount() > 0,
                "offline active worker must retain chunk tickets");

        helper.succeedWhen(() -> {
            helper.assertTrue(
                    helper.getBlockState(NEAR_TARGET).isAir(),
                    failureDiagnostic("configured block must be collected; job=" + worker.job()
                            + ", reason=" + worker.blockReason()
                            + ", runtime=" + worker.runtimeState()
                            + ", search=" + worker.mineSearchOutcome()
                            + ", path=" + worker.pathingStatus()
                            + ", telemetry=" + worker.searchTelemetry()
                            + ", mine=" + worker.mineProcessDiagnostic()));
            helper.assertValueEqual(
                    countItem(storage, Items.IRON_BLOCK), 1, "deposited iron block");
            helper.assertValueEqual(worker.job(), WorkerJob.COMPLETED,
                    "finite offline collection completes");
            helper.assertValueEqual(worker.workerTicketCount(), 0,
                    "completed offline worker releases chunk tickets");
            helper.succeed();
        });
    }

    @GameTest(
            templateNamespace = "minecraft",
            template = "empty",
            batch = "zzzBaritoneHelperFiniteGoal",
            timeoutTicks = ASYNC_TIMEOUT_TICKS)
    public static void finiteGoalCompletesAfterRealDropAndDeposit(GameTestHelper helper) {
        supportWorker(helper);
        helper.setBlock(NEAR_TARGET, Blocks.GOLD_BLOCK);
        BlockPos storagePos = new BlockPos(2, 1, 2);
        helper.setBlock(storagePos, Blocks.CHEST);
        Container storage = containerAt(helper, storagePos);
        WorkerEntity worker = spawnWorker(helper);
        worker.configureTarget(blockId(Blocks.GOLD_BLOCK), helper.absolutePos(NEAR_TARGET));
        // Keep this fixture isolated from neighboring GameTest structures.
        worker.setWorkArea(helper.absolutePos(WORKER_POS), 2, 8);
        worker.setRequestedAmount(1, false);
        worker.assignStorage(helper.getLevel(), helper.absolutePos(storagePos));
        worker.startJob();

        helper.succeedWhen(() -> {
            helper.assertTrue(helper.getBlockState(NEAR_TARGET).isAir(),
                    failureDiagnostic("finite goal must break the source block; job=" + worker.job()
                            + ", reason=" + worker.blockReason()
                            + ", runtime=" + worker.runtimeState()
                            + ", search=" + worker.mineSearchOutcome()
                            + ", path=" + worker.pathingStatus()
                            + ", telemetry=" + worker.searchTelemetry()
                            + ", mine=" + worker.mineProcessDiagnostic()));
            helper.assertValueEqual(worker.completedBlockCount(), 1,
                    "finite goal source-block count");
            helper.assertValueEqual(worker.job(), WorkerJob.COMPLETED,
                    "finite goal must finish after deposit");
            helper.assertValueEqual(countItem(storage, Items.GOLD_BLOCK), 1,
                    "real block drop must reach storage");
            helper.succeed();
        });
    }

    @GameTest(
            templateNamespace = "minecraft",
            template = "empty",
            batch = "zzzBaritoneHelperMultiDrop",
            timeoutTicks = ASYNC_TIMEOUT_TICKS)
    public static void actualMultiDropLootIsCollectedWithoutCollapsingTheStack(
            GameTestHelper helper) {
        supportWorker(helper);
        helper.setBlock(NEAR_TARGET, Blocks.CLAY);
        BlockPos storagePos = new BlockPos(2, 1, 2);
        helper.setBlock(storagePos, Blocks.CHEST);
        Container storage = containerAt(helper, storagePos);
        WorkerEntity worker = spawnWorker(helper);
        worker.configureTarget(blockId(Blocks.CLAY), helper.absolutePos(NEAR_TARGET));
        worker.setWorkArea(helper.absolutePos(WORKER_POS), 2, 8);
        worker.setRequestedAmount(1, false);
        worker.assignStorage(helper.getLevel(), helper.absolutePos(storagePos));
        helper.assertValueEqual(worker.startJob(), WorkerActionResult.STARTED,
                "multi-drop collection starts");

        helper.succeedWhen(() -> {
            helper.assertTrue(helper.getBlockState(NEAR_TARGET).isAir(),
                    failureDiagnostic("clay source must be broken; "
                            + worker.mineProcessDiagnostic()));
            helper.assertValueEqual(worker.completedBlockCount(), 1,
                    "requested quantity counts source blocks, not loot stacks");
            helper.assertValueEqual(countItem(storage, Items.CLAY_BALL), 4,
                    "vanilla clay loot table must deliver all four drops");
            helper.assertValueEqual(worker.job(), WorkerJob.COMPLETED,
                    "multi-drop finite job completes after deposit");
            helper.succeed();
        });
    }

    @GameTest(
            templateNamespace = "minecraft",
            template = "empty",
            batch = "zzzBaritoneHelperDeposit",
            timeoutTicks = 40)
    public static void helperDepositsInventoryIntoAssignedStorage(GameTestHelper helper) {
        supportWorker(helper);
        BlockPos storagePos = new BlockPos(2, 1, 1);
        helper.setBlock(storagePos, Blocks.CHEST);
        Container storage = containerAt(helper, storagePos);
        WorkerEntity worker = spawnWorker(helper);
        worker.setWorkArea(helper.absolutePos(WORKER_POS), 8, 8);
        worker.beginCollection(
                blockId(Blocks.IRON_BLOCK), helper.absolutePos(NEAR_TARGET));
        worker.assignStorage(helper.getLevel(), helper.absolutePos(storagePos));
        worker.setItem(0, new ItemStack(Items.DIAMOND, 5));
        worker.requestDepositOrBlock();

        helper.runAfterDelay(5, () -> {
            helper.assertTrue(worker.isEmpty(), "helper inventory must be emptied");
            helper.assertValueEqual(
                    countItem(storage, Items.DIAMOND), 5, "stored diamonds");
            helper.assertValueEqual(
                    worker.job(), WorkerJob.COLLECT, "resumed collection job");
            helper.succeed();
        });
    }

    @GameTest(
            templateNamespace = "minecraft",
            template = "empty",
            batch = "zzzBaritoneHelperFullStorage",
            timeoutTicks = 40)
    public static void fullStorageBlocksJobWithoutDeletingCargo(GameTestHelper helper) {
        supportWorker(helper);
        BlockPos storagePos = new BlockPos(2, 1, 1);
        helper.setBlock(storagePos, Blocks.CHEST);
        Container storage = containerAt(helper, storagePos);
        for (int slot = 0; slot < storage.getContainerSize(); slot++) {
            storage.setItem(slot, new ItemStack(Items.STONE, 64));
        }

        WorkerEntity worker = spawnWorker(helper);
        worker.setWorkArea(helper.absolutePos(WORKER_POS), 8, 8);
        worker.beginCollection(
                blockId(Blocks.IRON_BLOCK), helper.absolutePos(NEAR_TARGET));
        worker.assignStorage(helper.getLevel(), helper.absolutePos(storagePos));
        worker.setItem(0, new ItemStack(Items.DIAMOND, 5));
        worker.requestDepositOrBlock();

        helper.runAfterDelay(5, () -> {
            helper.assertValueEqual(worker.job(), WorkerJob.BLOCKED, "blocked job");
            helper.assertValueEqual(
                    countItem(worker, Items.DIAMOND), 5, "retained worker diamonds");
            helper.assertValueEqual(
                    countItem(storage, Items.DIAMOND), 0, "storage diamonds");
            helper.succeed();
        });
    }

    @GameTest(
            templateNamespace = "minecraft",
            template = "empty",
            batch = "zzzBaritoneHelperFullInventory",
            timeoutTicks = 40)
    public static void fullWorkerInventoryPreservesTargetBlock(GameTestHelper helper) {
        supportWorker(helper);
        helper.setBlock(NEAR_TARGET, Blocks.IRON_BLOCK);
        WorkerEntity worker = spawnWorker(helper);
        for (int slot = 0; slot < worker.getContainerSize(); slot++) {
            worker.setItem(slot, new ItemStack(Items.DIRT, 64));
        }
        worker.setWorkArea(helper.absolutePos(WORKER_POS), 8, 8);
        worker.beginCollection(
                blockId(Blocks.IRON_BLOCK), helper.absolutePos(NEAR_TARGET));

        helper.runAfterDelay(10, () -> {
            helper.assertValueEqual(worker.job(), WorkerJob.BLOCKED, "blocked job");
            helper.assertTrue(
                    helper.getBlockState(NEAR_TARGET).is(Blocks.IRON_BLOCK),
                    "target must remain when drops cannot fit");
            helper.assertValueEqual(
                    countItem(worker, Items.DIRT),
                    WorkerEntity.BASE_SLOTS * 64,
                    "conserved full inventory");
            helper.succeed();
        });
    }

    @GameTest(
            templateNamespace = "minecraft",
            template = "empty",
            batch = "zzzBaritoneHelperExcluded",
            timeoutTicks = 40)
    public static void excludedBlockTypeIsNotCollected(GameTestHelper helper) {
        supportWorker(helper);
        helper.setBlock(NEAR_TARGET, Blocks.IRON_BLOCK);
        WorkerEntity worker = spawnWorker(helper);
        ResourceLocation target = blockId(Blocks.IRON_BLOCK);
        worker.setWorkArea(helper.absolutePos(WORKER_POS), 8, 8);
        worker.beginCollection(target, helper.absolutePos(NEAR_TARGET));
        worker.toggleExclusion(target);

        helper.runAfterDelay(20, () -> {
            helper.assertValueEqual(worker.job(), WorkerJob.IDLE, "idle excluded job");
            helper.assertTrue(
                    helper.getBlockState(NEAR_TARGET).is(Blocks.IRON_BLOCK),
                    "excluded target must remain");
            helper.assertValueEqual(
                    countItem(worker, Items.IRON_BLOCK), 0, "excluded collection count");
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void cargoUpgradeExpandsCapacityExactlyOnce(GameTestHelper helper) {
        supportWorker(helper);
        WorkerEntity worker = spawnWorker(helper);
        var owner = helper.makeMockPlayer(GameType.SURVIVAL);
        worker.bindTo(owner);

        ItemStack first = new ItemStack(BaritoneHelper.CARGO_UPGRADE.get());
        owner.setItemInHand(InteractionHand.MAIN_HAND, first);
        worker.mobInteract(owner, InteractionHand.MAIN_HAND);
        helper.assertTrue(first.isEmpty(), "first cargo upgrade must be consumed");
        helper.assertValueEqual(
                worker.getContainerSize(), WorkerEntity.EXPANDED_SLOTS, "expanded slots");

        ItemStack second = new ItemStack(BaritoneHelper.CARGO_UPGRADE.get());
        owner.setItemInHand(InteractionHand.MAIN_HAND, second);
        worker.mobInteract(owner, InteractionHand.MAIN_HAND);
        helper.assertValueEqual(second.getCount(), 1, "second upgrade must be retained");
        helper.assertValueEqual(worker.cargoUpgrades(), 1, "cargo upgrade count");
        helper.succeed();
    }

    @GameTest(
            templateNamespace = "minecraft",
            template = "empty",
            batch = "zzzBaritoneHelperNavigation",
            timeoutTicks = ASYNC_TIMEOUT_TICKS)
    public static void helperNavigatesToReachableTarget(GameTestHelper helper) {
        for (int x = 1; x <= 4; x++) {
            helper.setBlock(x, 1, 1, Blocks.STONE);
        }
        BlockPos target = new BlockPos(4, 2, 1);
        helper.setBlock(target, Blocks.RAW_GOLD_BLOCK);
        WorkerEntity worker = spawnWorker(helper);
        Vec3 start = worker.position();
        worker.setWorkArea(helper.absolutePos(WORKER_POS), 8, 8);
        worker.beginCollection(
                blockId(Blocks.RAW_GOLD_BLOCK), helper.absolutePos(target));

        helper.succeedWhen(() -> {
            String diagnostic = "reachable target must be collected; "
                    + "position=" + worker.position()
                    + ", startDistance=" + worker.position().distanceToSqr(start)
                    + ", job=" + worker.job()
                    + ", activity=" + worker.activity()
                    + ", reason=" + worker.blockReason()
                    + ", target=" + worker.currentTarget()
                    + ", workPosition=" + worker.currentWorkPosition()
                    + ", navigationDone=" + worker.getNavigation().isDone()
                    + ", replans=" + worker.replanAttempts()
                    + ", noProgressTicks=" + worker.lastProgressAgeTicks();
            helper.assertTrue(helper.getBlockState(target).isAir(), diagnostic);
            helper.assertTrue(
                    worker.position().distanceToSqr(start) > 0.25,
                    "helper must navigate toward a distant target");
            helper.assertValueEqual(
                    countItem(worker, Blocks.RAW_GOLD_BLOCK.asItem()), 1, "traversal collection");
            helper.succeed();
        });
    }

    @GameTest(
            templateNamespace = "minecraft",
            template = "empty",
            batch = "zzzBaritoneHelperUnreachable",
            timeoutTicks = ASYNC_TIMEOUT_TICKS)
    public static void unreachableTargetRemainsAfterNavigationWatchdog(
            GameTestHelper helper) {
        supportWorker(helper);
        BlockPos target = new BlockPos(3, 2, 3);
        for (int x = 2; x <= 4; x++) {
            for (int y = 1; y <= 3; y++) {
                for (int z = 2; z <= 4; z++) {
                    boolean shell = x == 2 || x == 4
                            || y == 1 || y == 3
                            || z == 2 || z == 4;
                    if (shell) {
                        helper.setBlock(x, y, z, Blocks.BEDROCK);
                    }
                }
            }
        }
        helper.setBlock(target, Blocks.EMERALD_BLOCK);
        WorkerEntity worker = spawnWorker(helper);
        worker.setWorkArea(helper.absolutePos(WORKER_POS), 8, 8);
        worker.beginCollection(
                blockId(Blocks.EMERALD_BLOCK), helper.absolutePos(target));

        helper.succeedWhen(() -> {
            helper.assertTrue(
                    helper.getBlockState(target).is(Blocks.EMERALD_BLOCK),
                    "unreachable target must not be mined through walls");
            helper.assertValueEqual(
                    countItem(worker, Items.EMERALD_BLOCK), 0, "watchdog collection count");
            helper.assertValueEqual(worker.job(), WorkerJob.BLOCKED,
                    failureDiagnostic("exhausted unreachable area must block the job; "
                            + worker.mineProcessDiagnostic()));
            helper.assertValueEqual(worker.blockReason(), WorkerBlockReason.SEARCH_AREA_UNREACHABLE,
                    "unreachable coverage must report its distinct terminal reason");
        });
    }

    private static WorkerEntity spawnWorker(GameTestHelper helper) {
        BlockPos absolute = helper.absolutePos(WORKER_POS);
        int minChunkX = Math.floorDiv(absolute.getX() - 8, 16);
        int maxChunkX = Math.floorDiv(absolute.getX() + 8, 16);
        int minChunkZ = Math.floorDiv(absolute.getZ() - 8, 16);
        int maxChunkZ = Math.floorDiv(absolute.getZ() + 8, 16);
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                SharedWorldKnowledge.get(helper.getLevel()).cachedWorld()
                        .markDirty(ChunkPos.asLong(chunkX, chunkZ));
            }
        }
        return helper.spawn(
                BaritoneHelper.BARITONE_HELPER_ENTITY.get(),
                WORKER_POS.getX(),
                WORKER_POS.getY(),
                WORKER_POS.getZ());
    }

    private static void supportWorker(GameTestHelper helper) {
        helper.setBlock(1, 1, 1, Blocks.STONE);
    }

    private static ResourceLocation blockId(Block block) {
        return BuiltInRegistries.BLOCK.getKey(block);
    }

    private static Container containerAt(GameTestHelper helper, BlockPos relativePosition) {
        BlockEntity blockEntity = helper.getLevel().getBlockEntity(
                helper.absolutePos(relativePosition));
        helper.assertTrue(blockEntity instanceof Container, "expected container block entity");
        return (Container) blockEntity;
    }

    private static int countItem(Container container, Item item) {
        int count = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static String failureDiagnostic(String value) {
        if (value.length() <= MAX_FAILURE_DIAGNOSTIC_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_FAILURE_DIAGNOSTIC_LENGTH - 3) + "...";
    }
}

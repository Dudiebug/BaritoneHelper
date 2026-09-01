package dev.dudie.baritonehelper.gametest;

import dev.dudie.baritonehelper.ActiveWorkerData;
import dev.dudie.baritonehelper.BaritoneHelper;
import dev.dudie.baritonehelper.BaritoneHelperDataComponents;
import dev.dudie.baritonehelper.entity.WorkerEntity;
import dev.dudie.baritonehelper.internal.baritone.cache.CoverageState;
import dev.dudie.baritonehelper.internal.baritone.cache.SharedWorldKnowledge;
import dev.dudie.baritonehelper.item.WorkerItem;
import dev.dudie.baritonehelper.network.WorkerActionAcknowledgementS2C;
import dev.dudie.baritonehelper.network.WorkerDashboardActionC2S;
import dev.dudie.baritonehelper.worker.PackedWorkerData;
import dev.dudie.baritonehelper.worker.PickupState;
import dev.dudie.baritonehelper.worker.WorkerActionResult;
import dev.dudie.baritonehelper.worker.WorkerPickupService;
import io.netty.buffer.Unpooled;
import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Runtime-only release checks; ordinary JUnit stays free of game dependencies. */
@GameTestHolder(BaritoneHelper.MOD_ID)
@PrefixGameTestTemplate(false)
public final class Release32VerificationGameTests {
    private Release32VerificationGameTests() {
    }

    @GameTest(templateNamespace = "minecraft", template = "empty", timeoutTicks = 40)
    public static void ledgerSavedDataRoundTripsTargetIsolationAndInterruptedScan(
            GameTestHelper helper) {
        SharedWorldKnowledge original = new SharedWorldKnowledge();
        String target = "release32:ledger/" + UUID.randomUUID();
        String otherTarget = target + "/other";
        long scannedChunk = new ChunkPos(helper.absolutePos(new BlockPos(1, 1, 1))).toLong();
        long interruptedChunk = scannedChunk + 1L;
        original.ledger().beginScan(target, scannedChunk);
        original.ledger().publish(target, scannedChunk, List.of(101L, 101L, 202L));
        original.ledger().beginScan(target, interruptedChunk);
        original.ledger().beginScan(otherTarget, scannedChunk);
        original.ledger().publish(otherTarget, scannedChunk, List.of(303L));
        CompoundTag saved = original.save(new CompoundTag(), helper.getLevel().registryAccess());

        SharedWorldKnowledge restored = load(saved);
        helper.assertValueEqual(restored.ledger().state(target, scannedChunk), CoverageState.SCANNED,
                "saved scanned state");
        helper.assertValueEqual(restored.ledger().locations(target, scannedChunk),
                java.util.Set.of(101L, 202L), "saved deduplicated locations");
        helper.assertValueEqual(restored.ledger().state(target, interruptedChunk), CoverageState.DIRTY,
                "interrupted scans must reload dirty");
        helper.assertValueEqual(restored.ledger().locations(target, interruptedChunk),
                java.util.Set.of(), "interrupted locations must not be trusted");
        helper.assertValueEqual(restored.ledger().locations(otherTarget, scannedChunk),
                java.util.Set.of(303L), "target observations remain isolated");
        helper.assertTrue(restored.ledger().locations(target, scannedChunk).stream()
                .noneMatch(location -> location == 303L),
                "one target cannot inherit another target's locations");
        CompoundTag unsupported = saved.copy();
        unsupported.putInt("Schema", 99);
        helper.assertTrue(load(unsupported).ledger().snapshot().isEmpty(),
                "unsupported ledger schema must fail closed");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "empty", timeoutTicks = 40)
    public static void requestCodecPreservesCorrelationAndFingerprintFields(GameTestHelper helper) {
        WorkerDashboardActionC2S request = new WorkerDashboardActionC2S(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "minecraft:overworld",
                17,
                WorkerDashboardActionC2S.Action.SET_WORK_AREA,
                "",
                64,
                true,
                new BlockPos(4, 70, -8),
                128,
                32);
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(
                Unpooled.buffer(), RegistryAccess.EMPTY);
        WorkerDashboardActionC2S.STREAM_CODEC.encode(buffer, request);
        WorkerDashboardActionC2S decoded = WorkerDashboardActionC2S.STREAM_CODEC.decode(buffer);
        helper.assertValueEqual(decoded.requestId(), request.requestId(), "request correlation UUID");
        helper.assertValueEqual(decoded.workerUuid(), request.workerUuid(), "worker UUID");
        helper.assertValueEqual(decoded.dimension(), request.dimension(), "request dimension");
        helper.assertValueEqual(decoded.fingerprint(), request.fingerprint(), "request fingerprint");
        WorkerActionAcknowledgementS2C acknowledgement = new WorkerActionAcknowledgementS2C(
                request.requestId(), true, "ok", "message.ok", request.expectedRevision());
        helper.assertValueEqual(acknowledgement.requestId(), request.requestId(),
                "acknowledgement correlation UUID");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "empty", timeoutTicks = 40)
    public static void packedPayloadRoundTripsAndExcludesRuntimeTickets(GameTestHelper helper) {
        CompoundTag entityData = new CompoundTag();
        entityData.putString("WorkerJob", "COLLECT");
        entityData.putLongArray("WorkerTicketChunks", new long[] {1L, 2L});
        entityData.putLongArray("WorkerSimulationTicketChunks", new long[] {3L});
        entityData.putLongArray("SearchTicketChunks", new long[] {4L});
        UUID workerUuid = UUID.randomUUID();
        UUID ownerUuid = UUID.randomUUID();
        UUID transactionUuid = UUID.randomUUID();
        PackedWorkerData live = new PackedWorkerData(
                PackedWorkerData.CURRENT_SCHEMA,
                workerUuid,
                ownerUuid,
                transactionUuid,
                entityData,
                PickupState.LIVE);
        CompoundTag persistent = live.persistentData();
        helper.assertFalse(live.isPlaceable(), "live payload must not be placeable");
        helper.assertValueEqual(persistent.getUUID("Owner"), ownerUuid, "packed owner identity");
        helper.assertValueEqual(persistent.getString("WorkerJob"), "READY", "packed job reset");
        helper.assertFalse(persistent.contains("WorkerTicketChunks"), "view tickets are transient");
        helper.assertFalse(persistent.contains("WorkerSimulationTicketChunks"),
                "simulation tickets are transient");
        helper.assertFalse(persistent.contains("SearchTicketChunks"), "search tickets are transient");
        PackedWorkerData pending = live.withPickupState(PickupState.PENDING);
        helper.assertFalse(pending.isPlaceable(), "pending payload must not be placeable");
        PackedWorkerData committed = pending.withPickupState(PickupState.COMMITTED);
        helper.assertTrue(committed.isPlaceable(), "committed payload must be placeable");
        var encoded = PackedWorkerData.CODEC.encodeStart(NbtOps.INSTANCE, committed)
                .result().orElseThrow();
        PackedWorkerData decoded = PackedWorkerData.CODEC.parse(NbtOps.INSTANCE, encoded)
                .result().orElseThrow();
        helper.assertValueEqual(decoded.transactionUuid(), transactionUuid,
                "packed transaction UUID round trip");
        helper.assertValueEqual(decoded, committed, "packed payload NBT round trip");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "empty", timeoutTicks = 40)
    public static void pendingPickupSnapshotRoundTripsAndRestoresLiveWorker(GameTestHelper helper) {
        WorkerEntity worker = helper.spawn(
                BaritoneHelper.BARITONE_HELPER_ENTITY.get(), 1, 2, 1);
        ServerPlayer owner = helper.makeMockServerPlayerInLevel();
        worker.bindTo(owner);
        ActiveWorkerData active = owner.getData(BaritoneHelper.ACTIVE_WORKER);
        active.set(worker.getUUID(), worker.level().dimension().location().toString(), worker.blockPosition());
        UUID transaction = UUID.randomUUID();
        helper.assertTrue(active.beginPickup(worker.getUUID(), transaction), "pending transaction starts");
        worker.freezeForPickup();
        ItemStack packed = WorkerItem.createPackedStack(worker, transaction);
        helper.assertTrue(active.attachPickupSnapshot(
                worker.getUUID(), transaction,
                packed.get(BaritoneHelperDataComponents.PACKED_WORKER.get())),
                "immutable pickup snapshot attached");

        CompoundTag saved = active.serializeNBT(helper.getLevel().registryAccess());
        ActiveWorkerData restored = new ActiveWorkerData();
        restored.deserializeNBT(helper.getLevel().registryAccess(), saved);
        owner.setData(BaritoneHelper.ACTIVE_WORKER, restored);
        helper.assertTrue(restored.pickupSnapshot().isPresent(), "pending snapshot survives restart");
        ItemEntity nearbyDrop = new ItemEntity(
                helper.getLevel(), worker.getX(), worker.getY(), worker.getZ(),
                new ItemStack(Items.DIAMOND));
        helper.getLevel().addFreshEntity(nearbyDrop);
        helper.runAfterDelay(2, () -> {
            helper.assertFalse(nearbyDrop.isRemoved(), "frozen pickup cannot consume later drops");
            helper.assertValueEqual(worker.workerTicketCount(), 0, "frozen pickup holds no view tickets");
            helper.assertFalse(WorkerPickupService.reconcile(owner, worker),
                    "undelivered pending transaction restores the live worker");
            helper.assertValueEqual(restored.pickupState(), PickupState.LIVE,
                    "live pickup state restored");
            helper.assertFalse(worker.isNoAi(), "worker resumes after interrupted pickup");
            helper.assertValueEqual(countPackedTransaction(owner, transaction), 0,
                    "rollback does not invent a packed item");
            nearbyDrop.discard();
            worker.discard();
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = "minecraft", template = "empty", timeoutTicks = 40)
    public static void missingCommittedWorkerRedeliversSnapshotExactlyOnce(GameTestHelper helper) {
        WorkerEntity worker = helper.spawn(
                BaritoneHelper.BARITONE_HELPER_ENTITY.get(), 1, 2, 1);
        ServerPlayer owner = helper.makeMockServerPlayerInLevel();
        worker.bindTo(owner);
        ActiveWorkerData active = owner.getData(BaritoneHelper.ACTIVE_WORKER);
        active.set(worker.getUUID(), worker.level().dimension().location().toString(), worker.blockPosition());
        UUID workerUuid = worker.getUUID();
        UUID transaction = UUID.randomUUID();
        active.beginPickup(workerUuid, transaction);
        worker.freezeForPickup();
        ItemStack packed = WorkerItem.createPackedStack(worker, transaction);
        active.attachPickupSnapshot(workerUuid, transaction,
                packed.get(BaritoneHelperDataComponents.PACKED_WORKER.get()));
        active.commitPickup(workerUuid, transaction);
        worker.commitPickupRemoval();

        CompoundTag saved = active.serializeNBT(helper.getLevel().registryAccess());
        ActiveWorkerData restored = new ActiveWorkerData();
        restored.deserializeNBT(helper.getLevel().registryAccess(), saved);
        owner.setData(BaritoneHelper.ACTIVE_WORKER, restored);
        helper.assertTrue(WorkerPickupService.reconcile(owner, null),
                "missing committed worker is recovered from the durable snapshot");
        helper.assertTrue(restored.uuid().isEmpty(), "committed owner record clears after delivery");
        helper.assertValueEqual(countPackedTransaction(owner, transaction), 1,
                "restart recovery delivers one packed item");
        helper.assertFalse(WorkerPickupService.reconcile(owner, null),
                "replaying reconciliation is idempotent");
        helper.assertValueEqual(countPackedTransaction(owner, transaction), 1,
                "replay cannot duplicate the packed item");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "empty", timeoutTicks = 40)
    public static void deliveredPendingPickupCommitsWithoutDuplicate(GameTestHelper helper) {
        WorkerEntity worker = helper.spawn(
                BaritoneHelper.BARITONE_HELPER_ENTITY.get(), 1, 2, 1);
        ServerPlayer owner = helper.makeMockServerPlayerInLevel();
        worker.bindTo(owner);
        ActiveWorkerData active = owner.getData(BaritoneHelper.ACTIVE_WORKER);
        active.set(worker.getUUID(), worker.level().dimension().location().toString(), worker.blockPosition());
        UUID workerUuid = worker.getUUID();
        UUID transaction = UUID.randomUUID();
        active.beginPickup(workerUuid, transaction);
        worker.freezeForPickup();
        ItemStack packed = WorkerItem.createPackedStack(worker, transaction);
        active.attachPickupSnapshot(workerUuid, transaction,
                packed.get(BaritoneHelperDataComponents.PACKED_WORKER.get()));
        owner.getInventory().add(packed.copy());

        helper.assertValueEqual(WorkerPickupService.pickup(owner, worker, transaction),
                WorkerPickupService.Result.COMMITTED,
                "replayed request commits the existing delivery");
        helper.assertTrue(worker.isRemoved(), "live worker is removed after committed delivery");
        helper.assertTrue(active.uuid().isEmpty(), "owner record clears after committed delivery");
        helper.assertValueEqual(countPackedTransaction(owner, transaction), 1,
                "existing delivery is reused instead of duplicated");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "empty", timeoutTicks = 40)
    public static void activeTicketsUseSixChunkViewAndTwoChunkSimulationFootprints(
            GameTestHelper helper) {
        helper.setBlock(1, 1, 1, Blocks.STONE);
        WorkerEntity worker = helper.spawn(
                BaritoneHelper.BARITONE_HELPER_ENTITY.get(), 1, 2, 1);
        worker.configureTarget(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(Blocks.DIRT),
                worker.blockPosition());
        helper.assertValueEqual(worker.startJob(), WorkerActionResult.STARTED, "job start");
        helper.assertValueEqual(worker.workerTicketCount(), 169, "active view footprint");
        helper.assertValueEqual(worker.simulationTicketCount(), 25, "simulation footprint");
        worker.stopJob();
        helper.assertValueEqual(worker.workerTicketCount(), 0, "stop releases view tickets");
        helper.assertValueEqual(worker.simulationTicketCount(), 0, "stop releases simulation tickets");
        worker.remove(Entity.RemovalReason.DISCARDED);
        helper.assertValueEqual(worker.totalTicketCount(), 0, "removal leaves no tickets");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "empty", timeoutTicks = 60)
    public static void ownerIdentitySurvivesDimensionChangeButInventoryDoesNot(
            GameTestHelper helper) {
        helper.setBlock(1, 1, 1, Blocks.STONE);
        WorkerEntity worker = helper.spawn(
                BaritoneHelper.BARITONE_HELPER_ENTITY.get(), 1, 2, 1);
        ServerPlayer owner = helper.makeMockServerPlayerInLevel();
        ServerPlayer nonOwner = helper.makeMockServerPlayerInLevel();
        worker.bindTo(owner);
        helper.assertTrue(worker.isOwnedByPlayer(owner), "owner UUID identity");
        helper.assertFalse(worker.isOwnedByPlayer(nonOwner), "non-owner UUID rejected");
        helper.assertTrue(worker.createMenu(1, owner.getInventory(), owner) != null,
                "same-dimension owner can open inventory");
        helper.assertTrue(worker.createMenu(2, nonOwner.getInventory(), nonOwner) == null,
                "non-owner cannot open inventory");

        ServerLevel nether = java.util.Objects.requireNonNull(
                helper.getLevel().getServer().getLevel(net.minecraft.world.level.Level.NETHER),
                "Nether level");
        owner.changeDimension(new DimensionTransition(
                nether, new Vec3(0.5, 80.0, 0.5), Vec3.ZERO, 0.0F, 0.0F,
                DimensionTransition.DO_NOTHING));
        helper.runAfterDelay(1, () -> {
            helper.assertTrue(worker.isOwnedByPlayer(owner),
                    "ownership remains UUID-based after dimension change");
            helper.assertTrue(worker.createMenu(3, owner.getInventory(), owner) == null,
                    "cross-dimension owner cannot open inventory");
            helper.succeed();
        });
    }

    private static SharedWorldKnowledge load(CompoundTag tag) {
        try {
            Method loader = SharedWorldKnowledge.class.getDeclaredMethod(
                    "load", CompoundTag.class, net.minecraft.core.HolderLookup.Provider.class);
            loader.setAccessible(true);
            return (SharedWorldKnowledge) loader.invoke(null, tag, null);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("SavedData load hook unavailable", exception);
        }
    }

    private static int countPackedTransaction(ServerPlayer owner, UUID transaction) {
        int count = 0;
        for (int slot = 0; slot < owner.getInventory().getContainerSize(); slot++) {
            PackedWorkerData packed = owner.getInventory().getItem(slot)
                    .get(BaritoneHelperDataComponents.PACKED_WORKER.get());
            if (packed != null && transaction.equals(packed.transactionUuid())) count++;
        }
        return count;
    }
}

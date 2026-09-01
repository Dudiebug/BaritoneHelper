package dev.dudie.baritonehelper.network;

import dev.dudie.baritonehelper.BaritoneHelper;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(BaritoneHelper.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RequestReceiptDataGameTests {
    private RequestReceiptDataGameTests() {
    }

    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void receiptsRoundTripRejectConflictsAndStayBounded(GameTestHelper helper) {
        RequestReceiptData data = new RequestReceiptData();
        UUID playerId = UUID.randomUUID();
        UUID workerId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        WorkerDashboardActionC2S request = request(workerId, requestId, 64);
        WorkerActionAcknowledgementS2C acknowledgement = new WorkerActionAcknowledgementS2C(
                requestId, true, "ok", "message.baritonehelper.action_applied", 7);
        data.record(playerId, request, acknowledgement);

        CompoundTag saved = data.save(new CompoundTag(), helper.getLevel().registryAccess());
        RequestReceiptData restored = RequestReceiptData.load(
                saved, helper.getLevel().registryAccess());
        RequestReceiptData.Lookup replay = restored.lookup(playerId, request);
        helper.assertTrue(replay != null && !replay.conflicting(),
                "identical request must replay after SavedData round trip");
        helper.assertValueEqual(replay.acknowledgement(), acknowledgement,
                "persisted acknowledgement");

        RequestReceiptData.Lookup conflict = restored.lookup(
                playerId, request(workerId, requestId, 65));
        helper.assertTrue(conflict != null && conflict.conflicting(),
                "same request UUID with another fingerprint must conflict");

        for (int index = 0; index <= RequestReceiptData.MAX_RECEIPTS; index++) {
            UUID boundedRequestId = UUID.randomUUID();
            WorkerDashboardActionC2S boundedRequest = request(workerId, boundedRequestId, index + 1);
            restored.record(playerId, boundedRequest, new WorkerActionAcknowledgementS2C(
                    boundedRequestId, true, "ok", "message.baritonehelper.action_applied", index));
        }
        helper.assertValueEqual(restored.sizeForTesting(), RequestReceiptData.MAX_RECEIPTS,
                "bounded persisted receipt count");

        CompoundTag unknownSchema = saved.copy();
        unknownSchema.putInt("Schema", Integer.MAX_VALUE);
        helper.assertValueEqual(RequestReceiptData.load(
                unknownSchema, helper.getLevel().registryAccess()).sizeForTesting(), 0,
                "unknown receipt schema rebuilds safely");
        helper.succeed();
    }

    private static WorkerDashboardActionC2S request(
            UUID workerId, UUID requestId, int amount) {
        return new WorkerDashboardActionC2S(
                requestId,
                workerId,
                "minecraft:overworld",
                4,
                WorkerDashboardActionC2S.Action.SET_AMOUNT,
                "",
                amount,
                false,
                BlockPos.ZERO,
                64,
                32);
    }
}

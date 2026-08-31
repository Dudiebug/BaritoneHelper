package dev.dudie.baritonehelper.gametest;

import dev.dudie.baritonehelper.BaritoneHelper;
import dev.dudie.baritonehelper.entity.WorkerEntity;
import dev.dudie.baritonehelper.internal.baritone.Baritone;
import dev.dudie.baritonehelper.internal.baritone.api.utils.IInputOverrideHandler;
import dev.dudie.baritonehelper.internal.baritone.api.utils.input.Input;
import dev.dudie.baritonehelper.internal.baritone.utils.InputOverrideHandler;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(BaritoneHelper.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MovementParityGameTests {
    private MovementParityGameTests() {
    }

    @GameTest(templateNamespace = "minecraft", template = "empty", timeoutTicks = 40)
    public static void analogueInputPreservesDirectionsSprintStateAndStop(
            GameTestHelper helper) {
        helper.setBlock(1, 1, 1, Blocks.STONE);
        WorkerEntity worker = helper.spawn(
                BaritoneHelper.BARITONE_HELPER_ENTITY.get(), 1, 2, 1);
        helper.assertTrue(Math.abs(worker.getSpeed() - 0.1F) < 1.0E-5F,
                "worker movement speed must remain player-equivalent at 0.1");
        Baritone engine = worker.baritoneEngine();
        IInputOverrideHandler input = engine.getInputOverrideHandler();

        input.setInputForceState(Input.MOVE_FORWARD, true);
        input.setInputForceState(Input.MOVE_RIGHT, true);
        input.setInputForceState(Input.SPRINT, true);
        ((InputOverrideHandler) input).onTickServer();

        helper.assertTrue(Math.abs(worker.zza - 1.0F) < 1.0E-5F,
                "forward input must publish +1.0 analogue movement");
        helper.assertTrue(Math.abs(worker.xxa + 1.0F) < 1.0E-5F,
                "right input must publish -1.0 analogue movement");
        helper.assertTrue(input.isInputForcedDown(Input.SPRINT),
                "sprint input must remain owned until the path executor consumes it");

        input.clearAllKeys();
        input.setInputForceState(Input.MOVE_BACK, true);
        ((InputOverrideHandler) input).onTickServer();
        helper.assertTrue(Math.abs(worker.zza + 1.0F) < 1.0E-5F,
                "backward input must publish -1.0 analogue movement");

        input.clearAllKeys();
        ((InputOverrideHandler) input).onTickServer();
        helper.assertTrue(Math.abs(worker.xxa) < 1.0E-5F && Math.abs(worker.zza) < 1.0E-5F,
                "clearing inputs must publish an immediate stop");
        helper.assertFalse(worker.isSprinting(),
                "clearing inputs must stop sprinting");

        worker.disposeBaritoneEngine();
        helper.succeed();
    }
}

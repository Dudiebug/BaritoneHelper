package dev.dudie.baritonehelper.client;

import dev.dudie.baritonehelper.network.OpenWorkerDashboardS2C;
import dev.dudie.baritonehelper.network.WorkerActionAcknowledgementS2C;
import dev.dudie.baritonehelper.network.WorkerDashboardStateS2C;
import net.minecraft.client.Minecraft;

/** Client entry points for the common payload handlers. */
public final class WorkerDashboardClient {
    private WorkerDashboardClient() {}

    public static void open(OpenWorkerDashboardS2C payload) {
        Minecraft.getInstance().setScreen(new WorkerDashboardScreen(payload.snapshot()));
    }

    public static void state(WorkerDashboardStateS2C payload) {
        if (Minecraft.getInstance().screen instanceof WorkerDashboardScreen screen) {
            screen.setSnapshot(payload.snapshot());
        }
    }

    public static void ack(WorkerActionAcknowledgementS2C payload) {
        if (Minecraft.getInstance().screen instanceof WorkerDashboardScreen screen) {
            screen.setAcknowledgement(payload);
        }
    }
}

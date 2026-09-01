package dev.dudie.baritonehelper.worker;

import dev.dudie.baritonehelper.ActiveWorkerData;
import dev.dudie.baritonehelper.BaritoneHelper;
import dev.dudie.baritonehelper.entity.WorkerEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Loads and reconciles durable pickup transactions when their owner returns. */
@EventBusSubscriber(modid = BaritoneHelper.MOD_ID)
public final class WorkerPickupEvents {
    private WorkerPickupEvents() {}

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer owner)) return;
        owner.getServer().execute(() -> reconcile(owner));
    }

    static void reconcile(ServerPlayer owner) {
        ActiveWorkerData active = owner.getData(BaritoneHelper.ACTIVE_WORKER);
        if (active.uuid().isEmpty() || active.pickupState() == PickupState.LIVE) return;
        WorkerEntity worker = null;
        ResourceLocation dimension = ResourceLocation.tryParse(active.dimension());
        if (dimension != null) {
            ServerLevel level = owner.getServer().getLevel(
                    ResourceKey.create(Registries.DIMENSION, dimension));
            if (level != null) {
                level.getChunk(active.position());
                if (level.getEntity(active.uuid().orElseThrow()) instanceof WorkerEntity candidate
                        && candidate.isOwnedByPlayer(owner)) worker = candidate;
            }
        }
        if (worker == null) {
            UUIDSearch:
            for (ServerLevel level : owner.getServer().getAllLevels()) {
                if (level.getEntity(active.uuid().orElseThrow()) instanceof WorkerEntity candidate
                        && candidate.isOwnedByPlayer(owner)) {
                    worker = candidate;
                    break UUIDSearch;
                }
            }
        }
        WorkerPickupService.reconcile(owner, worker);
    }
}

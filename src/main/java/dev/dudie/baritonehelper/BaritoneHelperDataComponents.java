package dev.dudie.baritonehelper;

import dev.dudie.baritonehelper.worker.PackedWorkerData;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Mod-owned item data components. */
public final class BaritoneHelperDataComponents {
    public static final DeferredRegister.DataComponents DATA_COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, BaritoneHelper.MOD_ID);

    public static final DeferredHolder<
            DataComponentType<?>, DataComponentType<PackedWorkerData>> PACKED_WORKER =
            DATA_COMPONENTS.registerComponentType(
                    "packed_worker",
                    builder -> builder
                            .persistent(PackedWorkerData.CODEC)
                            .networkSynchronized(PackedWorkerData.STREAM_CODEC)
                            .cacheEncoding());

    private BaritoneHelperDataComponents() {
    }
}

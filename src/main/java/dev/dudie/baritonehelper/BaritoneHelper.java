package dev.dudie.baritonehelper;

import dev.dudie.baritonehelper.entity.WorkerEntity;
import dev.dudie.baritonehelper.item.WorkerControllerItem;
import dev.dudie.baritonehelper.item.WorkerItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.common.world.chunk.TicketController;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(BaritoneHelper.MOD_ID)
public final class BaritoneHelper {
    public static final String MOD_ID = "baritonehelper";
    public static final String LEGACY_NAMESPACE = "buddybot";

    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, MOD_ID);
    private static final DeferredRegister<EntityType<?>> LEGACY_ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, LEGACY_NAMESPACE);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);
    public static final DeferredRegister<net.neoforged.neoforge.attachment.AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(net.neoforged.neoforge.registries.NeoForgeRegistries.Keys.ATTACHMENT_TYPES, MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<WorkerEntity>> WORKER_ENTITY =
            ENTITIES.register("worker", () -> workerType(MOD_ID + ":worker"));

    /**
     * Hidden registry alias used only so already-placed buddybot:buddy_bot entities
     * remain loadable after the mod ID and public interface are renamed.
     */
    public static final DeferredHolder<EntityType<?>, EntityType<WorkerEntity>> LEGACY_WORKER_ENTITY =
            LEGACY_ENTITIES.register("buddy_bot", () -> workerType(LEGACY_NAMESPACE + ":buddy_bot"));

    public static final DeferredHolder<Item, WorkerItem> WORKER =
            ITEMS.register("worker", () -> new WorkerItem(new Item.Properties().stacksTo(1)));
    public static final DeferredHolder<Item, WorkerControllerItem> WORKER_CONTROLLER =
            ITEMS.register("worker_controller", () -> new WorkerControllerItem(new Item.Properties().stacksTo(1)));
    public static final DeferredHolder<Item, Item> CARGO_UPGRADE =
            ITEMS.register("cargo_upgrade", () -> new Item(new Item.Properties().stacksTo(1)));

    public static final DeferredHolder<
            net.neoforged.neoforge.attachment.AttachmentType<?>,
            net.neoforged.neoforge.attachment.AttachmentType<ActiveWorkerData>> ACTIVE_WORKER =
            ATTACHMENTS.register("active_worker", () -> net.neoforged.neoforge.attachment.AttachmentType
                    .serializable(ActiveWorkerData::new)
                    .copyOnDeath()
                    .build());

    public static final TicketController WORKER_TICKETS =
            new TicketController(ResourceLocation.fromNamespaceAndPath(MOD_ID, "worker"));

    public BaritoneHelper(IEventBus modBus) {
        ENTITIES.register(modBus);
        LEGACY_ENTITIES.register(modBus);
        ITEMS.register(modBus);
        ATTACHMENTS.register(modBus);
        modBus.addListener(this::attributes);
        modBus.addListener(this::creativeTab);
        modBus.addListener(this::registerTicketController);
    }

    private static EntityType<WorkerEntity> workerType(String id) {
        return EntityType.Builder
                .of(WorkerEntity::new, MobCategory.MISC)
                .sized(0.6F, 1.8F)
                .clientTrackingRange(10)
                .fireImmune()
                .build(id);
    }

    private void attributes(EntityAttributeCreationEvent event) {
        event.put(WORKER_ENTITY.get(), WorkerEntity.createAttributes().build());
        event.put(LEGACY_WORKER_ENTITY.get(), WorkerEntity.createAttributes().build());
    }

    private void creativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(WORKER.get());
            event.accept(WORKER_CONTROLLER.get());
            event.accept(CARGO_UPGRADE.get());
        }
    }

    private void registerTicketController(RegisterTicketControllersEvent event) {
        event.register(WORKER_TICKETS);
    }
}

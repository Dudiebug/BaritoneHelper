package dev.dudie.buddybot;

import dev.dudie.buddybot.entity.BuddyBotEntity;
import dev.dudie.buddybot.item.BuddyBotItem;
import dev.dudie.buddybot.logic.BuddyBotTier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(BuddyBot.MOD_ID)
public final class BuddyBot {
    public static final String MOD_ID = "buddybot";

    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);
    public static final DeferredRegister<net.neoforged.neoforge.attachment.AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(net.neoforged.neoforge.registries.NeoForgeRegistries.Keys.ATTACHMENT_TYPES, MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<BuddyBotEntity>> BUDDY_BOT_ENTITY =
            ENTITIES.register("buddy_bot", () -> EntityType.Builder
                    .of(BuddyBotEntity::new, MobCategory.CREATURE)
                    .sized(0.6F, 1.8F)
                    .clientTrackingRange(10)
                    .build(MOD_ID + ":buddy_bot"));

    public static final DeferredHolder<Item, BuddyBotItem> BUDDY_BOT = registerItem("buddy_bot", BuddyBotTier.BASIC);
    public static final DeferredHolder<Item, BuddyBotItem> BUDDY_BOT_MK2 = registerItem("buddy_bot_mk2", BuddyBotTier.MK2);
    public static final DeferredHolder<Item, BuddyBotItem> BUDDY_BOT_MK3 = registerItem("buddy_bot_mk3", BuddyBotTier.MK3);

    public static final DeferredHolder<net.neoforged.neoforge.attachment.AttachmentType<?>, net.neoforged.neoforge.attachment.AttachmentType<ActiveBuddyData>> ACTIVE_BUDDY =
            ATTACHMENTS.register("active_buddy", () -> net.neoforged.neoforge.attachment.AttachmentType
                    .serializable(ActiveBuddyData::new).copyOnDeath().build());

    public BuddyBot(IEventBus modBus) {
        ENTITIES.register(modBus);
        ITEMS.register(modBus);
        ATTACHMENTS.register(modBus);
        modBus.addListener(this::attributes);
        modBus.addListener(this::creativeTab);
    }

    private static DeferredHolder<Item, BuddyBotItem> registerItem(String name, BuddyBotTier tier) {
        return ITEMS.register(name, () -> new BuddyBotItem(tier, new Item.Properties().stacksTo(1)));
    }

    private void attributes(EntityAttributeCreationEvent event) {
        event.put(BUDDY_BOT_ENTITY.get(), BuddyBotEntity.createAttributes().build());
    }

    private void creativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(BUDDY_BOT.get());
            event.accept(BUDDY_BOT_MK2.get());
            event.accept(BUDDY_BOT_MK3.get());
        }
    }
}

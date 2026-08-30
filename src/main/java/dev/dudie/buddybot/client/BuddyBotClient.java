package dev.dudie.buddybot.client;

import dev.dudie.buddybot.BuddyBot;
import dev.dudie.buddybot.entity.BuddyBotEntity;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = BuddyBot.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class BuddyBotClient {
    private BuddyBotClient() {}

    @SubscribeEvent
    static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(BuddyBot.BUDDY_BOT_ENTITY.get(), Renderer::new);
    }

    private static final class Renderer extends HumanoidMobRenderer<BuddyBotEntity, PlayerModel<BuddyBotEntity>> {
        private static final ResourceLocation TEXTURE =
                ResourceLocation.fromNamespaceAndPath(BuddyBot.MOD_ID, "textures/entity/buddy_bot.png");

        private Renderer(EntityRendererProvider.Context context) {
            super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
        }

        @Override public ResourceLocation getTextureLocation(BuddyBotEntity entity) { return TEXTURE; }
    }
}

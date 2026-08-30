package dev.dudie.baritonehelper.client;

import dev.dudie.baritonehelper.BaritoneHelper;
import dev.dudie.baritonehelper.entity.WorkerEntity;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(
        modid = BaritoneHelper.MOD_ID,
        bus = EventBusSubscriber.Bus.MOD,
        value = Dist.CLIENT)
public final class BaritoneHelperClient {
    private BaritoneHelperClient() {
    }

    @SubscribeEvent
    static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(
                BaritoneHelper.BARITONE_HELPER_ENTITY.get(), Renderer::new);
        event.registerEntityRenderer(
                BaritoneHelper.LEGACY_BARITONE_HELPER_ENTITY.get(), Renderer::new);
    }

    private static final class Renderer
            extends HumanoidMobRenderer<WorkerEntity, PlayerModel<WorkerEntity>> {
        private static final ResourceLocation TEXTURE =
                ResourceLocation.fromNamespaceAndPath(
                        BaritoneHelper.MOD_ID,
                        "textures/entity/baritone_helper.png");

        private Renderer(EntityRendererProvider.Context context) {
            super(
                    context,
                    new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false),
                    0.5F);
        }

        @Override
        public ResourceLocation getTextureLocation(WorkerEntity entity) {
            return TEXTURE;
        }
    }
}

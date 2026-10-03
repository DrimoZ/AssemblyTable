package dev.drimoz.laserassembly.client;

import dev.drimoz.laserassembly.LaserAssembly;
import dev.drimoz.laserassembly.client.laser.LaserRenderer;
import dev.drimoz.laserassembly.client.laser.LaserScreen;
import dev.drimoz.laserassembly.client.table.AssemblyTableRenderer;
import dev.drimoz.laserassembly.client.table.AssemblyTableScreen;
import dev.drimoz.laserassembly.registry.ModBlocks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.ModelEvent;

@EventBusSubscriber(modid = LaserAssembly.MOD_ID, value = Dist.CLIENT)
public final class ClientEvents {

    @SubscribeEvent
    static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModBlocks.ASSEMBLY_TABLE_MENU.get(), AssemblyTableScreen::new);
        event.register(ModBlocks.LASER_MENU.get(), LaserScreen::new);
    }

    @SubscribeEvent
    static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlocks.LASER_ENTITY.get(), LaserRenderer::new);
        event.registerBlockEntityRenderer(ModBlocks.ASSEMBLY_TABLE_ENTITY.get(), AssemblyTableRenderer::new);
    }

    /** The parts the renderers draw themselves: no block state points at them, so nothing else would load them. */
    @SubscribeEvent
    static void registerModels(ModelEvent.RegisterAdditional event) {
        event.register(LaserRenderer.HEAD);
        event.register(LaserRenderer.GLOW);
        event.register(AssemblyTableRenderer.GLOW);
    }

    private ClientEvents() {}
}

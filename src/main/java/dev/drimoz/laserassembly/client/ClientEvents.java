package dev.drimoz.laserassembly.client;

import dev.drimoz.laserassembly.LaserAssembly;
import dev.drimoz.laserassembly.client.laser.LaserRenderer;
import dev.drimoz.laserassembly.client.laser.LaserScreen;
import dev.drimoz.laserassembly.client.table.AssemblyTableRenderer;
import dev.drimoz.laserassembly.client.table.AssemblyTableScreen;
import dev.drimoz.laserassembly.content.beam.LensItem;
import dev.drimoz.laserassembly.content.laser.AugmentItem;
import net.minecraft.client.Minecraft;
import dev.drimoz.laserassembly.registry.ModBlocks;
import dev.drimoz.laserassembly.registry.ModItems;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

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

    /** Lenses and augments wear their type's colour: one texture each, for every type a pack declares. */
    @SubscribeEvent
    static void registerItemColors(RegisterColorHandlersEvent.Item event) {
        event.register((stack, tint) -> tint == 0 ? 0xFF000000 | BeamColors.rgb(LensItem.beam(stack)) : -1, ModItems.LENS.get());
        event.register((stack, tint) -> tint != 0 || Minecraft.getInstance().level == null ? -1
                : AugmentItem.type(stack, Minecraft.getInstance().level.registryAccess())
                        .map(type -> 0xFF000000 | type.color().getValue()).orElse(-1), ModItems.AUGMENT.get());
    }

    private ClientEvents() {}
}

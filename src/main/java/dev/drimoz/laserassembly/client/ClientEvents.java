package dev.drimoz.laserassembly.client;

import dev.drimoz.laserassembly.LaserAssembly;
import dev.drimoz.laserassembly.client.laser.LaserRenderer;
import dev.drimoz.laserassembly.client.laser.LaserScreen;
import dev.drimoz.laserassembly.client.table.AssemblyTableRenderer;
import dev.drimoz.laserassembly.client.table.AssemblyTableScreen;
import dev.drimoz.laserassembly.content.beam.BeamType;
import dev.drimoz.laserassembly.registry.ModBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
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

    /**
     * A lens says which beam it fires, in that beam's colour. Read from the datapack entry rather than
     * the item, so a lens a pack adds with KubeJS gets its line too.
     */
    @SubscribeEvent
    static void lensTooltip(ItemTooltipEvent event) {
        var level = Minecraft.getInstance().level;
        ResourceLocation beam = BeamType.of(event.getItemStack(), level == null ? null : level.registryAccess());
        if (beam == null) return;
        Component name = BeamType.name(beam).copy().withColor(BeamColors.rgb(beam));
        event.getToolTip().add(1, Component.translatable(LaserAssembly.MOD_ID + ".lens.beam", name).withStyle(ChatFormatting.GRAY));
    }

    private ClientEvents() {}
}

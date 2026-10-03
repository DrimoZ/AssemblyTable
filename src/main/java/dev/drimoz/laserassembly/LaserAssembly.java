package dev.drimoz.laserassembly;

import com.mojang.logging.LogUtils;
import dev.drimoz.laserassembly.content.beam.BeamType;
import dev.drimoz.laserassembly.content.laser.AugmentType;
import dev.drimoz.laserassembly.content.table.Coolant;
import dev.drimoz.laserassembly.registry.ModBlocks;
import dev.drimoz.laserassembly.registry.ModItems;
import dev.drimoz.laserassembly.registry.ModRecipes;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.registries.DataPackRegistryEvent;
import org.slf4j.Logger;

@Mod(LaserAssembly.MOD_ID)
public class LaserAssembly {

    public static final String MOD_ID = "laserassembly";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    public LaserAssembly(IEventBus modEventBus, ModContainer modContainer) {
        ModBlocks.register(modEventBus);
        ModItems.register(modEventBus);
        ModRecipes.register(modEventBus);
        modEventBus.addListener(DataPackRegistryEvent.NewRegistry.class, event -> {
            event.dataPackRegistry(Coolant.REGISTRY, Coolant.CODEC);
            // Synced: clients draw beams, lenses and augments in their colour and describe them.
            event.dataPackRegistry(BeamType.REGISTRY, BeamType.CODEC, BeamType.CODEC);
            event.dataPackRegistry(AugmentType.REGISTRY, AugmentType.CODEC, AugmentType.CODEC);
        });

        modContainer.registerConfig(ModConfig.Type.SERVER, ServerConfig.SPEC);
        modEventBus.addListener(ModConfigEvent.Loading.class, ServerConfig::onLoad);
        modEventBus.addListener(ModConfigEvent.Reloading.class, ServerConfig::onLoad);
    }
}

package dev.drimoz.laserassembly;

import com.mojang.logging.LogUtils;
import dev.drimoz.laserassembly.content.beam.BeamType;
import dev.drimoz.laserassembly.content.laser.AugmentType;
import dev.drimoz.laserassembly.content.table.Coolant;
import dev.drimoz.laserassembly.registry.ModBlocks;
import dev.drimoz.laserassembly.registry.ModItems;
import dev.drimoz.laserassembly.registry.ModRecipes;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.registries.DataPackRegistryEvent;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import org.slf4j.Logger;

@Mod(LaserAssembly.MOD_ID)
public class LaserAssembly {

    public static final String MOD_ID = "laserassembly";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
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

        // Recipes for vanilla items survival cannot get, as a pack of their own: on by default, and a
        // pack that wants its own way to a saddle or a trident turns it off rather than overriding each file.
        modEventBus.addListener(AddPackFindersEvent.class, event -> {
            if (event.getPackType() != PackType.SERVER_DATA) return;
            event.addPackFinders(id("datapacks/vanilla_recipes"), PackType.SERVER_DATA,
                    Component.translatable(MOD_ID + ".pack.vanilla_recipes"), PackSource.BUILT_IN, false, Pack.Position.TOP);
        });

        modContainer.registerConfig(ModConfig.Type.SERVER, ServerConfig.SPEC);
        modEventBus.addListener(ModConfigEvent.Loading.class, ServerConfig::onLoad);
        modEventBus.addListener(ModConfigEvent.Reloading.class, ServerConfig::onLoad);
    }
}

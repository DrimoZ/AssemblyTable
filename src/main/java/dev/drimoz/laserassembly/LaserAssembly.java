package dev.drimoz.laserassembly;

import com.mojang.logging.LogUtils;
import dev.drimoz.laserassembly.content.table.Coolant;
import dev.drimoz.laserassembly.registry.ModBlocks;
import dev.drimoz.laserassembly.registry.ModRecipes;
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

    public LaserAssembly(IEventBus modEventBus, ModContainer modContainer) {
        ModBlocks.register(modEventBus);
        ModRecipes.register(modEventBus);
        modEventBus.addListener(DataPackRegistryEvent.NewRegistry.class,
                event -> event.dataPackRegistry(Coolant.REGISTRY, Coolant.CODEC));

        modContainer.registerConfig(ModConfig.Type.SERVER, ServerConfig.SPEC);
        modEventBus.addListener(ModConfigEvent.Loading.class, ServerConfig::onLoad);
        modEventBus.addListener(ModConfigEvent.Reloading.class, ServerConfig::onLoad);
    }
}

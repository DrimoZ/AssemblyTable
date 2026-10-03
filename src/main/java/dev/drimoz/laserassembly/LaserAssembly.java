package dev.drimoz.laserassembly;

import dev.drimoz.laserassembly.registry.ModRecipes;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

@Mod(LaserAssembly.MOD_ID)
public class LaserAssembly {

    public static final String MOD_ID = "laserassembly";

    public LaserAssembly(IEventBus modEventBus, ModContainer modContainer) {
        ModRecipes.register(modEventBus);
    }
}

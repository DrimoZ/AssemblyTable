package dev.drimoz.laserassembly.client;

import dev.drimoz.laserassembly.LaserAssembly;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/** The client's own entry point: only it may touch screens, so a dedicated server never loads this. */
@Mod(value = LaserAssembly.MOD_ID, dist = Dist.CLIENT)
public final class LaserAssemblyClient {

    /** NeoForge's generated screen, under Mods: the server config, editable in a single-player world. */
    public LaserAssemblyClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }
}

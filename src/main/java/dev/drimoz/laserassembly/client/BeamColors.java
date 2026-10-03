package dev.drimoz.laserassembly.client;

import dev.drimoz.laserassembly.content.beam.BeamType;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/** A beam type's colour as the client knows it, from the synced datapack registry. */
public final class BeamColors {

    /** BuildCraft's red: a laser with no lens, or a beam type the client does not know. */
    public static final int RAW = 0xFF3C28;

    public static int rgb(@Nullable ResourceLocation beam) {
        if (beam == null || Minecraft.getInstance().level == null) return RAW;
        return Minecraft.getInstance().level.registryAccess().registry(BeamType.REGISTRY)
                .map(registry -> registry.get(beam))
                .map(type -> type.color().getValue())
                .orElse(RAW);
    }

    private BeamColors() {}
}

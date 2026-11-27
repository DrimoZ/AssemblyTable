package dev.drimoz.laserassembly.client;

import dev.drimoz.laserassembly.content.beam.BeamType;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

/** A beam type's colour as the client knows it, from the synced datapack registry. */
public final class BeamColors {

    /** BuildCraft's red: a laser with no lens, or a beam type the client does not know. */
    public static final int RAW = 0xFF3C28;

    public static int rgb(@Nullable Identifier beam) {
        if (beam == null || Minecraft.getInstance().level == null) return RAW;
        return Minecraft.getInstance().level.registryAccess().lookup(BeamType.REGISTRY)
                .flatMap(registry -> registry.getOptional(beam))
                .map(type -> type.color().getValue())
                .orElse(RAW);
    }

    private BeamColors() {}
}

package dev.drimoz.laserassembly.content.beam;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.drimoz.laserassembly.LaserAssembly;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

/**
 * A kind of beam a lens gives a laser - cutting, welding, engraving - as a datapack entry, so a
 * pack adds its own without code. Synced to clients: the beam and the lens are drawn in its colour.
 */
public record BeamType(TextColor color) {

    public static final ResourceKey<Registry<BeamType>> REGISTRY =
            ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath(LaserAssembly.MOD_ID, "beam"));

    /** {@code "color": "#FF8C1A"}. */
    public static final Codec<BeamType> CODEC = RecordCodecBuilder.create(i -> i.group(
            TextColor.CODEC.fieldOf("color").forGetter(BeamType::color)
    ).apply(i, BeamType::new));

    /** {@code beam.<namespace>.<path>}: a pack that adds a beam adds its name to its own lang file. */
    public static Component name(ResourceLocation id) {
        return Component.translatable("beam." + id.getNamespace() + "." + id.getPath());
    }
}

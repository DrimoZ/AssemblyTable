package dev.drimoz.laserassembly.content.table;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.drimoz.laserassembly.LaserAssembly;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;

/**
 * A datapack entry: blocks that cool a table when placed against it, and by how much heat per tick.
 * {@code blocks} takes an id, a {@code #tag} or a list, so a pack adds its own cold fluids without code.
 */
public record Coolant(HolderSet<Block> blocks, double cooling) {

    public static final ResourceKey<Registry<Coolant>> REGISTRY =
            ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath(LaserAssembly.MOD_ID, "coolant"));

    public static final Codec<Coolant> CODEC = RecordCodecBuilder.create(i -> i.group(
            RegistryCodecs.homogeneousList(Registries.BLOCK).fieldOf("blocks").forGetter(Coolant::blocks),
            Codec.doubleRange(0, 1_000_000).fieldOf("cooling").forGetter(Coolant::cooling)
    ).apply(i, Coolant::new));
}

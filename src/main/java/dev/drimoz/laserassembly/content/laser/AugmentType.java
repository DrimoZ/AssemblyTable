package dev.drimoz.laserassembly.content.laser;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.drimoz.laserassembly.LaserAssembly;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * What an augment does to the laser it sits in, as a datapack entry naming its item - a plain
 * item, for the same reason as lenses (see {@code BeamType}). Every effect is optional, so a pack
 * combines them freely. Synced: the item's tooltip is read on the client.
 *
 * @param group      augments of one group exclude each other on a laser - Range I and Range II -
 *                   so a laser takes one of each kind; absent, the entry is its own group
 * @param range      blocks added to the distance a laser finds a table at
 * @param efficiency multiplies the FE a shot costs: 0.8 is 20 % cheaper
 * @param regulator  the laser sets its own intensity: the highest that fits the step without heating
 * @param barrel     lens slots added; the laser fires whichever lens fits the step in progress
 */
public record AugmentType(Item item, Optional<ResourceLocation> group, int range, double efficiency,
                          boolean regulator, int barrel) {

    public static final ResourceKey<Registry<AugmentType>> REGISTRY =
            ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath(LaserAssembly.MOD_ID, "augment"));

    public static final Codec<AugmentType> CODEC = RecordCodecBuilder.create(i -> i.group(
            BuiltInRegistries.ITEM.byNameCodec().fieldOf("item").forGetter(AugmentType::item),
            ResourceLocation.CODEC.optionalFieldOf("group").forGetter(AugmentType::group),
            Codec.intRange(0, 32).optionalFieldOf("range", 0).forGetter(AugmentType::range),
            Codec.doubleRange(0.05, 1).optionalFieldOf("efficiency", 1.0).forGetter(AugmentType::efficiency),
            Codec.BOOL.optionalFieldOf("regulator", false).forGetter(AugmentType::regulator),
            Codec.intRange(0, LaserBlockEntity.BARREL_SLOTS).optionalFieldOf("barrel", 0).forGetter(AugmentType::barrel)
    ).apply(i, AugmentType::new));

    /** The augment a stack is, or empty if none. A scan, as for lenses: only when a laser's augments change. */
    public static Optional<Holder.Reference<AugmentType>> of(ItemStack stack, @Nullable HolderLookup.Provider registries) {
        if (stack.isEmpty() || registries == null) return Optional.empty();
        return registries.lookup(REGISTRY).flatMap(augments -> augments.listElements()
                .filter(augment -> augment.value().item() == stack.getItem()).findFirst());
    }

    /** The group it excludes on a laser: its own, or itself. */
    public static ResourceLocation group(Holder.Reference<AugmentType> augment) {
        return augment.value().group.orElse(augment.key().location());
    }
}

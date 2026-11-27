package dev.drimoz.laserassembly.content.beam;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.drimoz.laserassembly.LaserAssembly;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * A kind of beam - cutting, welding, engraving - as a datapack entry, given by the lens item it
 * names. The lens is a plain item rather than one item told apart by a component: tags, KubeJS,
 * CraftTweaker and JEI all work on item ids, and a pack that adds a beam registers its lens item
 * with any of them and names it here. Synced to clients: the beam is drawn in its colour.
 */
public record BeamType(Item item, TextColor color) {

    public static final ResourceKey<Registry<BeamType>> REGISTRY =
            ResourceKey.createRegistryKey(Identifier.fromNamespaceAndPath(LaserAssembly.MOD_ID, "beam"));

    /** {@code "item": "laserassembly:cut_lens", "color": "#FF8C1A"}; an unknown item refuses the entry. */
    public static final Codec<BeamType> CODEC = RecordCodecBuilder.create(i -> i.group(
            BuiltInRegistries.ITEM.byNameCodec().fieldOf("item").forGetter(BeamType::item),
            TextColor.CODEC.fieldOf("color").forGetter(BeamType::color)
    ).apply(i, BeamType::new));

    /**
     * The beam a stack gives a laser, or null if it is no lens. A scan of a handful of entries: the
     * laser keeps what it finds until its lenses change, so this never runs on a tick.
     */
    public static @Nullable Identifier of(ItemStack stack, @Nullable HolderLookup.Provider registries) {
        if (stack.isEmpty() || registries == null) return null;
        return registries.lookup(REGISTRY).flatMap(beams -> beams.listElements()
                        .filter(beam -> beam.value().item() == stack.getItem()).findFirst())
                .map(beam -> beam.key().identifier()).orElse(null);
    }

    /** {@code beam.<namespace>.<path>}: a pack that adds a beam adds its name to its own lang file. */
    public static Component name(Identifier id) {
        return Component.translatable("beam." + id.getNamespace() + "." + id.getPath());
    }
}

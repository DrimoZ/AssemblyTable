package dev.drimoz.laserassembly.content.beam;

import dev.drimoz.laserassembly.registry.ModItems;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/** One item for every beam type: which beam it gives comes from its {@code laserassembly:beam} component. */
public class LensItem extends Item {

    public LensItem(Properties properties) {
        super(properties);
    }

    public static ItemStack of(ResourceKey<BeamType> beam) {
        ItemStack stack = new ItemStack(ModItems.LENS.get());
        stack.set(ModItems.BEAM.get(), beam);
        return stack;
    }

    /** The beam a stack gives a laser, or null if it is not a lens of any. */
    public static @Nullable ResourceLocation beam(ItemStack stack) {
        ResourceKey<BeamType> beam = stack.get(ModItems.BEAM.get());
        return beam == null ? null : beam.location();
    }

    @Override
    public Component getName(ItemStack stack) {
        ResourceLocation beam = beam(stack);
        return beam == null ? super.getName(stack) : Component.translatable(getDescriptionId() + ".of", BeamType.name(beam));
    }
}

package dev.drimoz.laserassembly.content.laser;

import dev.drimoz.laserassembly.LaserAssembly;
import dev.drimoz.laserassembly.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/** One item for every augment: which one it is comes from its {@code laserassembly:augment} component. */
public class AugmentItem extends Item {

    public AugmentItem(Properties properties) {
        super(properties);
    }

    public static ItemStack of(ResourceKey<AugmentType> augment) {
        ItemStack stack = new ItemStack(ModItems.AUGMENT.get());
        stack.set(ModItems.AUGMENT_TYPE.get(), augment);
        return stack;
    }

    public static @Nullable ResourceKey<AugmentType> key(ItemStack stack) {
        return stack.get(ModItems.AUGMENT_TYPE.get());
    }

    /** The augment's definition, or empty if the stack is none or a pack removed it. */
    public static Optional<AugmentType> type(ItemStack stack, @Nullable HolderLookup.Provider registries) {
        ResourceKey<AugmentType> key = key(stack);
        if (key == null || registries == null) return Optional.empty();
        return registries.lookup(AugmentType.REGISTRY).flatMap(lookup -> lookup.get(key)).map(holder -> holder.value());
    }

    @Override
    public Component getName(ItemStack stack) {
        ResourceKey<AugmentType> key = key(stack);
        return key == null ? super.getName(stack) : Component.translatable(getDescriptionId() + ".of", AugmentType.name(key.location()));
    }

    /** What it changes, in the order a player weighs it. */
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        type(stack, context.registries()).ifPresent(augment -> {
            if (augment.range() > 0) lines.add(text("augment.range", augment.range()));
            if (augment.efficiency() < 1) lines.add(text("augment.efficiency", Math.round((1 - augment.efficiency()) * 100)));
            if (augment.regulator()) lines.add(text("augment.regulator"));
            if (augment.barrel() > 0) lines.add(text("augment.barrel", augment.barrel()));
        });
    }

    private static Component text(String key, Object... args) {
        return Component.translatable(LaserAssembly.MOD_ID + "." + key, args).withStyle(ChatFormatting.GRAY);
    }

    public static @Nullable ResourceLocation id(ItemStack stack) {
        ResourceKey<AugmentType> key = key(stack);
        return key == null ? null : key.location();
    }
}

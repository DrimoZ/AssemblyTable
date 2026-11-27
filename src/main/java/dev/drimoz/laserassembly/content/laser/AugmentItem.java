package dev.drimoz.laserassembly.content.laser;

import dev.drimoz.laserassembly.LaserAssembly;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.function.Consumer;

/** The built-in augments: plain items, with a tooltip saying what their datapack entry does. */
public class AugmentItem extends Item {

    public AugmentItem(Properties properties) {
        super(properties);
    }

    /** What it changes, in the order a player weighs it. */
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> lines, TooltipFlag flag) {
        AugmentType.of(stack, context.registries()).map(holder -> holder.value()).ifPresent(augment -> {
            if (augment.range() > 0) lines.accept(text("augment.range", augment.range()));
            if (augment.efficiency() < 1) lines.accept(text("augment.efficiency", Math.round((1 - augment.efficiency()) * 100)));
            if (augment.regulator()) lines.accept(text("augment.regulator"));
            if (augment.barrel() > 0) lines.accept(text("augment.barrel", augment.barrel()));
        });
    }

    private static Component text(String key, Object... args) {
        return Component.translatable(LaserAssembly.MOD_ID + "." + key, args).withStyle(ChatFormatting.GRAY);
    }
}

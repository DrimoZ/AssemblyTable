package dev.drimoz.laserassembly.compat.jade;

import dev.drimoz.laserassembly.LaserAssembly;
import dev.drimoz.laserassembly.content.table.AssemblyTableBlock;
import dev.drimoz.laserassembly.content.table.AssemblyTableBlockEntity;
import dev.drimoz.laserassembly.recipe.AssemblyRecipe;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.ui.IElementHelper;

/**
 * The table at a glance: what it makes, which step, how far, how hot. Only Jade loads this class, so
 * the mod runs the same without it. The table already syncs which recipe runs; the figures that
 * change every tick come through Jade's own request while someone is looking.
 */
@WailaPlugin
public final class JadePlugin implements IWailaPlugin {

    private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(LaserAssembly.MOD_ID, "assembly_table");

    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(Table.INSTANCE, AssemblyTableBlockEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(Table.INSTANCE, AssemblyTableBlock.class);
    }

    private enum Table implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
        INSTANCE;

        @Override
        public void appendServerData(CompoundTag data, BlockAccessor accessor) {
            if (!(accessor.getBlockEntity() instanceof AssemblyTableBlockEntity table)) return;
            data.putInt("step", table.stepIndex());
            data.putInt("steps", table.stepCount());
            data.putFloat("progress", table.stepProgress());
            data.putFloat("heat", table.heatFraction());
        }

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            if (!(accessor.getBlockEntity() instanceof AssemblyTableBlockEntity table)) return;
            CompoundTag data = accessor.getServerData();
            ResourceLocation current = table.current();
            AssemblyRecipe recipe = current == null ? null : accessor.getLevel().getRecipeManager().byKey(current)
                    .map(holder -> holder.value() instanceof AssemblyRecipe assembly ? assembly : null).orElse(null);
            if (recipe == null) {
                tooltip.add(text("jade.idle").withStyle(ChatFormatting.GRAY));
            } else {
                // The item itself, then "Making ...": a bare item name did not say it was what the table makes.
                ItemStack result = recipe.getResultItem(accessor.getLevel().registryAccess());
                tooltip.add(IElementHelper.get().smallItem(result));
                tooltip.append(text("jade.making", result.getHoverName()));
                tooltip.add(text("jade.step", data.getInt("step") + 1, data.getInt("steps"),
                        Math.round(100 * data.getFloat("progress"))).withStyle(ChatFormatting.GRAY));
            }
            if (data.getFloat("heat") > 0) {
                tooltip.add(text("jade.heat", Math.round(100 * data.getFloat("heat"))).withStyle(ChatFormatting.GOLD));
            }
        }

        @Override
        public ResourceLocation getUid() {
            return UID;
        }
    }

    private static net.minecraft.network.chat.MutableComponent text(String key, Object... args) {
        return Component.translatable(LaserAssembly.MOD_ID + "." + key, args);
    }
}

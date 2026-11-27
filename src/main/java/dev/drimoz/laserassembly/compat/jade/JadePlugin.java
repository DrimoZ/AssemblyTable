package dev.drimoz.laserassembly.compat.jade;

import dev.drimoz.laserassembly.LaserAssembly;
import dev.drimoz.laserassembly.content.beam.BeamType;
import dev.drimoz.laserassembly.content.laser.LaserBlock;
import dev.drimoz.laserassembly.content.laser.LaserBlockEntity;
import dev.drimoz.laserassembly.content.table.AssemblyTableBlock;
import dev.drimoz.laserassembly.content.table.AssemblyTableBlockEntity;
import dev.drimoz.laserassembly.recipe.AssemblyRecipe;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
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
import snownee.jade.api.ui.JadeUI;
import dev.drimoz.laserassembly.registry.ModRecipes;

/**
 * The table at a glance: what it makes, which step, how far, how hot; a laser, whether and what it
 * fires. Only Jade loads this class, so the mod runs the same without it. The table already syncs
 * which recipe runs; the figures that change every tick come through Jade's own request while
 * someone is looking.
 */
@WailaPlugin
public final class JadePlugin implements IWailaPlugin {

    private static final Identifier UID = Identifier.fromNamespaceAndPath(LaserAssembly.MOD_ID, "assembly_table");
    private static final Identifier LASER_UID = Identifier.fromNamespaceAndPath(LaserAssembly.MOD_ID, "laser");

    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(TableData.INSTANCE, AssemblyTableBlockEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(Table.INSTANCE, AssemblyTableBlock.class);
        registration.registerBlockComponent(Laser.INSTANCE, LaserBlock.class);
    }

    /**
     * A laser at a glance: whether it fires, and if not why; its intensity; its beam, in colour. All
     * of it already reaches the client with the laser's own sync, so no server data is asked for.
     */
    private enum Laser implements IBlockComponentProvider {
        INSTANCE;

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            if (!(accessor.getBlockEntity() instanceof LaserBlockEntity laser)) return;
            int state = laser.target() == null ? LaserBlockEntity.STATE_NO_TARGET : laser.state();
            ChatFormatting colour = state == LaserBlockEntity.STATE_FIRING ? ChatFormatting.GREEN
                    : state == LaserBlockEntity.STATE_HOLDING || state == LaserBlockEntity.STATE_NO_TARGET ? ChatFormatting.GRAY : ChatFormatting.RED;
            tooltip.add(Component.translatable(LaserBlockEntity.stateKey(state)).withStyle(colour));
            tooltip.add(laser.regulated() ? text("tab.lasers.auto", laser.intensity())
                    : text("laser.intensity", laser.intensity(), LaserBlockEntity.MAX_INTENSITY));
            Identifier beam = laser.beam();
            if (beam == null) {
                tooltip.add(text("beam.raw").withStyle(ChatFormatting.GRAY));
            } else {
                int rgb = accessor.getLevel().registryAccess().lookup(BeamType.REGISTRY)
                        .flatMap(registry -> registry.getOptional(beam)).map(type -> type.color().getValue()).orElse(0xAAAAAA);
                tooltip.add(BeamType.name(beam).copy().withColor(rgb));
            }
        }

        @Override
        public Identifier getUid() {
            return LASER_UID;
        }
    }

    /** The figures that change every tick, asked of the server while someone looks. Its own class: Jade refuses one that also draws, since 1.21.6. */
    private enum TableData implements IServerDataProvider<BlockAccessor> {
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
        public Identifier getUid() {
            return UID;
        }
    }

    private enum Table implements IBlockComponentProvider {
        INSTANCE;

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            if (!(accessor.getBlockEntity() instanceof AssemblyTableBlockEntity table)) return;
            CompoundTag data = accessor.getServerData();
            Identifier current = table.current();
            AssemblyRecipe recipe = current == null ? null : ModRecipes.byId(accessor.getLevel(), current);
            if (recipe == null) {
                tooltip.add(text("jade.idle").withStyle(ChatFormatting.GRAY));
            } else {
                // The item itself, then "Making ...": a bare item name did not say it was what the table makes.
                ItemStack result = recipe.result();
                tooltip.add(JadeUI.smallItem(result));
                tooltip.append(text("jade.making", result.getHoverName()));
                tooltip.add(text("jade.step", data.getIntOr("step", 0) + 1, data.getIntOr("steps", 0),
                        Math.round(100 * data.getFloatOr("progress", 0))).withStyle(ChatFormatting.GRAY));
            }
            if (data.getFloatOr("heat", 0) > 0) {
                tooltip.add(text("jade.heat", Math.round(100 * data.getFloatOr("heat", 0))).withStyle(ChatFormatting.GOLD));
            }
        }

        @Override
        public Identifier getUid() {
            return UID;
        }
    }

    private static net.minecraft.network.chat.MutableComponent text(String key, Object... args) {
        return Component.translatable(LaserAssembly.MOD_ID + "." + key, args);
    }
}

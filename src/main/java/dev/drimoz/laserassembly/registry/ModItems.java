package dev.drimoz.laserassembly.registry;

import dev.drimoz.laserassembly.LaserAssembly;
import dev.drimoz.laserassembly.content.beam.BeamType;
import dev.drimoz.laserassembly.content.beam.LensItem;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {

    static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(LaserAssembly.MOD_ID);
    private static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, LaserAssembly.MOD_ID);
    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, LaserAssembly.MOD_ID);

    /** The beam a lens gives, by id: a key rather than a holder, so a lens of a beam a pack removed simply gives none. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ResourceKey<BeamType>>> BEAM =
            COMPONENTS.registerComponentType("beam", builder -> builder
                    .persistent(ResourceKey.codec(BeamType.REGISTRY))
                    .networkSynchronized(ResourceKey.streamCodec(BeamType.REGISTRY)));

    public static final DeferredItem<LensItem> LENS = ITEMS.register("lens", () -> new LensItem(new Item.Properties().stacksTo(16)));

    /** Every item the mod registers, and one lens per beam type the datapacks declare. */
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.laserassembly"))
            .icon(() -> new ItemStack(ModBlocks.LASER_ITEM.get()))
            .displayItems((parameters, output) -> ITEMS.getEntries().forEach(item -> {
                if (item == LENS) {
                    parameters.holders().lookup(BeamType.REGISTRY).ifPresent(beams ->
                            beams.listElementIds().forEach(beam -> output.accept(LensItem.of(beam))));
                } else {
                    output.accept(item.get());
                }
            }))
            .build());

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        COMPONENTS.register(modBus);
        TABS.register(modBus);
    }

    private ModItems() {}
}

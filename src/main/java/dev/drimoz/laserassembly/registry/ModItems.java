package dev.drimoz.laserassembly.registry;

import dev.drimoz.laserassembly.LaserAssembly;
import dev.drimoz.laserassembly.content.laser.AugmentItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * One item per lens and per augment. What each does lives in its datapack entry, which names the
 * item: {@code laserassembly:beam/cut} gives {@code cut_lens} its beam.
 */
public final class ModItems {

    static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(LaserAssembly.MOD_ID);
    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, LaserAssembly.MOD_ID);

    public static final DeferredItem<Item> CUT_LENS = lens("cut_lens");
    public static final DeferredItem<Item> WELD_LENS = lens("weld_lens");
    public static final DeferredItem<Item> ENGRAVE_LENS = lens("engrave_lens");

    public static final DeferredItem<AugmentItem> REGULATOR_AUGMENT = augment("regulator_augment");
    public static final DeferredItem<AugmentItem> BARREL_AUGMENT = augment("barrel_augment");
    public static final DeferredItem<AugmentItem> RANGE_AUGMENT_1 = augment("range_augment_1");
    public static final DeferredItem<AugmentItem> RANGE_AUGMENT_2 = augment("range_augment_2");
    public static final DeferredItem<AugmentItem> EFFICIENCY_AUGMENT_1 = augment("efficiency_augment_1");
    public static final DeferredItem<AugmentItem> EFFICIENCY_AUGMENT_2 = augment("efficiency_augment_2");

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.laserassembly"))
            .icon(() -> new ItemStack(ModBlocks.LASER_ITEM.get()))
            .displayItems((parameters, output) -> ITEMS.getEntries().forEach(item -> output.accept(item.get())))
            .build());

    private static DeferredItem<Item> lens(String name) {
        return ITEMS.registerSimpleItem(name, properties -> properties.stacksTo(16));
    }

    private static DeferredItem<AugmentItem> augment(String name) {
        return ITEMS.registerItem(name, AugmentItem::new, properties -> properties.stacksTo(16));
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        TABS.register(modBus);
    }

    private ModItems() {}
}

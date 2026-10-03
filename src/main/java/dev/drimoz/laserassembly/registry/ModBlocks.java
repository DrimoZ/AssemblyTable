package dev.drimoz.laserassembly.registry;

import dev.drimoz.laserassembly.LaserAssembly;
import dev.drimoz.laserassembly.content.laser.LaserBlock;
import dev.drimoz.laserassembly.content.laser.LaserBlockEntity;
import dev.drimoz.laserassembly.content.laser.LaserMenu;
import dev.drimoz.laserassembly.content.table.AssemblyTableBlock;
import dev.drimoz.laserassembly.content.table.AssemblyTableBlockEntity;
import dev.drimoz.laserassembly.content.table.AssemblyTableMenu;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(LaserAssembly.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(LaserAssembly.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, LaserAssembly.MOD_ID);

    public static final DeferredBlock<AssemblyTableBlock> ASSEMBLY_TABLE = BLOCKS.register("assembly_table",
            () -> new AssemblyTableBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL).strength(3.5f).sound(SoundType.METAL).requiresCorrectToolForDrops().noOcclusion()));

    public static final DeferredItem<BlockItem> ASSEMBLY_TABLE_ITEM = ITEMS.registerSimpleBlockItem(ASSEMBLY_TABLE);

    @SuppressWarnings("DataFlowIssue") // the data fixer type is null for every modded block entity
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AssemblyTableBlockEntity>> ASSEMBLY_TABLE_ENTITY =
            BLOCK_ENTITIES.register("assembly_table",
                    () -> BlockEntityType.Builder.of(AssemblyTableBlockEntity::new, ASSEMBLY_TABLE.get()).build(null));

    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, LaserAssembly.MOD_ID);

    /** Extra data is the table's position; the client reads the table it already has there. */
    public static final DeferredHolder<MenuType<?>, MenuType<AssemblyTableMenu>> ASSEMBLY_TABLE_MENU =
            MENUS.register("assembly_table", () -> IMenuTypeExtension.create(AssemblyTableMenu::new));

    public static final DeferredBlock<LaserBlock> LASER = BLOCKS.register("laser",
            () -> new LaserBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL).strength(3.5f).sound(SoundType.METAL).requiresCorrectToolForDrops()));

    public static final DeferredItem<BlockItem> LASER_ITEM = ITEMS.registerSimpleBlockItem(LASER);

    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LaserBlockEntity>> LASER_ENTITY =
            BLOCK_ENTITIES.register("laser", () -> BlockEntityType.Builder.of(LaserBlockEntity::new, LASER.get()).build(null));

    public static final DeferredHolder<MenuType<?>, MenuType<LaserMenu>> LASER_MENU =
            MENUS.register("laser", () -> IMenuTypeExtension.create(LaserMenu::new));

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        MENUS.register(modBus);
        TABS.register(modBus);
        modBus.addListener(ModBlocks::registerCapabilities);
    }

    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, LaserAssembly.MOD_ID);

    /** Lists every item the mod registers, so a new item never needs adding here by hand. */
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.laserassembly"))
            .icon(() -> new ItemStack(LASER_ITEM.get()))
            .displayItems((parameters, output) -> ITEMS.getEntries().forEach(item -> output.accept(item.get())))
            .build());

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ASSEMBLY_TABLE_ENTITY.get(), (table, side) -> table.automation());
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ASSEMBLY_TABLE_ENTITY.get(), (table, side) -> table.energy());
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, LASER_ENTITY.get(), (laser, side) -> laser.energy());
    }

    private ModBlocks() {}
}

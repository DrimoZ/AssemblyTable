package dev.drimoz.laserassembly.registry;

import dev.drimoz.laserassembly.LaserAssembly;
import dev.drimoz.laserassembly.content.laser.LaserBlock;
import dev.drimoz.laserassembly.content.laser.LaserBlockEntity;
import dev.drimoz.laserassembly.content.laser.LaserMenu;
import dev.drimoz.laserassembly.content.table.AssemblyTableBlock;
import dev.drimoz.laserassembly.content.table.AssemblyTableBlockEntity;
import dev.drimoz.laserassembly.content.table.AssemblyTableMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(LaserAssembly.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, LaserAssembly.MOD_ID);
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, LaserAssembly.MOD_ID);

    public static final DeferredBlock<AssemblyTableBlock> ASSEMBLY_TABLE = BLOCKS.registerBlock("assembly_table", AssemblyTableBlock::new, properties -> properties
                    .mapColor(MapColor.METAL).strength(3.5f).sound(SoundType.METAL).requiresCorrectToolForDrops().noOcclusion());

    public static final DeferredItem<BlockItem> ASSEMBLY_TABLE_ITEM = ModItems.ITEMS.registerSimpleBlockItem(ASSEMBLY_TABLE);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AssemblyTableBlockEntity>> ASSEMBLY_TABLE_ENTITY =
            BLOCK_ENTITIES.register("assembly_table",
                    () -> new BlockEntityType<>(AssemblyTableBlockEntity::new, ASSEMBLY_TABLE.get()));

    /** Extra data is the table's position; the client reads the table it already has there. */
    public static final DeferredHolder<MenuType<?>, MenuType<AssemblyTableMenu>> ASSEMBLY_TABLE_MENU =
            MENUS.register("assembly_table", () -> IMenuTypeExtension.create(AssemblyTableMenu::new));

    public static final DeferredBlock<LaserBlock> LASER = BLOCKS.registerBlock("laser", LaserBlock::new, properties -> properties
                    .mapColor(MapColor.METAL).strength(3.5f).sound(SoundType.METAL).requiresCorrectToolForDrops().noOcclusion());

    public static final DeferredItem<BlockItem> LASER_ITEM = ModItems.ITEMS.registerSimpleBlockItem(LASER);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LaserBlockEntity>> LASER_ENTITY =
            BLOCK_ENTITIES.register("laser", () -> new BlockEntityType<>(LaserBlockEntity::new, LASER.get()));

    public static final DeferredHolder<MenuType<?>, MenuType<LaserMenu>> LASER_MENU =
            MENUS.register("laser", () -> IMenuTypeExtension.create(LaserMenu::new));

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        MENUS.register(modBus);
        modBus.addListener(ModBlocks::registerCapabilities);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.Item.BLOCK, ASSEMBLY_TABLE_ENTITY.get(), (table, side) -> table.handler(side));
        event.registerBlockEntity(Capabilities.Energy.BLOCK, LASER_ENTITY.get(), (laser, side) -> laser.energy());
    }

    private ModBlocks() {}
}

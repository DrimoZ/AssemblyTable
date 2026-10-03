package dev.drimoz.laserassembly.registry;

import dev.drimoz.laserassembly.LaserAssembly;
import dev.drimoz.laserassembly.content.table.AssemblyTableBlock;
import dev.drimoz.laserassembly.content.table.AssemblyTableBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
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

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        modBus.addListener(ModBlocks::registerCapabilities);
        // Until the mod has enough items for a tab of its own.
        modBus.addListener(BuildCreativeModeTabContentsEvent.class, event -> {
            if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) event.accept(ASSEMBLY_TABLE_ITEM);
        });
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ASSEMBLY_TABLE_ENTITY.get(), (table, side) -> table.automation());
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ASSEMBLY_TABLE_ENTITY.get(), (table, side) -> table.energy());
    }

    private ModBlocks() {}
}

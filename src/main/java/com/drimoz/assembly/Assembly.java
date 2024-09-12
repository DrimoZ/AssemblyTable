package com.drimoz.assembly;

import com.drimoz.assembly.recipe.AssemblyTableRecipe;
import com.drimoz.assembly.core.block.AssemblyTableBlock;
import com.drimoz.assembly.core.tileentity.AssemblyTableBlockEntity;
import com.drimoz.assembly.client.menu.AssemblyTableMenu;
import com.drimoz.assembly.client.screen.AssemblyTableScreen;
import com.drimoz.assembly.network.AssemblyNetwork;
import com.mojang.logging.LogUtils;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;

@Mod(Assembly.MOD_ID)
public class Assembly {

    // Mod Generics

    public static final String MOD_ID = "assembly";
    public static final String MOD_NAME = "Assembly";
    public static final Logger LOGGER = LogUtils.getLogger();

    // Mod Deferred Registries

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, MOD_ID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MOD_ID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, MOD_ID);
    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS = DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, MOD_ID);

    // Blocks

    public static final RegistryObject<Block> ASSEMBLY_TABLE = BLOCKS.register("assembly_table", AssemblyTableBlock::new);

    // BlockEntities

    public static final RegistryObject<BlockEntityType<AssemblyTableBlockEntity>> ASSEMBLY_TABLE_ENTITY = BLOCK_ENTITY_TYPES.register("assembly_table", () -> BlockEntityType.Builder.of(AssemblyTableBlockEntity::new, ASSEMBLY_TABLE.get()).build(null));

    // Items

    public static final RegistryObject<Item> ASSEMBLY_TABLE_ITEM = ITEMS.register("assembly_table",
            () -> new BlockItem(ASSEMBLY_TABLE.get(), new Item.Properties()));

    // Creative Tab

    public static final RegistryObject<CreativeModeTab> ASSEMBLY_CREATIVE_TAB = CREATIVE_MODE_TABS.register("assembly", () -> CreativeModeTab.builder()
            .withTabsBefore(CreativeModeTabs.COMBAT)
            .title(Component.translatable("itemGroup.assembly.assembly"))
            .icon(() -> new ItemStack(ASSEMBLY_TABLE.get()))
            .displayItems((parameters, output) -> {
                output.accept(ASSEMBLY_TABLE_ITEM.get());
            }).build());

    // Menus

    public static final RegistryObject<MenuType<AssemblyTableMenu>> ASSEMBLY_TABLE_MENU =
            MENUS.register(
                    "assembly_table_menu",
                    () -> IForgeMenuType.create((windowId, inv, data) -> new AssemblyTableMenu(windowId, inv, (AssemblyTableBlockEntity) inv.player.level().getBlockEntity(data.readBlockPos()))));

    // Recipe Serializer

    public static final RegistryObject<RecipeSerializer<AssemblyTableRecipe>> ASSEMBLING_SERIALIZER = SERIALIZERS.register("assembling", () -> AssemblyTableRecipe.Serializer.INSTANCE);

    // Capability

    public Assembly() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        modEventBus.addListener(this::commonSetup);

        BLOCKS.register(modEventBus);
        BLOCK_ENTITY_TYPES.register(modEventBus);
        ITEMS.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);
        SERIALIZERS.register(modEventBus);

        MENUS.register(FMLJavaModLoadingContext.get().getModEventBus());

        AssemblyNetwork.init();

        MinecraftForge.EVENT_BUS.register(this);

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        MenuScreens.register(ASSEMBLY_TABLE_MENU.get(), AssemblyTableScreen::new);
    }
}

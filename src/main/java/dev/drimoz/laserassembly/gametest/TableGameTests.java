package dev.drimoz.laserassembly.gametest;

import dev.drimoz.laserassembly.LaserAssembly;
import dev.drimoz.laserassembly.content.table.AssemblyTableBlockEntity;
import dev.drimoz.laserassembly.registry.ModBlocks;
import dev.drimoz.laserassembly.content.beam.BeamType;
import dev.drimoz.laserassembly.content.beam.LensItem;
import dev.drimoz.laserassembly.content.laser.LaserBlock;
import net.minecraft.resources.ResourceKey;
import dev.drimoz.laserassembly.content.laser.LaserBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** What needs a world: the table's whole loop, from a pipe's insert to the result in its outputs. */
@GameTestHolder(LaserAssembly.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TableGameTests {

    // Height 1 is the platform's stone floor: everything stands on it, at height 2.

    private static final ResourceLocation SADDLE = ResourceLocation.fromNamespaceAndPath(LaserAssembly.MOD_ID, "saddle");

    /** The whole loop: a pipe's insert, a laser's beam, the result in the outputs. */
    @GameTest(template = "platform", timeoutTicks = 200)
    public static void aLaserCraftsFromFilteredInputs(GameTestHelper helper) {
        BlockPos tablePos = new BlockPos(2, 2, 3);
        helper.setBlock(tablePos, ModBlocks.ASSEMBLY_TABLE.get());
        helper.setBlock(new BlockPos(2, 2, 1), ModBlocks.LASER.get().defaultBlockState().setValue(LaserBlock.FACING, Direction.SOUTH));
        AssemblyTableBlockEntity table = helper.getBlockEntity(tablePos);
        LaserBlockEntity laser = helper.getBlockEntity(new BlockPos(2, 2, 1));
        table.toggle(SADDLE);
        IItemHandler items = table.automation();

        // Two crafts' worth of leather (input_crafts = 2), and nothing the ticked recipe does not use.
        helper.assertTrue(insert(items, Items.LEATHER, 64) == 58, "inputs should take exactly 6 leather");
        helper.assertTrue(insert(items, Items.DIRT, 1) == 1, "inputs took dirt");
        insert(items, Items.IRON_INGOT, 1);
        insert(items, Items.STRING, 2);
        // At intensity 5, 100 progress a tick: 4000 FE in 40 ticks.
        laser.energy().receiveEnergy(10_000, false);
        laser.stepIntensity(4);

        // One craft and only one: the single iron ingot pays for it, the second set of leather stays.
        helper.succeedWhen(() -> {
            helper.assertTrue(count(items, Items.SADDLE) == 1, "no saddle yet");
            helper.assertTrue(helper.getTick() >= 40 && helper.getTick() < 100, "crafted at tick " + helper.getTick());
            helper.assertTrue(count(items, Items.LEATHER) == 3, "leather consumed: " + (6 - count(items, Items.LEATHER)));
            helper.assertTrue(count(items, Items.IRON_INGOT) == 0 && count(items, Items.STRING) == 0, "ingredients left over");
        });
    }

    /** A recipe in steps: each laser fires only on the step its lens fits, one after the other. */
    @GameTest(template = "platform", timeoutTicks = 300)
    public static void lensesTakeTurnsOnARecipeInSteps(GameTestHelper helper) {
        BlockPos tablePos = new BlockPos(2, 2, 3);
        helper.setBlock(tablePos, ModBlocks.ASSEMBLY_TABLE.get());
        AssemblyTableBlockEntity table = helper.getBlockEntity(tablePos);
        LaserBlockEntity cutter = laser(helper, new BlockPos(2, 2, 1), Direction.SOUTH, "cut");
        LaserBlockEntity welder = laser(helper, new BlockPos(0, 2, 3), Direction.EAST, "weld");
        table.toggle(ResourceLocation.fromNamespaceAndPath(LaserAssembly.MOD_ID, "trident"));
        IItemHandler items = table.automation();
        insert(items, Items.IRON_INGOT, 3);
        insert(items, Items.PRISMARINE_SHARD, 4);
        insert(items, Items.PRISMARINE_CRYSTALS, 2);

        helper.runAfterDelay(10, () -> helper.assertTrue(cutter.firing() && !welder.firing(), "the cut step should take the cutter alone"));
        helper.succeedWhen(() -> helper.assertTrue(count(items, Items.TRIDENT) == 1, "no trident yet"));
    }

    /** A laser at full intensity, with a full buffer and a lens of {@code beam}. */
    private static LaserBlockEntity laser(GameTestHelper helper, BlockPos pos, Direction facing, String beam) {
        helper.setBlock(pos, ModBlocks.LASER.get().defaultBlockState().setValue(LaserBlock.FACING, facing));
        LaserBlockEntity laser = helper.getBlockEntity(pos);
        laser.energy().receiveEnergy(LaserBlockEntity.ENERGY_CAPACITY, false);
        laser.stepIntensity(LaserBlockEntity.MAX_INTENSITY);
        laser.lens().insertItem(0, LensItem.of(ResourceKey.create(BeamType.REGISTRY,
                ResourceLocation.fromNamespaceAndPath(LaserAssembly.MOD_ID, beam))), false);
        return laser;
    }

    @GameTest(template = "platform", timeoutTicks = 60)
    public static void aBlockInTheWayHidesTheTable(GameTestHelper helper) {
        helper.setBlock(new BlockPos(2, 2, 3), ModBlocks.ASSEMBLY_TABLE.get());
        helper.setBlock(new BlockPos(2, 2, 2), Blocks.STONE);
        helper.setBlock(new BlockPos(2, 2, 1), ModBlocks.LASER.get().defaultBlockState().setValue(LaserBlock.FACING, Direction.SOUTH));
        LaserBlockEntity laser = helper.getBlockEntity(new BlockPos(2, 2, 1));
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(laser.target() == null, "laser aims through stone at " + laser.target());
            helper.succeed();
        });
    }

    /** Returns what did not go in. */
    private static int insert(IItemHandler handler, Item item, int count) {
        return ItemHandlerHelper.insertItemStacked(handler, new ItemStack(item, count), false).getCount();
    }

    private static int count(IItemHandler handler, Item item) {
        int total = 0;
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack stack = handler.getStackInSlot(i);
            if (stack.is(item)) total += stack.getCount();
        }
        return total;
    }

    private TableGameTests() {}
}

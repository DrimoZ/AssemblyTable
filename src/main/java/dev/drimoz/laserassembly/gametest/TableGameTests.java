package dev.drimoz.laserassembly.gametest;

import dev.drimoz.laserassembly.LaserAssembly;
import dev.drimoz.laserassembly.content.table.AssemblyTableBlockEntity;
import dev.drimoz.laserassembly.registry.ModBlocks;
import dev.drimoz.laserassembly.content.laser.LaserBlock;
import dev.drimoz.laserassembly.content.laser.LaserBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.capabilities.Capabilities;
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

        // One craft's worth of leather for automation (input_crafts = 1), and nothing the ticked recipe does not use.
        helper.assertTrue(insert(items, Items.LEATHER, 64) == 61, "automation should put in exactly 3 leather");
        helper.assertTrue(insert(items, Items.DIRT, 1) == 1, "inputs took dirt");
        // A player is not capped: three more by hand, the second craft's worth.
        helper.assertTrue(ItemHandlerHelper.insertItemStacked(table.inputs(), new ItemStack(Items.LEATHER, 3), false).isEmpty(),
                "a player should fill past the automation cap");
        insert(items, Items.IRON_INGOT, 1);
        insert(items, Items.STRING, 2);
        // At intensity 5, 100 progress a tick: 4000 FE in 40 ticks.
        laser.energy().receiveEnergy(10_000, false);
        laser.stepIntensity(4);

        // One craft and only one: the single iron ingot pays for it, the second set of leather stays.
        helper.succeedWhen(() -> {
            helper.assertTrue(count(items, Items.SADDLE) == 1, "no saddle yet");
            helper.assertTrue(helper.getTick() >= 40 && helper.getTick() < 100, "crafted at tick " + helper.getTick());
            helper.assertTrue(table.lasers().contains(laser.getBlockPos()), "the table does not list its laser");
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

    /**
     * One laser runs a recipe in steps on its own: a Barrel to switch lenses, a Regulator to pick an
     * intensity that does not heat. And it takes one augment of each group.
     */
    @GameTest(template = "platform", timeoutTicks = 400)
    public static void augmentsLetOneLaserRunEveryStep(GameTestHelper helper) {
        BlockPos tablePos = new BlockPos(2, 2, 3);
        helper.setBlock(tablePos, ModBlocks.ASSEMBLY_TABLE.get());
        AssemblyTableBlockEntity table = helper.getBlockEntity(tablePos);
        LaserBlockEntity laser = laser(helper, new BlockPos(2, 2, 1), Direction.SOUTH, "cut");
        helper.assertTrue(laser.augments().insertItem(0, item("regulator_augment"), false).isEmpty(), "regulator refused");
        helper.assertTrue(laser.augments().insertItem(1, item("range_augment_1"), false).isEmpty(), "range refused");
        helper.assertTrue(!laser.augments().isItemValid(0, item("range_augment_2")), "a second range should be refused");
        laser.augments().setStackInSlot(1, item("barrel_augment"));
        // Both lenses in the Barrel and the lens slot empty: an empty slot is a raw beam, which fits a
        // typed step at a tenth of its power, and once took the turn of the right lenses.
        laser.lens().setStackInSlot(0, ItemStack.EMPTY);
        laser.barrel().insertItem(0, lens("cut"), false);
        laser.barrel().insertItem(1, lens("weld"), false);

        table.toggle(ResourceLocation.fromNamespaceAndPath(LaserAssembly.MOD_ID, "trident"));
        IItemHandler items = table.automation();
        insert(items, Items.IRON_INGOT, 3);
        insert(items, Items.PRISMARINE_SHARD, 4);
        insert(items, Items.PRISMARINE_CRYSTALS, 2);
        // The trident costs 10 000 FE of work, the laser's whole buffer: keep it topped up.
        helper.onEachTick(() -> laser.energy().receiveEnergy(LaserBlockEntity.ENERGY_CAPACITY, false));

        helper.succeedWhen(() -> {
            helper.assertTrue(count(items, Items.TRIDENT) == 1, "no trident yet");
            // Default thresholds for an intensity-1 step: heating above 3, so the Regulator settles on 3.
            helper.assertTrue(laser.effects().regulator(), "regulator not counted");
        });
    }

    private static ItemStack lens(String beam) {
        return item(beam + "_lens");
    }

    private static ItemStack item(String name) {
        return new ItemStack(BuiltInRegistries.ITEM.get(LaserAssembly.id(name)));
    }

    /** A laser at full intensity, with a full buffer and a lens of {@code beam}. */
    private static LaserBlockEntity laser(GameTestHelper helper, BlockPos pos, Direction facing, String beam) {
        helper.setBlock(pos, ModBlocks.LASER.get().defaultBlockState().setValue(LaserBlock.FACING, facing));
        LaserBlockEntity laser = helper.getBlockEntity(pos);
        laser.energy().receiveEnergy(LaserBlockEntity.ENERGY_CAPACITY, false);
        laser.stepIntensity(LaserBlockEntity.MAX_INTENSITY);
        laser.lens().insertItem(0, lens(beam), false);
        return laser;
    }

    /** A hopper on an input face feeds any ticked recipe, not only the one shown on the socket. */
    @GameTest(template = "platform", timeoutTicks = 100)
    public static void aHopperFeedsEveryTickedRecipe(GameTestHelper helper) {
        BlockPos tablePos = new BlockPos(2, 2, 2);
        helper.setBlock(tablePos, ModBlocks.ASSEMBLY_TABLE.get());
        AssemblyTableBlockEntity table = helper.getBlockEntity(tablePos);
        table.toggle(SADDLE);
        table.toggle(ResourceLocation.fromNamespaceAndPath(LaserAssembly.MOD_ID, "trident"));
        // Both, the default, then closed, then input.
        table.cycleFace(Direction.WEST);
        table.cycleFace(Direction.WEST);
        helper.setBlock(tablePos.west(), Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.EAST));
        HopperBlockEntity hopper = helper.getBlockEntity(tablePos.west());
        hopper.setItem(0, new ItemStack(Items.PRISMARINE_SHARD, 4));

        helper.succeedWhen(() -> helper.assertTrue(count(table.automation(), Items.PRISMARINE_SHARD) == 4,
                "the table took " + count(table.automation(), Items.PRISMARINE_SHARD) + " of 4 shards"));
    }

    /** A closed face shows pipes nothing; auto export pushes the outputs into the inventory beside it. */
    @GameTest(template = "platform", timeoutTicks = 60)
    public static void facesCloseAndAutoExportPushes(GameTestHelper helper) {
        BlockPos tablePos = new BlockPos(2, 2, 2);
        helper.setBlock(tablePos, ModBlocks.ASSEMBLY_TABLE.get());
        helper.setBlock(tablePos.north(), Blocks.CHEST);
        AssemblyTableBlockEntity table = helper.getBlockEntity(tablePos);
        BlockPos absolute = helper.absolutePos(tablePos);

        // Both, the default, and one click on: closed.
        table.cycleFace(Direction.SOUTH);
        helper.assertTrue(helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, absolute, Direction.SOUTH) == null,
                "a closed face still shows an inventory");

        table.outputs().setStackInSlot(0, new ItemStack(Items.SADDLE));
        table.toggleAutoExport();
        IItemHandler chest = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, absolute.north(), Direction.SOUTH);
        helper.succeedWhen(() -> {
            helper.assertTrue(count(chest, Items.SADDLE) == 1, "the saddle is still in the table");
            helper.assertTrue(table.outputs().getStackInSlot(0).isEmpty(), "the saddle is in both places");
        });
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

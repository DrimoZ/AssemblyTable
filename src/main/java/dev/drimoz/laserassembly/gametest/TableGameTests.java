package dev.drimoz.laserassembly.gametest;

import dev.drimoz.laserassembly.LaserAssembly;
import dev.drimoz.laserassembly.content.table.AssemblyTableBlockEntity;
import dev.drimoz.laserassembly.registry.ModBlocks;
import dev.drimoz.laserassembly.content.laser.LaserBlock;
import dev.drimoz.laserassembly.content.laser.LaserBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import dev.drimoz.laserassembly.core.Assembly;
import dev.drimoz.laserassembly.core.Tuning;
import dev.drimoz.laserassembly.content.ItemSlots;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.nbt.TagParser;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.drimoz.laserassembly.recipe.AssemblyRecipe;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.entity.EntityType;
import java.util.function.Consumer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import java.util.List;

/**
 * What needs a world: the table's whole loop, from a pipe's insert to the result in its outputs.
 *
 * <p>26.1 has no {@code @GameTest}: each test is a function registered here plus a
 * {@code test_instance} file of the same name, which gives its structure and timeout. Registered
 * through {@link RegisterEvent}, not {@code TestFunctionLoader}, which runs before any mod is
 * constructed - Portable Beacons found every test "missing" that way.
 */
@EventBusSubscriber(modid = LaserAssembly.MOD_ID)
public final class TableGameTests {

    @SubscribeEvent
    static void register(RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, helper -> {
            helper.register(LaserAssembly.id("a_laser_crafts_from_filtered_inputs"), (Consumer<GameTestHelper>) TableGameTests::aLaserCraftsFromFilteredInputs);
            helper.register(LaserAssembly.id("lenses_take_turns_on_a_recipe_in_steps"), (Consumer<GameTestHelper>) TableGameTests::lensesTakeTurnsOnARecipeInSteps);
            helper.register(LaserAssembly.id("augments_let_one_laser_run_every_step"), (Consumer<GameTestHelper>) TableGameTests::augmentsLetOneLaserRunEveryStep);
            helper.register(LaserAssembly.id("a_hopper_feeds_every_ticked_recipe"), (Consumer<GameTestHelper>) TableGameTests::aHopperFeedsEveryTickedRecipe);
            helper.register(LaserAssembly.id("faces_close_and_auto_export_pushes"), (Consumer<GameTestHelper>) TableGameTests::facesCloseAndAutoExportPushes);
            helper.register(LaserAssembly.id("a_block_in_the_way_hides_the_table"), (Consumer<GameTestHelper>) TableGameTests::aBlockInTheWayHidesTheTable);
            helper.register(LaserAssembly.id("a_save_keeps_everything"), (Consumer<GameTestHelper>) TableGameTests::aSaveKeepsEverything);
            helper.register(LaserAssembly.id("a_vanished_recipe_stops_the_table_and_keeps_its_items"), (Consumer<GameTestHelper>) TableGameTests::aVanishedRecipeStopsTheTableAndKeepsItsItems);
            helper.register(LaserAssembly.id("a_recipe_that_shrank_starts_over"), (Consumer<GameTestHelper>) TableGameTests::aRecipeThatShrankStartsOver);
            helper.register(LaserAssembly.id("overheating_stops_without_destroying"), (Consumer<GameTestHelper>) TableGameTests::overheatingStopsWithoutDestroying);
            helper.register(LaserAssembly.id("an_item_fitting_two_ingredients_goes_where_it_is_needed"), (Consumer<GameTestHelper>) TableGameTests::anItemFittingTwoIngredientsGoesWhereItIsNeeded);
            helper.register(LaserAssembly.id("a_save_from_1_21_1_loads_whole"), (Consumer<GameTestHelper>) TableGameTests::aSaveFrom1211LoadsWhole);
        });
    }

    // Height 1 is the platform's stone floor: everything stands on it, at height 2.

    private static final Identifier SADDLE = Identifier.fromNamespaceAndPath(LaserAssembly.MOD_ID, "saddle");

    /** The whole loop: a pipe's insert, a laser's beam, the result in the outputs. */
    public static void aLaserCraftsFromFilteredInputs(GameTestHelper helper) {
        BlockPos tablePos = new BlockPos(2, 2, 3);
        helper.setBlock(tablePos, ModBlocks.ASSEMBLY_TABLE.get());
        helper.setBlock(new BlockPos(2, 2, 1), ModBlocks.LASER.get().defaultBlockState().setValue(LaserBlock.FACING, Direction.SOUTH));
        AssemblyTableBlockEntity table = helper.getBlockEntity(tablePos, AssemblyTableBlockEntity.class);
        LaserBlockEntity laser = helper.getBlockEntity(new BlockPos(2, 2, 1), LaserBlockEntity.class);
        table.toggle(SADDLE);
        ResourceHandler<ItemResource> items = table.automation();

        // Automation fills one stack of leather and no more, and nothing the ticked recipe does not use.
        helper.assertTrue(insert(items, Items.LEATHER, 64) == 0, "automation should take a full stack");
        helper.assertTrue(insert(items, Items.LEATHER, 1) == 1, "automation went past one stack");
        helper.assertTrue(insert(items, Items.DIRT, 1) == 1, "inputs took dirt");
        // A player is not held to it.
        helper.assertTrue(insert(table.inputs(), Items.LEATHER, 3) == 0,
                "a player should fill past the automation cap");
        insert(items, Items.IRON_INGOT, 1);
        insert(items, Items.STRING, 2);
        // At intensity 5, 100 progress a tick: 4000 FE in 40 ticks.
        laser.energy().set(LaserBlockEntity.ENERGY_CAPACITY);
        laser.stepIntensity(4);

        // One craft and only one: the single iron ingot pays for it, the rest of the leather stays.
        helper.succeedWhen(() -> {
            helper.assertTrue(count(items, Items.SADDLE) == 1, "no saddle yet");
            helper.assertTrue(helper.getTick() >= 40 && helper.getTick() < 100, "crafted at tick " + helper.getTick());
            helper.assertTrue(table.lasers().contains(laser.getBlockPos()), "the table does not list its laser");
            helper.assertTrue(count(items, Items.LEATHER) == 64, "leather consumed: " + (67 - count(items, Items.LEATHER)));
            helper.assertTrue(count(items, Items.IRON_INGOT) == 0 && count(items, Items.STRING) == 0, "ingredients left over");
        });
    }

    /** A recipe in steps: each laser fires only on the step its lens fits, one after the other. */
    public static void lensesTakeTurnsOnARecipeInSteps(GameTestHelper helper) {
        BlockPos tablePos = new BlockPos(2, 2, 3);
        helper.setBlock(tablePos, ModBlocks.ASSEMBLY_TABLE.get());
        AssemblyTableBlockEntity table = helper.getBlockEntity(tablePos, AssemblyTableBlockEntity.class);
        LaserBlockEntity cutter = laser(helper, new BlockPos(2, 2, 1), Direction.SOUTH, "cut");
        LaserBlockEntity welder = laser(helper, new BlockPos(0, 2, 3), Direction.EAST, "weld");
        table.toggle(Identifier.fromNamespaceAndPath(LaserAssembly.MOD_ID, "trident"));
        ResourceHandler<ItemResource> items = table.automation();
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
    public static void augmentsLetOneLaserRunEveryStep(GameTestHelper helper) {
        BlockPos tablePos = new BlockPos(2, 2, 3);
        helper.setBlock(tablePos, ModBlocks.ASSEMBLY_TABLE.get());
        AssemblyTableBlockEntity table = helper.getBlockEntity(tablePos, AssemblyTableBlockEntity.class);
        LaserBlockEntity laser = laser(helper, new BlockPos(2, 2, 1), Direction.SOUTH, "cut");
        helper.assertTrue(put(laser.augments(), 0, item("regulator_augment")), "regulator refused");
        helper.assertTrue(put(laser.augments(), 1, item("range_augment_1")), "range refused");
        helper.assertTrue(!laser.augments().isValid(0, ItemResource.of(item("range_augment_2"))), "a second range should be refused");
        laser.augments().set(1, item("barrel_augment"));
        // Both lenses in the Barrel and the lens slot empty: an empty slot is a raw beam, which fits a
        // typed step at a tenth of its power, and once took the turn of the right lenses.
        laser.lens().set(0, ItemStack.EMPTY);
        put(laser.barrel(), 0, lens("cut"));
        put(laser.barrel(), 1, lens("weld"));

        table.toggle(Identifier.fromNamespaceAndPath(LaserAssembly.MOD_ID, "trident"));
        ResourceHandler<ItemResource> items = table.automation();
        insert(items, Items.IRON_INGOT, 3);
        insert(items, Items.PRISMARINE_SHARD, 4);
        insert(items, Items.PRISMARINE_CRYSTALS, 2);
        // The trident costs 10 000 FE of work, the laser's whole buffer: keep it topped up.
        helper.onEachTick(() -> laser.energy().set(LaserBlockEntity.ENERGY_CAPACITY));

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
        return new ItemStack(BuiltInRegistries.ITEM.getValue(LaserAssembly.id(name)));
    }

    /** A laser at full intensity, with a full buffer and a lens of {@code beam}. */
    private static LaserBlockEntity laser(GameTestHelper helper, BlockPos pos, Direction facing, String beam) {
        helper.setBlock(pos, ModBlocks.LASER.get().defaultBlockState().setValue(LaserBlock.FACING, facing));
        LaserBlockEntity laser = helper.getBlockEntity(pos, LaserBlockEntity.class);
        laser.energy().set(LaserBlockEntity.ENERGY_CAPACITY);
        laser.stepIntensity(LaserBlockEntity.MAX_INTENSITY);
        put(laser.lens(), 0, lens(beam));
        return laser;
    }

    /** A hopper on an input face feeds any ticked recipe, not only the one shown on the socket. */
    public static void aHopperFeedsEveryTickedRecipe(GameTestHelper helper) {
        BlockPos tablePos = new BlockPos(2, 2, 2);
        helper.setBlock(tablePos, ModBlocks.ASSEMBLY_TABLE.get());
        AssemblyTableBlockEntity table = helper.getBlockEntity(tablePos, AssemblyTableBlockEntity.class);
        table.toggle(SADDLE);
        table.toggle(Identifier.fromNamespaceAndPath(LaserAssembly.MOD_ID, "trident"));
        // Both, the default, then closed, then input.
        table.cycleFace(Direction.WEST);
        table.cycleFace(Direction.WEST);
        helper.setBlock(tablePos.west(), Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.EAST));
        HopperBlockEntity hopper = helper.getBlockEntity(tablePos.west(), HopperBlockEntity.class);
        hopper.setItem(0, new ItemStack(Items.PRISMARINE_SHARD, 4));

        helper.succeedWhen(() -> helper.assertTrue(count(table.automation(), Items.PRISMARINE_SHARD) == 4,
                "the table took " + count(table.automation(), Items.PRISMARINE_SHARD) + " of 4 shards"));
    }

    /** A closed face shows pipes nothing; auto export pushes the outputs into the inventory beside it. */
    public static void facesCloseAndAutoExportPushes(GameTestHelper helper) {
        BlockPos tablePos = new BlockPos(2, 2, 2);
        helper.setBlock(tablePos, ModBlocks.ASSEMBLY_TABLE.get());
        helper.setBlock(tablePos.north(), Blocks.CHEST);
        AssemblyTableBlockEntity table = helper.getBlockEntity(tablePos, AssemblyTableBlockEntity.class);
        BlockPos absolute = helper.absolutePos(tablePos);

        // Both, the default, and one click on: closed.
        table.cycleFace(Direction.SOUTH);
        helper.assertTrue(helper.getLevel().getCapability(Capabilities.Item.BLOCK, absolute, Direction.SOUTH) == null,
                "a closed face still shows an inventory");

        table.outputs().set(0, new ItemStack(Items.SADDLE));
        table.toggleAutoExport();
        ResourceHandler<ItemResource> chest = helper.getLevel().getCapability(Capabilities.Item.BLOCK, absolute.north(), Direction.SOUTH);
        helper.succeedWhen(() -> {
            helper.assertTrue(count(chest, Items.SADDLE) == 1, "the saddle is still in the table");
            helper.assertTrue(table.outputs().stack(0).isEmpty(), "the saddle is in both places");
        });
    }

    public static void aBlockInTheWayHidesTheTable(GameTestHelper helper) {
        helper.setBlock(new BlockPos(2, 2, 3), ModBlocks.ASSEMBLY_TABLE.get());
        helper.setBlock(new BlockPos(2, 2, 2), Blocks.STONE);
        helper.setBlock(new BlockPos(2, 2, 1), ModBlocks.LASER.get().defaultBlockState().setValue(LaserBlock.FACING, Direction.SOUTH));
        LaserBlockEntity laser = helper.getBlockEntity(new BlockPos(2, 2, 1), LaserBlockEntity.class);
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(laser.target() == null, "laser aims through stone at " + laser.target());
            helper.succeed();
        });
    }

    /** Everything a table holds and is set to comes back from a save as it was: items, selection, work, settings. */
    public static void aSaveKeepsEverything(GameTestHelper helper) {
        AssemblyTableBlockEntity table = saddleTable(helper);
        laser(helper, new BlockPos(2, 2, 1), Direction.SOUTH, "cut");
        table.outputs().set(0, new ItemStack(Items.SADDLE));
        table.cycleFace(Direction.WEST);
        table.toggleAutoImport();

        // Halfway through the 40-tick saddle, at intensity 5: progress and heat are both under way.
        helper.runAfterDelay(20, () -> {
            HolderLookup.Provider registries = helper.getLevel().registryAccess();
            CompoundTag saved = table.saveWithoutMetadata(registries);
            helper.assertTrue(saved.getDoubleOr("progress", 0) > 0 && saved.getDoubleOr("heat", 0) > 0, "nothing under way to save: " + saved);
            AssemblyTableBlockEntity copy = new AssemblyTableBlockEntity(table.getBlockPos(), table.getBlockState());
            copy.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, registries, saved));
            CompoundTag reloaded = copy.saveWithoutMetadata(registries);
            helper.assertTrue(reloaded.equals(saved), "lost in the round trip:\n" + saved + "\n" + reloaded);
            helper.succeed();
        });
    }

    /**
     * A recipe gone after a datapack change: the table keeps its id, stops, and gives nothing out -
     * the ingredients stay where they are, and the laser holds fire.
     */
    public static void aVanishedRecipeStopsTheTableAndKeepsItsItems(GameTestHelper helper) {
        AssemblyTableBlockEntity table = saddleTable(helper);
        Identifier gone = LaserAssembly.id("gone");
        reload(helper, table, tag -> {
            tag.store("selected", Identifier.CODEC.listOf(), List.of(gone));
            tag.putString("current", gone.toString());
        });
        LaserBlockEntity laser = laser(helper, new BlockPos(2, 2, 1), Direction.SOUTH, "cut");

        // Twice the saddle's 40 ticks: long enough to have crafted it, had the table run.
        helper.runAfterDelay(80, () -> {
            helper.assertTrue(table.selected().contains(gone), "the table forgot the recipe");
            helper.assertTrue(!laser.firing(), "the laser fires at a table with nothing to make");
            assertSaddleIngredients(helper, table);
            helper.assertTrue(count(table.outputs(), Items.SADDLE) == 0, "crafted without a recipe");
            helper.assertTrue(helper.getEntities(EntityType.ITEM).isEmpty(), "the table threw items out");
            helper.succeed();
        });
    }

    /** The same id back with fewer steps than the table had reached: it starts over, where it once crashed the server. */
    public static void aRecipeThatShrankStartsOver(GameTestHelper helper) {
        AssemblyTableBlockEntity table = saddleTable(helper);
        reload(helper, table, tag -> {
            tag.putString("current", SADDLE.toString());
            tag.putInt("step", 2);
        });
        laser(helper, new BlockPos(2, 2, 1), Direction.SOUTH, "cut");
        helper.succeedWhen(() -> helper.assertTrue(count(table.outputs(), Items.SADDLE) == 1, "no saddle yet"));
    }

    /** At max heat the table stops, the laser holds fire, and nothing is lost: not an item, not the progress. */
    public static void overheatingStopsWithoutDestroying(GameTestHelper helper) {
        AssemblyTableBlockEntity table = saddleTable(helper);
        // One tick short of the maximum: intensity 5 on an intensity-1 step heats 2 a tick, less 1 dissipated.
        reload(helper, table, tag -> {
            tag.putDouble("heat", Tuning.DEFAULTS.maxHeat() - 1);
            tag.putDouble("progress", 1000);
        });
        LaserBlockEntity laser = laser(helper, new BlockPos(2, 2, 1), Direction.SOUTH, "cut");

        helper.succeedWhen(() -> {
            helper.assertTrue(table.status() == Assembly.Status.OVERHEATED, "not overheated: " + table.status());
            helper.assertTrue(!laser.firing(), "the laser fires at an overheated table");
            helper.assertTrue(table.stepProgress() > 0, "the progress was lost");
            helper.assertTrue(helper.getBlockState(new BlockPos(2, 2, 3)).is(ModBlocks.ASSEMBLY_TABLE.get()), "the table is gone");
            assertSaddleIngredients(helper, table);
            helper.assertTrue(helper.getEntities(EntityType.ITEM).isEmpty(), "the table threw items out");
        });
    }

    /**
     * A table and a laser exactly as 1.21.1 saved them - NeoForge's old item handler layout, the energy
     * as a bare int - which is what a world carried over to 26.1 hands them: nothing they held may go.
     */
    public static void aSaveFrom1211LoadsWhole(GameTestHelper helper) {
        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        BlockPos tablePos = new BlockPos(2, 2, 3);
        helper.setBlock(tablePos, ModBlocks.ASSEMBLY_TABLE.get());
        AssemblyTableBlockEntity table = helper.getBlockEntity(tablePos, AssemblyTableBlockEntity.class);
        table.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, registries, snbt("""
                {inputs: {Items: [{Slot: 0, id: "minecraft:leather", count: 3}], Size: 12},
                 outputs: {Items: [{Slot: 1, id: "minecraft:saddle", count: 1}], Size: 4},
                 selected: ["laserassembly:saddle"], current: "laserassembly:saddle", working: 0b, lasers: [L; 0L],
                 step: 0, progress: 120.0d, heat: 3.0d, overheated: 0b, faces: 1, auto_import: 1b, auto_export: 0b,
                 switched_on: 1b, redstone_mode: 2, threshold: 7}""")));
        helper.assertTrue(count(table.inputs(), Items.LEATHER) == 3, "the inputs came back empty");
        helper.assertTrue(table.outputs().stack(1).is(Items.SADDLE), "the output moved or went");
        helper.assertTrue(table.selected().contains(SADDLE) && SADDLE.equals(table.current()), "the recipe was forgotten");
        CompoundTag saved = table.saveWithoutMetadata(registries);
        helper.assertTrue(saved.getDoubleOr("progress", 0) == 120 && saved.getDoubleOr("heat", 0) == 3, "the work was lost: " + saved);
        helper.assertTrue(saved.getIntOr("threshold", 0) == 7 && saved.getIntOr("redstone_mode", 0) == 2 && saved.getIntOr("faces", 0) == 1
                && saved.getBooleanOr("auto_import", false), "the settings were lost: " + saved);

        BlockPos laserPos = new BlockPos(2, 2, 1);
        helper.setBlock(laserPos, ModBlocks.LASER.get().defaultBlockState().setValue(LaserBlock.FACING, Direction.SOUTH));
        LaserBlockEntity laser = helper.getBlockEntity(laserPos, LaserBlockEntity.class);
        laser.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, registries, snbt("""
                {energy: 4000, intensity: 3, lens: {Items: [{Slot: 0, id: "laserassembly:cut_lens", count: 1}], Size: 1},
                 augments: {Items: [], Size: 2}, barrel: {Items: [], Size: 3}}""")));
        helper.assertTrue(laser.energy().getAmountAsInt() == 4000, "the laser lost its energy");
        helper.assertTrue(laser.intensity() == 3, "the laser lost its intensity");
        helper.assertTrue(ItemStack.isSameItem(laser.lens().stack(0), lens("cut")), "the laser lost its lens");
        helper.succeed();
    }

    private static CompoundTag snbt(String text) {
        try {
            return TagParser.parseCompoundFully(text);
        } catch (CommandSyntaxException e) {
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * Matching goes through vanilla's recipe-book solver: a greedy pass gives the oak to the first
     * ingredient and finds none left for the second. A GameTest rather than JUnit since 26.1, which
     * gives items their components only once a world has loaded its resources.
     */
    public static void anItemFittingTwoIngredientsGoesWhereItIsNeeded(GameTestHelper helper) {
        var ops = RegistryOps.create(JsonOps.INSTANCE, helper.getLevel().registryAccess());
        AssemblyRecipe recipe = AssemblyRecipe.CODEC.codec().parse(ops, JsonParser.parseString("""
                { "ingredients": [
                    { "ingredient": ["minecraft:oak_planks", "minecraft:birch_planks"] },
                    { "ingredient": "minecraft:oak_planks" } ],
                  "result": { "id": "minecraft:stick" }, "energy": 1 }""")).getOrThrow();
        helper.assertTrue(recipe.matches(new AssemblyRecipe.Input(List.of(new ItemStack(Items.OAK_PLANKS), new ItemStack(Items.BIRCH_PLANKS))), helper.getLevel()),
                "oak and birch should fit, the oak where only oak will do");
        helper.assertFalse(recipe.matches(new AssemblyRecipe.Input(List.of(new ItemStack(Items.BIRCH_PLANKS, 2))), helper.getLevel()),
                "two birch fit only one of the two");
        helper.succeed();
    }

    /** A table at (2, 2, 3) with the saddle ticked and one craft's ingredients in it. */
    private static AssemblyTableBlockEntity saddleTable(GameTestHelper helper) {
        BlockPos tablePos = new BlockPos(2, 2, 3);
        helper.setBlock(tablePos, ModBlocks.ASSEMBLY_TABLE.get());
        AssemblyTableBlockEntity table = helper.getBlockEntity(tablePos, AssemblyTableBlockEntity.class);
        table.toggle(SADDLE);
        insert(table.inputs(), Items.LEATHER, 3);
        insert(table.inputs(), Items.IRON_INGOT, 1);
        insert(table.inputs(), Items.STRING, 2);
        return table;
    }

    private static void assertSaddleIngredients(GameTestHelper helper, AssemblyTableBlockEntity table) {
        helper.assertTrue(count(table.inputs(), Items.LEATHER) == 3 && count(table.inputs(), Items.IRON_INGOT) == 1
                && count(table.inputs(), Items.STRING) == 2, "the ingredients moved");
    }

    /** What a save edited by a datapack change, or a world left in that state, loads as. */
    private static void reload(GameTestHelper helper, AssemblyTableBlockEntity table, Consumer<CompoundTag> edit) {
        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        CompoundTag tag = table.saveWithoutMetadata(registries);
        edit.accept(tag);
        table.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, registries, tag));
    }

    /** Returns what did not go in. */
    private static int insert(ResourceHandler<ItemResource> handler, Item item, int count) {
        return count - ResourceHandlerUtil.insertStacking(handler, ItemResource.of(item), count, null);
    }

    /** Into one slot, as a player's click would: whether it all went in. */
    private static boolean put(ItemSlots slots, int index, ItemStack stack) {
        try (Transaction transaction = Transaction.openRoot()) {
            boolean all = slots.insert(index, ItemResource.of(stack), stack.getCount(), transaction) == stack.getCount();
            transaction.commit();
            return all;
        }
    }

    private static int count(ResourceHandler<ItemResource> handler, Item item) {
        int total = 0;
        for (int i = 0; i < handler.size(); i++) {
            if (handler.getResource(i).is(item)) total += handler.getAmountAsInt(i);
        }
        return total;
    }

    private TableGameTests() {}
}

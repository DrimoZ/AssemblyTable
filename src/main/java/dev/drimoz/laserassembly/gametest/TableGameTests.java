package dev.drimoz.laserassembly.gametest;

import dev.drimoz.laserassembly.LaserAssembly;
import dev.drimoz.laserassembly.content.table.AssemblyTableBlockEntity;
import dev.drimoz.laserassembly.registry.ModBlocks;
import net.minecraft.core.BlockPos;
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

    private static final ResourceLocation SADDLE = ResourceLocation.fromNamespaceAndPath(LaserAssembly.MOD_ID, "saddle");

    @GameTest(template = "platform", timeoutTicks = 400)
    public static void craftsOnDirectPowerFromFilteredInputs(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, ModBlocks.ASSEMBLY_TABLE.get());
        AssemblyTableBlockEntity table = helper.getBlockEntity(pos);
        table.toggle(SADDLE);
        IItemHandler items = table.automation();

        // Two crafts' worth of leather (input_crafts = 2), and nothing the ticked recipe does not use.
        helper.assertTrue(insert(items, Items.LEATHER, 64) == 58, "inputs should take exactly 6 leather");
        helper.assertTrue(insert(items, Items.DIRT, 1) == 1, "inputs took dirt");
        insert(items, Items.IRON_INGOT, 1);
        insert(items, Items.STRING, 2);
        table.energy().receiveEnergy(20_000, false);

        // One craft and only one: the single iron ingot pays for it, the second set of leather stays.
        helper.succeedWhen(() -> {
            helper.assertTrue(count(items, Items.SADDLE) == 1, "no saddle yet");
            // 4000 FE at 1 intensity x 20 FE: no sooner than 200 ticks.
            helper.assertTrue(helper.getTick() >= 200, "crafted too fast, at tick " + helper.getTick());
            helper.assertTrue(count(items, Items.LEATHER) == 3, "leather consumed: " + (6 - count(items, Items.LEATHER)));
            helper.assertTrue(count(items, Items.IRON_INGOT) == 0 && count(items, Items.STRING) == 0, "ingredients left over");
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

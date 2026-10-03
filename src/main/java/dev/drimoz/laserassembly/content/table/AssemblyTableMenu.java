package dev.drimoz.laserassembly.content.table;

import dev.drimoz.laserassembly.gui.GuiMetrics;
import dev.drimoz.laserassembly.recipe.AssemblyRecipe;
import dev.drimoz.laserassembly.registry.ModBlocks;
import dev.drimoz.laserassembly.registry.ModRecipes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.items.SlotItemHandler;

import java.util.Comparator;
import java.util.List;

public class AssemblyTableMenu extends AbstractContainerMenu {

    public static final int INPUT_COLUMNS = 3;
    public static final int GRID_COLUMN = 5;
    public static final int GRID_COLUMNS = 4;
    public static final int GRID_ROWS = 3;
    /** Inputs fill rows 0-3; the recipe grid rows 0-2, the outputs row 3 beneath it. */
    public static final int CONTENT_BOTTOM = GuiMetrics.row(4);

    private static final int TABLE_SLOTS = AssemblyTableBlockEntity.INPUTS + AssemblyTableBlockEntity.OUTPUTS;

    private final AssemblyTableBlockEntity table;
    private final ContainerData data;
    private final ContainerLevelAccess access;
    private final List<RecipeHolder<AssemblyRecipe>> recipes;

    /** Client side: the table is the one the client already has at that position. */
    public AssemblyTableMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(id, inventory, (AssemblyTableBlockEntity) inventory.player.level().getBlockEntity(buf.readBlockPos()),
                new SimpleContainerData(AssemblyTableBlockEntity.DATA_COUNT));
    }

    public AssemblyTableMenu(int id, Inventory inventory, AssemblyTableBlockEntity table, ContainerData data) {
        super(ModBlocks.ASSEMBLY_TABLE_MENU.get(), id);
        this.table = table;
        this.data = data;
        this.access = ContainerLevelAccess.create(table.getLevel(), table.getBlockPos());
        this.recipes = recipes(table.getLevel());

        for (int i = 0; i < AssemblyTableBlockEntity.INPUTS; i++) {
            addSlot(new SlotItemHandler(table.inputs(), i,
                    GuiMetrics.column(1 + i % INPUT_COLUMNS) + 1, GuiMetrics.row(i / INPUT_COLUMNS) + 1) {
                /**
                 * Clicks and shift-clicks ask the slot how much fits, not the handler: without this,
                 * the two-crafts cap held for pipes and not for players.
                 */
                @Override
                public int getMaxStackSize(ItemStack stack) {
                    return Math.min(super.getMaxStackSize(stack), getItem().getCount() + table.room(stack));
                }
            });
        }
        for (int i = 0; i < AssemblyTableBlockEntity.OUTPUTS; i++) {
            addSlot(new SlotItemHandler(table.outputs(), i, GuiMetrics.column(GRID_COLUMN + i) + 1, GuiMetrics.row(3) + 1) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return false;
                }
            });
        }
        int inventoryY = GuiMetrics.inventoryY(CONTENT_BOTTOM);
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, 9 + row * 9 + col, GuiMetrics.column(col) + 1, inventoryY + row * GuiMetrics.SLOT));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, GuiMetrics.column(col) + 1, GuiMetrics.hotbarY(CONTENT_BOTTOM)));
        }
        addDataSlots(data);
    }

    /**
     * Every assembling recipe, sorted by id: the same list on both sides, which is what lets a click
     * travel as a plain index through {@link #clickMenuButton}.
     */
    // ponytail: a /reload landing between a click and its arrival can shift the index by one; a
    // payload carrying the recipe id if that ever bites.
    public static List<RecipeHolder<AssemblyRecipe>> recipes(Level level) {
        return level.getRecipeManager().getAllRecipesFor(ModRecipes.ASSEMBLING.get()).stream()
                .sorted(Comparator.comparing(holder -> holder.id().toString()))
                .toList();
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id < 0 || id >= recipes.size()) return false;
        table.toggle(recipes.get(id).id());
        return true;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, ModBlocks.ASSEMBLY_TABLE.get());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        boolean moved = index < TABLE_SLOTS
                ? moveItemStackTo(stack, TABLE_SLOTS, slots.size(), true)
                : moveItemStackTo(stack, 0, AssemblyTableBlockEntity.INPUTS, false);
        if (!moved) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return original;
    }

    public AssemblyTableBlockEntity table() {
        return table;
    }

    public List<RecipeHolder<AssemblyRecipe>> recipes() {
        return recipes;
    }

    public int data(int index) {
        return data.get(index);
    }
}

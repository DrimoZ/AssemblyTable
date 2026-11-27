package dev.drimoz.laserassembly.content.table;

import dev.drimoz.laserassembly.gui.GuiMetrics;
import dev.drimoz.laserassembly.recipe.AssemblyRecipe;
import dev.drimoz.laserassembly.registry.ModBlocks;
import dev.drimoz.laserassembly.registry.ModRecipes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.neoforged.neoforge.transfer.item.ResourceHandlerSlot;

import java.util.List;

public class AssemblyTableMenu extends AbstractContainerMenu {

    /**
     * Under the recipe band (socket, name, step bar), the work area: heat in column 0, inputs 4x3 in
     * columns 1-4, an arrow, and the outputs 2x2 in columns 7-8, centred on the inputs' height.
     */
    // Clear of the step strip: touching, it and the heat gauge read as one bent bar.
    public static final int WORK_TOP = GuiMetrics.CONTENT_TOP + 40;
    public static final int INPUT_COLUMNS = 4;
    public static final int OUTPUT_COLUMN = 7;
    public static final int OUTPUT_TOP = WORK_TOP + GuiMetrics.SLOT / 2;
    public static final int CONTENT_BOTTOM = WORK_TOP + 3 * GuiMetrics.SLOT;

    // Button ids past the recipes, which take 0 to their count: the faces tab's controls.
    public static final int BUTTON_FACE = 1_000_000;
    public static final int BUTTON_AUTO_IMPORT = 2_000_000;
    public static final int BUTTON_AUTO_EXPORT = 2_000_001;
    /** Then the lasers tab: laser {@code i} steps down at BUTTON_LASER + 2i, up at + 2i + 1; every laser at BUTTON_LASERS_ALL + 0 or 1. */
    public static final int BUTTON_LASER = 3_000_000;
    public static final int BUTTON_LASERS_ALL = 4_000_000;
    /** The control tab: the switch, a redstone mode by ordinal, the threshold down or up. */
    public static final int BUTTON_SWITCH = 5_000_000;
    public static final int BUTTON_MODE = 5_000_010;
    public static final int BUTTON_THRESHOLD = 5_000_020;

    private static final int TABLE_SLOTS = AssemblyTableBlockEntity.INPUTS + AssemblyTableBlockEntity.OUTPUTS;

    private final AssemblyTableBlockEntity table;
    private final ContainerData data;
    private final ContainerLevelAccess access;
    private final List<RecipeHolder<AssemblyRecipe>> recipes;

    /** Client side: the table is the one the client already has at that position. */
    public AssemblyTableMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(id, inventory, onClient(inventory.player.level(), buf.readBlockPos()), new SimpleContainerData(AssemblyTableBlockEntity.DATA_COUNT));
    }

    /**
     * The client's table at {@code pos}, or a stand-in when it has none there: a missing one made the
     * menu throw and disconnected the player. The slots are filled by the server's sync either way.
     */
    private static AssemblyTableBlockEntity onClient(Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof AssemblyTableBlockEntity table) return table;
        AssemblyTableBlockEntity standIn = new AssemblyTableBlockEntity(pos, ModBlocks.ASSEMBLY_TABLE.get().defaultBlockState());
        standIn.setLevel(level);
        return standIn;
    }

    public AssemblyTableMenu(int id, Inventory inventory, AssemblyTableBlockEntity table, ContainerData data) {
        super(ModBlocks.ASSEMBLY_TABLE_MENU.get(), id);
        this.table = table;
        this.data = data;
        this.access = ContainerLevelAccess.create(table.getLevel(), table.getBlockPos());
        this.recipes = recipes(table.getLevel());

        for (int i = 0; i < AssemblyTableBlockEntity.INPUTS; i++) {
            addSlot(new ResourceHandlerSlot(table.inputs(), table.inputs()::set, i,
                    GuiMetrics.column(1 + i % INPUT_COLUMNS) + 1, WORK_TOP + (i / INPUT_COLUMNS) * GuiMetrics.SLOT + 1));
        }
        for (int i = 0; i < AssemblyTableBlockEntity.OUTPUTS; i++) {
            addSlot(new ResourceHandlerSlot(table.outputs(), table.outputs()::set, i, GuiMetrics.column(OUTPUT_COLUMN + i % 2) + 1, OUTPUT_TOP + (i / 2) * GuiMetrics.SLOT + 1) {
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
     * travel as a plain index through {@link #clickMenuButton}. Each side takes it once, when the menu
     * opens, and the screen only reads that copy, so a /reload while it is open cannot shift an index:
     * the server's recipe update reaches the client after the open packet, or before it, never between.
     */
    public static List<RecipeHolder<AssemblyRecipe>> recipes(Level level) {
        return ModRecipes.all(level);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id >= BUTTON_FACE && id < BUTTON_FACE + Direction.values().length) {
            table.cycleFace(Direction.values()[id - BUTTON_FACE]);
            return true;
        }
        if (id == BUTTON_SWITCH) table.toggleSwitch();
        else if (id >= BUTTON_MODE && id < BUTTON_MODE + AssemblyTableBlockEntity.RedstoneMode.values().length) {
            table.setRedstoneMode(AssemblyTableBlockEntity.RedstoneMode.values()[id - BUTTON_MODE]);
        } else if (id == BUTTON_THRESHOLD || id == BUTTON_THRESHOLD + 1) table.stepThreshold(id == BUTTON_THRESHOLD ? -1 : 1);
        else         if (id == BUTTON_LASERS_ALL || id == BUTTON_LASERS_ALL + 1) table.stepLaser(-1, id == BUTTON_LASERS_ALL ? -1 : 1);
        else if (id >= BUTTON_LASER && id < BUTTON_LASERS_ALL) table.stepLaser((id - BUTTON_LASER) / 2, (id - BUTTON_LASER) % 2 == 0 ? -1 : 1);
        else if (id == BUTTON_AUTO_IMPORT) table.toggleAutoImport();
        else if (id == BUTTON_AUTO_EXPORT) table.toggleAutoExport();
        else if (id >= 0 && id < recipes.size()) table.toggle(recipes.get(id).id().identifier());
        else return false;
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

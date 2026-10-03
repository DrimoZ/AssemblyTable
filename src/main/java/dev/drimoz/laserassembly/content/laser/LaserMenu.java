package dev.drimoz.laserassembly.content.laser;

import dev.drimoz.laserassembly.gui.GuiMetrics;
import dev.drimoz.laserassembly.registry.ModBlocks;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.SlotItemHandler;

/** The lens slot in column 1, the player's inventory below. Buttons 0 and 1 step the intensity. */
public class LaserMenu extends AbstractContainerMenu {

    /** Content rows 0-2: intensity, cost, state. */
    public static final int CONTENT_BOTTOM = GuiMetrics.row(3);
    public static final int LENS_X = GuiMetrics.column(1) + 1;
    public static final int LENS_Y = GuiMetrics.row(1) + 1;

    private final LaserBlockEntity laser;
    private final ContainerData data;
    private final ContainerLevelAccess access;

    public LaserMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(id, inventory, (LaserBlockEntity) inventory.player.level().getBlockEntity(buf.readBlockPos()),
                new SimpleContainerData(LaserBlockEntity.DATA_COUNT));
    }

    public LaserMenu(int id, Inventory inventory, LaserBlockEntity laser, ContainerData data) {
        super(ModBlocks.LASER_MENU.get(), id);
        this.laser = laser;
        this.data = data;
        this.access = ContainerLevelAccess.create(laser.getLevel(), laser.getBlockPos());

        addSlot(new SlotItemHandler(laser.lens(), 0, LENS_X, LENS_Y));
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

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id != 0 && id != 1) return false;
        laser.stepIntensity(id == 0 ? -1 : 1);
        return true;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, ModBlocks.LASER.get());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        boolean moved = index == 0 ? moveItemStackTo(stack, 1, slots.size(), true) : moveItemStackTo(stack, 0, 1, false);
        if (!moved) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return original;
    }

    public LaserBlockEntity laser() {
        return laser;
    }

    public int data(int index) {
        return data.get(index);
    }
}

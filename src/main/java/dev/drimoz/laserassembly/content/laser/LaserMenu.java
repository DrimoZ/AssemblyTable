package dev.drimoz.laserassembly.content.laser;

import dev.drimoz.laserassembly.gui.GuiMetrics;
import dev.drimoz.laserassembly.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.item.ResourceHandlerSlot;

/**
 * The lens in column 1; on the row beneath, the augments in columns 1-2 and the Barrel's lenses in
 * columns 6-8; the player's inventory below. Buttons 0 and 1 step the intensity.
 */
public class LaserMenu extends AbstractContainerMenu {

    /** Content rows 0-2: intensity, beam, state; row 3 the augments and the Barrel. */
    public static final int CONTENT_BOTTOM = GuiMetrics.row(4);
    public static final int AUGMENT_COLUMN = 1;
    public static final int BARREL_COLUMN = 6;
    public static final int EXTRA_Y = GuiMetrics.row(3) + 1;
    private static final int LASER_SLOTS = 1 + LaserBlockEntity.AUGMENT_SLOTS + LaserBlockEntity.BARREL_SLOTS;
    public static final int LENS_X = GuiMetrics.column(1) + 1;
    public static final int LENS_Y = GuiMetrics.row(1) + 1;

    private final LaserBlockEntity laser;
    private final ContainerData data;
    private final ContainerLevelAccess access;

    public LaserMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(id, inventory, onClient(inventory.player.level(), buf.readBlockPos()), new SimpleContainerData(LaserBlockEntity.DATA_COUNT));
    }

    /**
     * The client's laser at {@code pos}, or a stand-in when it has none there: a missing one made the
     * menu throw and disconnected the player. The slots are filled by the server's sync either way.
     */
    private static LaserBlockEntity onClient(Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof LaserBlockEntity laser) return laser;
        LaserBlockEntity standIn = new LaserBlockEntity(pos, ModBlocks.LASER.get().defaultBlockState());
        standIn.setLevel(level);
        return standIn;
    }

    public LaserMenu(int id, Inventory inventory, LaserBlockEntity laser, ContainerData data) {
        super(ModBlocks.LASER_MENU.get(), id);
        this.laser = laser;
        this.data = data;
        this.access = ContainerLevelAccess.create(laser.getLevel(), laser.getBlockPos());

        addSlot(new ResourceHandlerSlot(laser.lens(), laser.lens()::set, 0, LENS_X, LENS_Y));
        for (int i = 0; i < LaserBlockEntity.AUGMENT_SLOTS; i++) {
            addSlot(new ResourceHandlerSlot(laser.augments(), laser.augments()::set, i, GuiMetrics.column(AUGMENT_COLUMN + i) + 1, EXTRA_Y));
        }
        for (int i = 0; i < LaserBlockEntity.BARREL_SLOTS; i++) {
            addSlot(new ResourceHandlerSlot(laser.barrel(), laser.barrel()::set, i, GuiMetrics.column(BARREL_COLUMN + i) + 1, EXTRA_Y));
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
        boolean moved = index < LASER_SLOTS ? moveItemStackTo(stack, LASER_SLOTS, slots.size(), true)
                : moveItemStackTo(stack, 0, LASER_SLOTS, false);
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

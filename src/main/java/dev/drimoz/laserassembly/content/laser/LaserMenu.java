package dev.drimoz.laserassembly.content.laser;

import dev.drimoz.laserassembly.registry.ModBlocks;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;

/** No slots yet: lens and augments arrive with their items. Buttons 0 and 1 step the intensity. */
public class LaserMenu extends AbstractContainerMenu {

    private final LaserBlockEntity laser;
    private final ContainerData data;
    private final ContainerLevelAccess access;

    public LaserMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(id, (LaserBlockEntity) inventory.player.level().getBlockEntity(buf.readBlockPos()),
                new SimpleContainerData(LaserBlockEntity.DATA_COUNT));
    }

    public LaserMenu(int id, LaserBlockEntity laser, ContainerData data) {
        super(ModBlocks.LASER_MENU.get(), id);
        this.laser = laser;
        this.data = data;
        this.access = ContainerLevelAccess.create(laser.getLevel(), laser.getBlockPos());
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
        return ItemStack.EMPTY;
    }

    public LaserBlockEntity laser() {
        return laser;
    }

    public int data(int index) {
        return data.get(index);
    }
}

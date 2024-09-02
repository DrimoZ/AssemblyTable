package com.drimoz.assembly.table.slots;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;

public class InputSlot extends SlotItemHandler {

    public InputSlot(ItemStackHandler itemHandler, int index, int xPosition, int yPosition) {
        super(itemHandler, index, xPosition, yPosition);
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return true; // Allows all items for now, but can be restricted
    }

    @Override
    public boolean mayPickup(Player playerIn) {
        return true; // Allows the player to take items
    }
}

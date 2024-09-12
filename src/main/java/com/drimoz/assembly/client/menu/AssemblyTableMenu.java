package com.drimoz.assembly.client.menu;

import com.drimoz.assembly.Assembly;
import com.drimoz.assembly.core.tileentity.AssemblyTableBlockEntity;
import com.drimoz.assembly.client.menu.slots.InputSlot;
import com.drimoz.assembly.client.menu.slots.OutputSlot;
import com.drimoz.assembly.client.menu.slots.ShadowSlot;
import com.drimoz.assembly.core.utils.Constants;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.ForgeCapabilities;

import java.util.List;

import static com.drimoz.assembly.core.utils.Constants.Menu.*;



public class AssemblyTableMenu extends AbstractContainerMenu {

    // Private Properties

    private final int TE_INVENTORY_SLOT_COUNT;

    private final AssemblyTableBlockEntity blockEntity;
    private final ContainerLevelAccess access;

    // Life Cycle

    public AssemblyTableMenu(
            int containerId,
            Inventory playerInventory,
            AssemblyTableBlockEntity blockEntity
    ) {
        super(Assembly.ASSEMBLY_TABLE_MENU.get(), containerId);

        this.blockEntity = blockEntity;
        this.access = ContainerLevelAccess.create(blockEntity.getLevel(), blockEntity.getBlockPos());
        this.TE_INVENTORY_SLOT_COUNT = AssemblyTableBlockEntity.TOTAL_SLOTS;


        // Hotbar Slots
        for (int i = 0; i < HOTBAR_SLOT_COUNT; ++i) {
            this.addSlot(new Slot(playerInventory, i, HOTBAR_FIRST_SLOT_X + i * 18, HOTBAR_FIRST_SLOT_Y));
        }

        // Player Inventory Slots
        for (int i = 0; i < PLAYER_INVENTORY_ROW_COUNT; ++i) {
            for (int l = 0; l < PLAYER_INVENTORY_COLUMN_COUNT; ++l) {
                this.addSlot(new Slot(playerInventory, l + i * 9 + 9, PLAYER_INVENTORY_FIRST_SLOT_X + l * 18, PLAYER_INVENTORY_FIRST_SLOT_Y + i * 18));
            }
        }

        addAssemblyTableSlots();
    }


    // Interface - Inherited

    @Override
    public boolean stillValid(Player player) {
        return stillValid(this.access, player, Assembly.ASSEMBLY_TABLE.get());
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {

        int min = TE_INVENTORY_FIRST_SLOT_INDEX + AssemblyTableBlockEntity.INPUT_SLOT_COUNT;
        int max = min + AssemblyTableBlockEntity.SHADOW_SLOT_COUNT;

        if (slotId >= min && slotId < max) {
            blockEntity.toggleRecipe(slotId);
        }

        super.clicked(slotId, button, clickType, player);
    }

    // Inner Work

    @Override
    public ItemStack quickMoveStack(Player playerIn, int pIndex) {
        Slot sourceSlot = slots.get(pIndex);
        if (sourceSlot == null || !sourceSlot.hasItem()) return ItemStack.EMPTY;  //EMPTY_ITEM
        ItemStack sourceStack = sourceSlot.getItem();
        ItemStack copyOfSourceStack = sourceStack.copy();

        // Check if the slot clicked is one of the vanilla container slots
        if (pIndex < VANILLA_FIRST_SLOT_INDEX + VANILLA_SLOT_COUNT) {
            // This is a vanilla container slot so merge the stack into the tile inventory
            if (!blockEntity.isValidInput(sourceStack) || !moveItemStackTo(sourceStack, TE_INVENTORY_FIRST_SLOT_INDEX, TE_INVENTORY_FIRST_SLOT_INDEX
                    + TE_INVENTORY_SLOT_COUNT, false)) {
                return ItemStack.EMPTY;  // EMPTY_ITEM
            }
        } else if (pIndex < TE_INVENTORY_FIRST_SLOT_INDEX + TE_INVENTORY_SLOT_COUNT) {
            // This is a TE slot so merge the stack into the players inventory
            if (!moveItemStackTo(sourceStack, VANILLA_FIRST_SLOT_INDEX, VANILLA_FIRST_SLOT_INDEX + VANILLA_SLOT_COUNT, false)) {
                return ItemStack.EMPTY;
            }
        } else {
            System.out.println("Invalid slotIndex:" + pIndex);
            return ItemStack.EMPTY;
        }
        // If stack size == 0 (the entire stack was moved) set slot contents to null
        if (sourceStack.getCount() == 0) {
            sourceSlot.set(ItemStack.EMPTY);
        } else {
            sourceSlot.setChanged();
        }
        sourceSlot.onTake(playerIn, sourceStack);
        return copyOfSourceStack;
    }

    private void addAssemblyTableSlots() {
        blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER).ifPresent(handler -> {
            int slotIndex = 0;

            // Input Slots

            for (int row = 0; row < INPUT_ROW_COUNT; ++row) {
                for (int col = 0; col < INPUT_COLUMN_COUNT; ++col) {
                    this.addSlot(new InputSlot(handler, slotIndex, INPUT_FIRST_SLOT_X + col * 18, INPUT_FIRST_SLOT_Y + row * 18));
                    slotIndex++;
                }
            }

            // Shadow Slots

            for (int row = 0; row < SHADOW_ROW_COUNT; ++row) {
                for (int col = 0; col < SHADOW_COLUMN_COUNT; ++col) {
                    this.addSlot(new ShadowSlot(handler, slotIndex, SHADOW_FIRST_SLOT_X + col * 18, SHADOW_FIRST_SLOT_Y + row * 18));
                    slotIndex++;
                }
            }

            // Output Slots

            for (int row = 0; row < OUTPUT_ROW_COUNT; ++row) {
                for (int col = 0; col < OUTPUT_COLUMN_COUNT; ++col) {
                    this.addSlot(new OutputSlot(handler, slotIndex, OUTPUT_FIRST_SLOT_X + col * 18, OUTPUT_FIRST_SLOT_Y + row * 18));
                    slotIndex++;
                }
            }
        });

        //blockEntity.getCapability(Assembly.SHADOW_CAP).ifPresent(handler -> {
        //    int slotIndex = AssemblyTableBlockEntity.INPUT_SLOT_COUNT;

        //

        //    for (int row = 0; row < SHADOW_ROW_COUNT; ++row) {
        //        for (int col = 0; col < SHADOW_COLUMN_COUNT; ++col) {
        //            this.addSlot(new ShadowSlot(handler, slotIndex, SHADOW_FIRST_SLOT_X + col * 18, SHADOW_FIRST_SLOT_Y + row * 18));
        //            slotIndex++;
        //        }
        //    }
        //});
    }

    // Accessors

    public AssemblyTableBlockEntity getBlockEntity() {
        return blockEntity;
    }
}



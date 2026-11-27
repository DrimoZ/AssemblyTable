package dev.drimoz.laserassembly.content;

import net.minecraft.core.NonNullList;
import net.minecraft.world.ItemStackWithSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;

/**
 * A block entity's item slots on NeoForge's transfer API, read as stacks the way the block entities
 * think of them.
 *
 * <p>Saved in the layout 1.21.1's {@code ItemStackHandler} wrote - {@code {Items: [{Slot, id, count,
 * components}], Size}} - rather than the handler's own: a table or laser carried over from a 1.21.1
 * world would otherwise load empty, and its items would be gone.
 */
public class ItemSlots extends ItemStacksResourceHandler {

    public ItemSlots(int size) {
        super(size);
    }

    /** The stack in slot {@code index} itself, not a copy, so ticks can read it without allocating: never change it. */
    public ItemStack stack(int index) {
        return stacks.get(index);
    }

    public void save(ValueOutput output, String key) {
        ValueOutput own = output.child(key);
        ValueOutput.TypedOutputList<ItemStackWithSlot> items = own.list("Items", ItemStackWithSlot.CODEC);
        for (int i = 0; i < stacks.size(); i++) {
            if (!stacks.get(i).isEmpty()) items.add(new ItemStackWithSlot(i, stacks.get(i)));
        }
        own.putInt("Size", stacks.size());
    }

    /** Straight into the slots, without {@link #onContentsChanged}: loading is not a change. */
    public void load(ValueInput input, String key) {
        NonNullList<ItemStack> loaded = NonNullList.withSize(stacks.size(), ItemStack.EMPTY);
        // 1.21.1 wrote the slot as an int, vanilla's codec reads a byte: both are numbers to it.
        for (ItemStackWithSlot item : input.childOrEmpty(key).listOrEmpty("Items", ItemStackWithSlot.CODEC)) {
            if (item.isValidInContainer(loaded.size())) loaded.set(item.slot(), item.stack());
        }
        setStacks(loaded);
    }

    /** For tests and stand-ins: overwrites a slot as {@link #set} does. */
    public void set(int index, ItemStack stack) {
        set(index, ItemResource.of(stack), stack.getCount());
    }
}

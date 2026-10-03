package dev.drimoz.laserassembly.content.table;

import dev.drimoz.laserassembly.LaserAssembly;
import dev.drimoz.laserassembly.ServerConfig;
import dev.drimoz.laserassembly.core.Assembly;
import dev.drimoz.laserassembly.core.Beams;
import dev.drimoz.laserassembly.core.Rotation;
import dev.drimoz.laserassembly.core.Step;
import dev.drimoz.laserassembly.core.Tuning;
import dev.drimoz.laserassembly.recipe.AssemblyRecipe;
import dev.drimoz.laserassembly.registry.ModBlocks;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.StackedContents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.wrapper.CombinedInvWrapper;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The table. Ingredients stay in their slots until a craft completes, so breaking the table or
 * switching recipe gives everything back with no bookkeeping: conservation needs no hidden state.
 */
public class AssemblyTableBlockEntity extends BlockEntity {

    public static final int INPUTS = 12;
    public static final int OUTPUTS = 4;
    private static final int ENERGY_CAPACITY = 20_000;

    private final ItemStackHandler inputs = new ItemStackHandler(INPUTS) {
        @Override
        protected void onContentsChanged(int slot) {
            contentsChanged();
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return room(stack) > 0;
        }

        /** Only what the ticked recipes use, and at most a few crafts of it: a pipe cannot clog the table. */
        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            int room = room(stack);
            if (room <= 0) return stack;
            if (stack.getCount() <= room) return super.insertItem(slot, stack, simulate);
            ItemStack rest = super.insertItem(slot, stack.copyWithCount(room), simulate);
            return stack.copyWithCount(stack.getCount() - room + rest.getCount());
        }
    };

    private final ItemStackHandler outputs = new ItemStackHandler(OUTPUTS) {
        @Override
        protected void onContentsChanged(int slot) {
            contentsChanged();
        }
    };

    /** What pipes and hoppers see: inputs take, outputs give, nothing else. */
    private final IItemHandler automation = new CombinedInvWrapper(new OneWay(inputs, true), new OneWay(outputs, false));

    private final DirectPower energy = new DirectPower();

    private final List<ResourceLocation> selected = new ArrayList<>();
    private @Nullable ResourceLocation current;
    private final Assembly assembly = new Assembly();
    private Assembly.Status status = Assembly.Status.IDLE;

    /** Lasers add here during their tick; the table spends it on its own and clears it. */
    private final Beams pending = new Beams();

    // Caches, rebuilt rather than saved.
    private @Nullable AssemblyRecipe currentRecipe;
    private @Nullable Tuning stepsTuning;
    private List<Step> steps = List.of();
    private boolean contentsDirty = true;
    private boolean coolingDirty = true;
    private double cooling;

    public AssemblyTableBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.ASSEMBLY_TABLE_ENTITY.get(), pos, state);
    }

    // Ticking

    void serverTick() {
        Tuning tuning = ServerConfig.tuning();
        if (coolingDirty) {
            coolingDirty = false;
            cooling = measureCooling();
        }

        AssemblyRecipe recipe = current == null ? null : lookup(current);
        // A /reload swaps every recipe object: re-check the current one against the new definition.
        if (recipe != currentRecipe || tuning != stepsTuning) contentsDirty = true;
        if (contentsDirty) {
            contentsDirty = false;
            choose(canRun(recipe) ? current : Rotation.next(selected, current, id -> canRun(lookup(id))));
            recipe = currentRecipe;
        }

        if (recipe == null || getBlockState().getValue(AssemblyTableBlock.POWERED)) {
            pending.clear();
            status = Assembly.Status.IDLE;
            return;
        }

        if (ServerConfig.directPower() && !assembly.overheated()) {
            double intensity = ServerConfig.directIntensity();
            if (energy.spend((int) Math.ceil(intensity * tuning.fePerIntensity()))) pending.add(null, intensity);
        }

        status = assembly.tick(steps, pending, cooling, tuning);
        pending.clear();
        if (status == Assembly.Status.DONE) {
            Craft craft = plan(recipe);
            if (craft != null) craft.apply();
            // BuildCraft's turn-taking: after each craft, the next ticked recipe that can run.
            choose(Rotation.next(selected, current, id -> canRun(lookup(id))));
        }
        setChanged();
    }

    /** Lasers and other beam sources call this during their own tick. */
    public void receiveBeam(@Nullable ResourceLocation beam, double intensity) {
        pending.add(beam, intensity);
    }

    /** Whether a beam would do anything here this tick - lasers hold fire otherwise. */
    public boolean wantsBeams() {
        return current != null && !assembly.overheated() && !getBlockState().getValue(AssemblyTableBlock.POWERED);
    }

    /** Also how the caches catch up with a reload or a config change: the same id keeps its progress. */
    private void choose(@Nullable ResourceLocation next) {
        if (next == null || !next.equals(current)) assembly.reset();
        current = next;
        currentRecipe = next == null ? null : lookup(next);
        stepsTuning = ServerConfig.tuning();
        steps = currentRecipe == null ? List.of() : currentRecipe.steps(stepsTuning);
    }

    // Recipes

    private @Nullable AssemblyRecipe lookup(ResourceLocation id) {
        if (level == null) return null;
        return level.getRecipeManager().byKey(id)
                .map(holder -> holder.value() instanceof AssemblyRecipe recipe ? recipe : null)
                .orElse(null);
    }

    private boolean canRun(@Nullable AssemblyRecipe recipe) {
        if (recipe == null) return false;
        try {
            recipe.steps(ServerConfig.tuning());
        } catch (IllegalArgumentException e) {
            // The server config moved a default under a value this recipe sets explicitly.
            LaserAssembly.LOGGER.warn("Recipe unusable with the current server config: {}", e.getMessage());
            return false;
        }
        return plan(recipe) != null;
    }

    /** Tick or untick a recipe. The inputs keep what they hold; they only stop taking more of it. */
    public void toggle(ResourceLocation id) {
        if (!selected.remove(id)) selected.add(id);
        contentsDirty = true;
        setChanged();
    }

    public List<ResourceLocation> selected() {
        return selected;
    }

    /**
     * How many more of {@code stack} the inputs take: enough for {@code input_crafts} crafts of the
     * ticked recipe that uses the most of it, minus what is already there.
     */
    private int room(ItemStack stack) {
        int perCraft = 0;
        for (ResourceLocation id : selected) {
            AssemblyRecipe recipe = lookup(id);
            if (recipe == null) continue;
            int uses = 0;
            for (var sized : recipe.ingredients()) if (sized.test(stack)) uses += sized.count();
            perCraft = Math.max(perCraft, uses);
        }
        int held = 0;
        for (int i = 0; i < INPUTS; i++) {
            ItemStack slot = inputs.getStackInSlot(i);
            if (ItemStack.isSameItemSameComponents(slot, stack)) held += slot.getCount();
        }
        return perCraft * ServerConfig.inputCrafts() - held;
    }

    // Crafting

    /** One craft: how many to take from each input slot, and what comes out. */
    private record Craft(AssemblyTableBlockEntity table, int[] take, List<ItemStack> out) {
        void apply() {
            for (int i = 0; i < INPUTS; i++) {
                if (take[i] > 0) table.inputs.extractItem(i, take[i], false);
            }
            for (ItemStack stack : out) ItemHandlerHelper.insertItemStacked(table.outputs, stack, false);
        }
    }

    /**
     * The craft the inputs can pay for, or null - including when its result and leftovers (the bucket
     * of a milk bucket) would not all fit in the outputs: room is reserved before work starts.
     */
    private @Nullable Craft plan(AssemblyRecipe recipe) {
        StackedContents contents = new StackedContents();
        List<ItemStack> items = new ArrayList<>(INPUTS);
        for (int i = 0; i < INPUTS; i++) {
            ItemStack stack = inputs.getStackInSlot(i);
            items.add(stack);
            contents.accountStack(stack);
        }
        IntList picked = new IntArrayList();
        if (!contents.canCraft(recipe, picked)) return null;

        int[] take = new int[INPUTS];
        List<ItemStack> out = new ArrayList<>();
        out.add(recipe.assemble(new AssemblyRecipe.Input(items), level.registryAccess()));
        for (int item : picked) {
            for (int i = 0; i < INPUTS; i++) {
                ItemStack stack = items.get(i);
                if (take[i] < stack.getCount() && StackedContents.getStackingIndex(stack) == item) {
                    take[i]++;
                    ItemStack leftover = stack.getCraftingRemainingItem();
                    if (!leftover.isEmpty()) out.add(leftover);
                    break;
                }
            }
        }

        ItemStackHandler trial = new ItemStackHandler(OUTPUTS);
        for (int i = 0; i < OUTPUTS; i++) trial.setStackInSlot(i, outputs.getStackInSlot(i).copy());
        for (ItemStack stack : out) {
            if (!ItemHandlerHelper.insertItemStacked(trial, stack.copy(), false).isEmpty()) return null;
        }
        return new Craft(this, take, out);
    }

    // Surroundings

    void neighboursChanged() {
        coolingDirty = true;
    }

    /** Every face but the top, which belongs to the assembly on display. Each block counts its best coolant. */
    private double measureCooling() {
        var coolants = level.registryAccess().registryOrThrow(Coolant.REGISTRY);
        double total = 0;
        for (Direction side : Direction.values()) {
            if (side == Direction.UP) continue;
            var block = level.getBlockState(worldPosition.relative(side)).getBlockHolder();
            double best = 0;
            for (Coolant coolant : coolants) if (coolant.blocks().contains(block)) best = Math.max(best, coolant.cooling());
            total += best;
        }
        return total;
    }

    private void contentsChanged() {
        contentsDirty = true;
        setChanged();
    }

    void dropContents() {
        for (ItemStackHandler handler : new ItemStackHandler[]{inputs, outputs}) {
            for (int i = 0; i < handler.getSlots(); i++) {
                Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), handler.getStackInSlot(i));
            }
        }
    }

    // Capabilities

    public IItemHandler automation() {
        return automation;
    }

    public @Nullable IEnergyStorage energy() {
        return ServerConfig.directPower() ? energy : null;
    }

    public Assembly assembly() {
        return assembly;
    }

    public Assembly.Status status() {
        return status;
    }

    // Persistence

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("inputs", inputs.serializeNBT(registries));
        tag.put("outputs", outputs.serializeNBT(registries));
        tag.put("energy", energy.serializeNBT(registries));
        ListTag ids = new ListTag();
        for (ResourceLocation id : selected) ids.add(StringTag.valueOf(id.toString()));
        tag.put("selected", ids);
        if (current != null) tag.putString("current", current.toString());
        tag.putInt("step", assembly.step());
        tag.putDouble("progress", assembly.progress());
        tag.putDouble("heat", assembly.heat());
        tag.putBoolean("overheated", assembly.overheated());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        inputs.deserializeNBT(registries, tag.getCompound("inputs"));
        outputs.deserializeNBT(registries, tag.getCompound("outputs"));
        if (tag.contains("energy")) energy.deserializeNBT(registries, tag.get("energy"));
        selected.clear();
        for (Tag id : tag.getList("selected", Tag.TAG_STRING)) {
            ResourceLocation parsed = ResourceLocation.tryParse(id.getAsString());
            if (parsed != null) selected.add(parsed);
        }
        // Kept even if the recipe is gone after a datapack change: the table waits for it to come back.
        current = tag.contains("current") ? ResourceLocation.tryParse(tag.getString("current")) : null;
        assembly.restore(tag.getInt("step"), tag.getDouble("progress"), tag.getDouble("heat"), tag.getBoolean("overheated"));
    }

    // Handlers

    /** FE fed straight into the table. Nothing may pull it back out. */
    private final class DirectPower extends EnergyStorage {
        DirectPower() {
            super(ENERGY_CAPACITY, ENERGY_CAPACITY, 0);
        }

        @Override
        public int receiveEnergy(int toReceive, boolean simulate) {
            int received = super.receiveEnergy(toReceive, simulate);
            if (received > 0 && !simulate) setChanged();
            return received;
        }

        boolean spend(int amount) {
            if (energy < amount) return false;
            energy -= amount;
            return true;
        }
    }

    /** A handler seen from outside: insert-only or extract-only. */
    private record OneWay(IItemHandlerModifiable inner, boolean insert) implements IItemHandlerModifiable {
        @Override
        public int getSlots() {
            return inner.getSlots();
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return inner.getStackInSlot(slot);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return insert ? inner.insertItem(slot, stack, simulate) : stack;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return insert ? ItemStack.EMPTY : inner.extractItem(slot, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            return inner.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return insert && inner.isItemValid(slot, stack);
        }

        @Override
        public void setStackInSlot(int slot, ItemStack stack) {
            inner.setStackInSlot(slot, stack);
        }
    }
}

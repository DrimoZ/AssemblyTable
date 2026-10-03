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
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.entity.player.StackedContents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
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
public class AssemblyTableBlockEntity extends BlockEntity implements MenuProvider {

    public static final int INPUTS = 12;
    public static final int OUTPUTS = 4;

    // What the screen reads, through ContainerData: synced while the menu is open, and only then.
    // Values travel as shorts, hence permille rather than raw heat and progress.
    public static final int DATA_HEAT = 0;
    public static final int DATA_PROGRESS = 1;
    public static final int DATA_STEP = 2;
    public static final int DATA_STEPS = 3;
    public static final int DATA_STATUS = 4;
    public static final int DATA_COUNT = 5;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case DATA_HEAT -> (int) (1000 * assembly.heat() / ServerConfig.tuning().maxHeat());
                case DATA_PROGRESS -> steps.isEmpty() ? 0 : (int) (1000 * assembly.progress() / steps.get(assembly.step()).energy());
                case DATA_STEP -> assembly.step();
                case DATA_STEPS -> steps.size();
                case DATA_STATUS -> status.ordinal();
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

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

    private final List<ResourceLocation> selected = new ArrayList<>();
    private @Nullable ResourceLocation current;
    /** Synced, for the assembly on display. */
    private boolean working;
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
            setWorking(false);
            if (assembly.heat() > 0) {
                assembly.cool(cooling, tuning);
                setChanged();
            }
            return;
        }

        status = assembly.tick(steps, pending, cooling, tuning);
        pending.clear();
        setWorking(status == Assembly.Status.WORKING || status == Assembly.Status.SLOWED || status == Assembly.Status.DONE);
        if (status == Assembly.Status.DONE) {
            Craft craft = plan(recipe);
            if (craft != null) {
                craft.apply();
                finished();
            }
            // BuildCraft's turn-taking: after each craft, the next ticked recipe that can run.
            choose(Rotation.next(selected, current, id -> canRun(lookup(id))));
        }
        setChanged();
    }

    /** Sent to clients only when it flips: the assembly on the plate turns while it is true. */
    private void setWorking(boolean now) {
        if (now == working) return;
        working = now;
        sync();
    }

    /** Once per craft, from the server: a burst over the plate and a chime. Not a timer. */
    private void finished() {
        if (!(level instanceof ServerLevel server)) return;
        double x = worldPosition.getX() + 0.5, y = worldPosition.getY() + 1.3, z = worldPosition.getZ() + 0.5;
        server.sendParticles(ParticleTypes.END_ROD, x, y, z, 12, 0.15, 0.1, 0.15, 0.04);
        server.playSound(null, worldPosition, SoundEvents.AMETHYST_CLUSTER_PLACE, SoundSource.BLOCKS, 0.7f, 1.4f);
    }

    public boolean working() {
        return working;
    }

    /** Lasers and other beam sources call this during their own tick. */
    public void receiveBeam(@Nullable ResourceLocation beam, double intensity) {
        pending.add(beam, intensity);
    }

    /** Whether the table is working at all - a laser keeps its aim on one that is, and looks elsewhere otherwise. */
    public boolean wantsBeams() {
        return current != null && !steps.isEmpty() && !assembly.overheated() && !getBlockState().getValue(AssemblyTableBlock.POWERED);
    }

    /**
     * How a beam would fit the step in progress, or null when the table wants none. A laser fires
     * only on {@link Step.Fit#OK}: a beam that would do nothing costs nothing and heats nothing.
     */
    public @Nullable Step.Fit fit(@Nullable ResourceLocation beam, double intensity) {
        return wantsBeams() ? steps.get(assembly.step()).fit(beam, intensity, ServerConfig.tuning().rawFactor()) : null;
    }

    /** Also how the caches catch up with a reload or a config change: the same id keeps its progress. */
    private void choose(@Nullable ResourceLocation next) {
        if (next == null || !next.equals(current)) {
            assembly.reset();
            current = next;
            sync();
        }
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
        sync();
    }

    public List<ResourceLocation> selected() {
        return selected;
    }

    public @Nullable ResourceLocation current() {
        return current;
    }

    // Menu

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new AssemblyTableMenu(id, inventory, this, data);
    }

    public ItemStackHandler inputs() {
        return inputs;
    }

    public ItemStackHandler outputs() {
        return outputs;
    }

    // Client sync: what is ticked and what is running, sent when it changes - never on a timer.

    private void sync() {
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        writeSelection(tag);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** Only the selection: the default would run loadAdditional and empty the client's inventories. */
    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        readSelection(tag);
    }

    @Override
    public void onDataPacket(Connection connection, ClientboundBlockEntityDataPacket packet, HolderLookup.Provider registries) {
        readSelection(packet.getTag());
    }

    private void writeSelection(CompoundTag tag) {
        ListTag ids = new ListTag();
        for (ResourceLocation id : selected) ids.add(StringTag.valueOf(id.toString()));
        tag.put("selected", ids);
        if (current != null) tag.putString("current", current.toString());
        tag.putBoolean("working", working);
    }

    private void readSelection(CompoundTag tag) {
        selected.clear();
        for (Tag id : tag.getList("selected", Tag.TAG_STRING)) {
            ResourceLocation parsed = ResourceLocation.tryParse(id.getAsString());
            if (parsed != null) selected.add(parsed);
        }
        // Kept even if the recipe is gone after a datapack change: the table waits for it to come back.
        current = tag.contains("current") ? ResourceLocation.tryParse(tag.getString("current")) : null;
        working = tag.getBoolean("working");
    }

    /**
     * How many more of {@code stack} the inputs take: enough for {@code input_crafts} crafts of the
     * ticked recipe that uses the most of it, minus what is already there.
     */
    public int room(ItemStack stack) {
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

    public Assembly.Status status() {
        return status;
    }

    // Persistence

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("inputs", inputs.serializeNBT(registries));
        tag.put("outputs", outputs.serializeNBT(registries));
        writeSelection(tag);
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
        readSelection(tag);
        assembly.restore(tag.getInt("step"), tag.getDouble("progress"), tag.getDouble("heat"), tag.getBoolean("overheated"));
    }

    // Handlers

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

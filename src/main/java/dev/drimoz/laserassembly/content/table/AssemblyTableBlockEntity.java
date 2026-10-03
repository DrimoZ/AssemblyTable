package dev.drimoz.laserassembly.content.table;

import dev.drimoz.laserassembly.LaserAssembly;
import dev.drimoz.laserassembly.ServerConfig;
import dev.drimoz.laserassembly.content.laser.LaserBlockEntity;
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
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
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
    private static final int AUTO_TICKS = 10;

    // What the screen reads, through ContainerData: synced while the menu is open, and only then.
    // Values travel as shorts, hence permille rather than raw heat and progress.
    public static final int DATA_HEAT = 0;
    public static final int DATA_PROGRESS = 1;
    public static final int DATA_STEP = 2;
    public static final int DATA_STEPS = 3;
    public static final int DATA_STATUS = 4;
    public static final int DATA_BEAMS = 5;
    /** Tenths of heat per tick, from the blocks around the table. */
    public static final int DATA_COOLING = 6;
    /** Each face's mode, two bits per direction, by ordinal. */
    public static final int DATA_FACES = 7;
    /** Bit 0 auto import, bit 1 auto export. */
    public static final int DATA_AUTO = 8;
    public static final int DATA_COUNT = 9;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case DATA_HEAT -> (int) (1000 * heatFraction());
                case DATA_PROGRESS -> (int) (1000 * stepProgress());
                case DATA_STEP -> assembly.step();
                case DATA_STEPS -> steps.size();
                case DATA_STATUS -> status.ordinal();
                case DATA_BEAMS -> beamsLastTick;
                case DATA_COOLING -> (int) Math.round(cooling * 10);
                case DATA_FACES -> packedFaces();
                case DATA_AUTO -> (autoImport ? 1 : 0) | (autoExport ? 2 : 0);
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
    private final OneWay inputView = new OneWay(inputs, true);
    private final OneWay outputView = new OneWay(outputs, false);
    /** What pipes and hoppers see on a face set to both: inputs take, outputs give, nothing else. */
    private final IItemHandler automation = new CombinedInvWrapper(inputView, outputView);

    /** What each face lets automation do, by direction ordinal. The top is not configurable: see {@link #handler}. */
    private final FaceMode[] faces = {FaceMode.BOTH, FaceMode.BOTH, FaceMode.BOTH, FaceMode.BOTH, FaceMode.BOTH, FaceMode.BOTH};
    private boolean autoImport;
    private boolean autoExport;
    /** Neighbours' item handlers, one cache per face, built on first use on the server. */
    private final BlockCapabilityCache<IItemHandler, @Nullable Direction>[] neighbours = newCaches();

    private final List<ResourceLocation> selected = new ArrayList<>();
    private @Nullable ResourceLocation current;
    /** Synced, for the assembly on display. */
    private boolean working;
    /** Lasers aiming here, which register and leave themselves. Synced, for the lasers tab; not saved: they come back within a second. */
    private final List<BlockPos> lasers = new ArrayList<>();
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
    /** For the info tab: how many beams reached the table on its last working tick. */
    private int beamsLastTick;
    private int lastSignal;

    public AssemblyTableBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.ASSEMBLY_TABLE_ENTITY.get(), pos, state);
    }

    // Ticking

    void serverTick() {
        Tuning tuning = ServerConfig.tuning();
        // Batches every 10 ticks, staggered by position so a wall of tables does not move all at once.
        if ((autoImport || autoExport) && Math.floorMod(level.getGameTime() + worldPosition.hashCode(), AUTO_TICKS) == 0) autoTransfer();
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
            beamsLastTick = 0;
            status = Assembly.Status.IDLE;
            setWorking(false);
            updateComparator();
            if (assembly.heat() > 0) {
                assembly.cool(cooling, tuning);
                setChanged();
            }
            return;
        }

        status = assembly.tick(steps, pending, cooling, tuning);
        beamsLastTick = pending.size();
        pending.clear();
        setWorking(status == Assembly.Status.WORKING || status == Assembly.Status.SLOWED || status == Assembly.Status.DONE);
        updateComparator();
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

    // Progress, for the screen, the comparator and Jade

    public float heatFraction() {
        return (float) (assembly.heat() / ServerConfig.tuning().maxHeat());
    }

    public int stepIndex() {
        return assembly.step();
    }

    public int stepCount() {
        return steps.size();
    }

    public float stepProgress() {
        return steps.isEmpty() ? 0 : (float) (assembly.progress() / steps.get(assembly.step()).energy());
    }

    /** How far through the whole recipe, every step weighed by its energy. */
    public float recipeProgress() {
        if (steps.isEmpty()) return 0;
        long total = 0, done = 0;
        for (int i = 0; i < steps.size(); i++) {
            total += steps.get(i).energy();
            if (i < assembly.step()) done += steps.get(i).energy();
        }
        return (float) ((done + assembly.progress()) / total);
    }

    /** 0 while the table does not work, else 1 to 15 with the recipe's progress. */
    public int comparatorSignal() {
        return working ? Math.max(1, Math.round(15 * recipeProgress())) : 0;
    }

    /** Neighbours hear of the signal only when it changes level, not every tick. */
    private void updateComparator() {
        int signal = comparatorSignal();
        if (signal == lastSignal) return;
        lastSignal = signal;
        level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
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

    /** The step in progress, or null when the table wants no beam - each laser judges its own fit against it. */
    public @Nullable Step currentStep() {
        return wantsBeams() ? steps.get(assembly.step()) : null;
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
        tag.putLongArray("lasers", lasers.stream().mapToLong(BlockPos::asLong).toArray());
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
        lasers.clear();
        for (long pos : tag.getLongArray("lasers")) lasers.add(BlockPos.of(pos));
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

    // Lasers

    public void link(BlockPos laser) {
        if (lasers.contains(laser)) return;
        lasers.add(laser);
        sync();
    }

    public void unlink(BlockPos laser) {
        if (lasers.remove(laser)) sync();
    }

    public List<BlockPos> lasers() {
        return lasers;
    }

    /**
     * From the lasers tab: step one linked laser's intensity, or every one's with {@code index} -1.
     * Only lasers still aiming here: the list a client saw may be a tick old.
     */
    public void stepLaser(int index, int delta) {
        for (int i = 0; i < lasers.size(); i++) {
            if ((index < 0 || i == index) && level.isLoaded(lasers.get(i))
                    && level.getBlockEntity(lasers.get(i)) instanceof LaserBlockEntity laser && worldPosition.equals(laser.target())) {
                laser.stepIntensity(delta);
            }
        }
    }

    // Faces and automatic transfer

    public void cycleFace(Direction side) {
        if (side == Direction.UP) return;
        faces[side.ordinal()] = faces[side.ordinal()].next();
        setChanged();
        // Pipes asked for a handler on this face; it may be another now, or none.
        level.invalidateCapabilities(worldPosition);
    }

    public void toggleAutoImport() {
        autoImport = !autoImport;
        setChanged();
    }

    public void toggleAutoExport() {
        autoExport = !autoExport;
        setChanged();
    }

    /**
     * One stack each way per face per batch, through the same filtered inputs a pipe uses: the table
     * pulls only what its ticked recipes take, and pushes only its outputs.
     */
    private void autoTransfer() {
        for (Direction side : Direction.values()) {
            FaceMode mode = faces[side.ordinal()];
            if (side == Direction.UP || mode == FaceMode.NONE) continue;
            IItemHandler other = neighbour(side);
            if (other == null) continue;
            if (autoImport && mode.takes()) move(other, inputView);
            // From the outputs themselves, not their extract-only view, so a remainder can go back in.
            if (autoExport && mode.gives()) move(outputs, other);
        }
    }

    /**
     * Simulate, compute what moves, then extract: what does not fit after all goes straight back, and
     * if a neighbour will not take its own items back, they drop by the table rather than vanish.
     */
    private void move(IItemHandler from, IItemHandler to) {
        for (int slot = 0; slot < from.getSlots(); slot++) {
            ItemStack offered = from.extractItem(slot, 64, true);
            if (offered.isEmpty()) continue;
            int fits = offered.getCount() - ItemHandlerHelper.insertItemStacked(to, offered, true).getCount();
            if (fits <= 0) continue;
            ItemStack taken = from.extractItem(slot, fits, false);
            ItemStack left = ItemHandlerHelper.insertItemStacked(to, taken, false);
            if (!left.isEmpty()) left = from.insertItem(slot, left, false);
            if (!left.isEmpty()) Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY() + 1, worldPosition.getZ(), left);
            return;
        }
    }

    private @Nullable IItemHandler neighbour(Direction side) {
        if (neighbours[side.ordinal()] == null) {
            neighbours[side.ordinal()] = BlockCapabilityCache.create(Capabilities.ItemHandler.BLOCK, (ServerLevel) level,
                    worldPosition.relative(side), side.getOpposite());
        }
        return neighbours[side.ordinal()].getCapability();
    }

    @SuppressWarnings("unchecked")
    private static BlockCapabilityCache<IItemHandler, @Nullable Direction>[] newCaches() {
        return new BlockCapabilityCache[Direction.values().length];
    }

    private int packedFaces() {
        int packed = 0;
        for (int i = 0; i < faces.length; i++) packed |= faces[i].ordinal() << (2 * i);
        return packed;
    }

    private void unpackFaces(int packed) {
        for (int i = 0; i < faces.length; i++) faces[i] = FaceMode.values()[(packed >> (2 * i)) & 3];
    }

    /**
     * What automation sees on {@code side}, by its mode. The top always takes and gives: it is the
     * face of the assembly on display, so it is never set to anything, nor pushed or pulled from.
     */
    public @Nullable IItemHandler handler(@Nullable Direction side) {
        if (side == null || side == Direction.UP) return automation;
        return switch (faces[side.ordinal()]) {
            case NONE -> null;
            case INPUT -> inputView;
            case OUTPUT -> outputView;
            case BOTH -> automation;
        };
    }

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
        tag.putInt("faces", packedFaces());
        tag.putBoolean("auto_import", autoImport);
        tag.putBoolean("auto_export", autoExport);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        inputs.deserializeNBT(registries, tag.getCompound("inputs"));
        outputs.deserializeNBT(registries, tag.getCompound("outputs"));
        readSelection(tag);
        assembly.restore(tag.getInt("step"), tag.getDouble("progress"), tag.getDouble("heat"), tag.getBoolean("overheated"));
        if (tag.contains("faces")) unpackFaces(tag.getInt("faces"));
        autoImport = tag.getBoolean("auto_import");
        autoExport = tag.getBoolean("auto_export");
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

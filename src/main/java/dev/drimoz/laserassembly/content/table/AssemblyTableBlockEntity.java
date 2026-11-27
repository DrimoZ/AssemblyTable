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
import dev.drimoz.laserassembly.content.ItemSlots;
import dev.drimoz.laserassembly.registry.ModRecipes;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
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
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.CombinedResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

import java.util.function.ToIntFunction;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The table. Ingredients stay in their slots until a craft completes, so breaking the table or
 * switching recipe gives everything back with no bookkeeping: conservation needs no hidden state.
 */
public class AssemblyTableBlockEntity extends BlockEntity implements MenuProvider {

    public static final int INPUTS = 12;
    public static final int OUTPUTS = 4;
    private static final int AUTO_TICKS = 10;
    /** Config contradictions already logged, as "recipe: message". Server thread only. */
    private static final Set<String> UNUSABLE = new HashSet<>();

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
    /** Bit 0 switched on, bits 1-2 the redstone mode, bits 3-6 the threshold, bit 7 paused right now. */
    public static final int DATA_CONTROL = 9;
    /** The pace, in hundredths: how much faster than one laser, heat included. */
    public static final int DATA_SPEED = 10;
    public static final int DATA_COUNT = 11;

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
                case DATA_CONTROL -> (switchedOn ? 1 : 0) | redstoneMode.ordinal() << 1 | threshold << 3 | (paused() ? 1 << 7 : 0);
                case DATA_SPEED -> (int) Math.round(100 * assembly.rate());
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

    private final ItemSlots inputs = new ItemSlots(INPUTS) {
        @Override
        protected void onContentsChanged(int slot, ItemStack previous) {
            contentsChanged();
        }

        /** Only what a ticked recipe uses; a player may put in whole stacks of it, automation less (see {@link #room}). */
        @Override
        public boolean isValid(int slot, ItemResource resource) {
            return uses(resource) > 0;
        }
    };

    private final ItemSlots outputs = new ItemSlots(OUTPUTS) {
        @Override
        protected void onContentsChanged(int slot, ItemStack previous) {
            contentsChanged();
        }
    };

    /** What pipes and hoppers see: inputs take, outputs give, nothing else. */
    private final OneWay inputView = new OneWay(inputs, true, this::room);
    private final OneWay outputView = new OneWay(outputs, false, resource -> 0);
    /** What pipes and hoppers see on a face set to both: inputs take, outputs give, nothing else. */
    private final ResourceHandler<ItemResource> automation = new CombinedResourceHandler<>(inputView, outputView);

    /** What each face lets automation do, by direction ordinal. The top is not configurable: see {@link #handler}. */
    private final FaceMode[] faces = {FaceMode.BOTH, FaceMode.BOTH, FaceMode.BOTH, FaceMode.BOTH, FaceMode.BOTH, FaceMode.BOTH};
    private boolean autoImport;
    private boolean autoExport;
    private boolean switchedOn = true;
    private RedstoneMode redstoneMode = RedstoneMode.BELOW;
    private int threshold = 1;
    private int signal;
    /** Neighbours' item handlers, one cache per face, built on first use on the server. */
    private final BlockCapabilityCache<ResourceHandler<ItemResource>, @Nullable Direction>[] neighbours = newCaches();

    private final List<Identifier> selected = new ArrayList<>();
    private @Nullable Identifier current;
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
            signal = level.getBestNeighborSignal(worldPosition);
            cooling = measureCooling();
        }

        AssemblyRecipe recipe = current == null ? null : lookup(current);
        // A /reload swaps every recipe object: re-check the current one against the new definition.
        if (recipe != currentRecipe || tuning != stepsTuning) contentsDirty = true;
        if (contentsDirty) {
            contentsDirty = false;
            choose(canRun(current) ? current : Rotation.next(selected, current, this::canRun));
            recipe = currentRecipe;
        }

        if (recipe == null || paused()) {
            pending.clear();
            beamsLastTick = 0;
            status = Assembly.Status.IDLE;
            setWorking(false);
            updateComparator();
            // Cooled even at no heat: it also zeroes the pace the info tab shows.
            if (assembly.heat() > 0) setChanged();
            assembly.cool(cooling, tuning);
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
            choose(Rotation.next(selected, current, this::canRun));
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
        double x = worldPosition.getX() + 0.5, y = worldPosition.getY() + AssemblyTableBlock.TOP + 0.3, z = worldPosition.getZ() + 0.5;
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

    /**
     * Tells comparators when the signal changes level. A working tick already tells them through
     * {@link #setChanged()}; this covers the ticks that skip it, such as stopping with no heat left.
     */
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
    public void receiveBeam(@Nullable Identifier beam, double intensity) {
        pending.add(beam, intensity);
    }

    /** Whether the table is working at all - a laser keeps its aim on one that is, and looks elsewhere otherwise. */
    public boolean wantsBeams() {
        return current != null && !steps.isEmpty() && !assembly.overheated() && !paused();
    }

    /** The step in progress, or null when the table wants no beam - each laser judges its own fit against it. */
    public @Nullable Step currentStep() {
        return wantsBeams() ? steps.get(assembly.step()) : null;
    }

    /** Also how the caches catch up with a reload or a config change: the same id keeps its progress. */
    private void choose(@Nullable Identifier next) {
        if (next == null || !next.equals(current)) {
            assembly.reset();
            current = next;
            sync();
        }
        currentRecipe = next == null ? null : lookup(next);
        stepsTuning = ServerConfig.tuning();
        steps = currentRecipe == null ? List.of() : currentRecipe.steps(stepsTuning);
        // A /reload or a save can bring the same id back with fewer steps: past its end, the next
        // tick read a step that is not there and crashed the server.
        if (!steps.isEmpty() && assembly.step() >= steps.size()) assembly.reset();
    }

    // Recipes

    private @Nullable AssemblyRecipe lookup(Identifier id) {
        if (level == null) return null;
        return ModRecipes.byId(level, id);
    }

    private boolean canRun(@Nullable Identifier id) {
        AssemblyRecipe recipe = id == null ? null : lookup(id);
        if (recipe == null) return false;
        try {
            recipe.steps(ServerConfig.tuning());
        } catch (IllegalArgumentException e) {
            // The server config moved a default under a value this recipe sets explicitly. Said once
            // per recipe and message: logged on every inventory change, it buried the log, and
            // without the id nobody could tell which recipe it meant.
            if (UNUSABLE.add(id + ": " + e.getMessage())) {
                LaserAssembly.LOGGER.warn("Recipe {} unusable with the current server config: {}", id, e.getMessage());
            }
            return false;
        }
        return plan(recipe) != null;
    }

    /** Tick or untick a recipe. The inputs keep what they hold; they only stop taking more of it. */
    public void toggle(Identifier id) {
        if (!selected.remove(id)) selected.add(id);
        contentsDirty = true;
        setChanged();
        sync();
    }

    public List<Identifier> selected() {
        return selected;
    }

    public @Nullable Identifier current() {
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

    public ItemSlots inputs() {
        return inputs;
    }

    public ItemSlots outputs() {
        return outputs;
    }

    // Client sync: what is ticked and what is running, sent when it changes - never on a timer.

    private void sync() {
        if (level != null && !level.isClientSide()) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
        writeSelection(output);
        return output.buildResult();
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** Only the selection: the default would run loadAdditional and empty the client's inventories. */
    @Override
    public void handleUpdateTag(ValueInput input) {
        readSelection(input);
    }

    @Override
    public void onDataPacket(Connection connection, ValueInput input) {
        readSelection(input);
    }

    private void writeSelection(ValueOutput output) {
        output.store("selected", Identifier.CODEC.listOf(), selected);
        output.storeNullable("current", Identifier.CODEC, current);
        output.putBoolean("working", working);
        // Packed longs, as 1.21.1 wrote them.
        output.store("lasers", Codec.LONG_STREAM, lasers.stream().mapToLong(BlockPos::asLong));
    }

    private void readSelection(ValueInput input) {
        selected.clear();
        selected.addAll(input.read("selected", Identifier.CODEC.listOf()).orElse(List.of()));
        // Kept even if the recipe is gone after a datapack change: the table waits for it to come back.
        current = input.read("current", Identifier.CODEC).orElse(null);
        working = input.getBooleanOr("working", false);
        lasers.clear();
        input.read("lasers", Codec.LONG_STREAM).ifPresent(packed -> packed.forEach(pos -> lasers.add(BlockPos.of(pos))));
    }

    /**
     * How many more of {@code stack} automation may put in: up to one full stack of it across the
     * inputs, so a pipe or the auto import cannot fill every slot with one ingredient. A player, who
     * sees what they put in, is not held to it.
     */
    private int room(ItemResource resource) {
        int held = 0;
        for (int i = 0; i < INPUTS; i++) {
            ItemStack slot = inputs.stack(i);
            if (resource.matches(slot)) held += slot.getCount();
        }
        return uses(resource) > 0 ? resource.getMaxStackSize() - held : 0;
    }

    /** How many of {@code stack} one craft takes, in the ticked recipe that takes the most. */
    private int uses(ItemResource resource) {
        int perCraft = 0;
        for (Identifier id : selected) {
            AssemblyRecipe recipe = lookup(id);
            if (recipe == null) continue;
            int uses = 0;
            // The ingredient, not the SizedIngredient: its test also wants a stack of the full count,
            // and a hopper hands over one item at a time.
            for (var sized : recipe.ingredients()) if (resource.test(sized.ingredient())) uses += sized.count();
            perCraft = Math.max(perCraft, uses);
        }
        return perCraft;
    }

    // Crafting

    /** One craft: how many to take from each input slot, and what comes out. */
    private record Craft(AssemblyTableBlockEntity table, int[] take, List<ItemStack> out) {
        /** In one transaction: {@link #plan} reserved the room, and a craft happens whole or not at all. */
        void apply() {
            try (Transaction transaction = Transaction.openRoot()) {
                for (int i = 0; i < INPUTS; i++) {
                    if (take[i] > 0) table.inputs.extract(i, table.inputs.getResource(i), take[i], transaction);
                }
                for (ItemStack stack : out) ResourceHandlerUtil.insertStacking(table.outputs, ItemResource.of(stack), stack.getCount(), transaction);
                transaction.commit();
            }
        }
    }

    /**
     * The craft the inputs can pay for, or null - including when its result and leftovers (the bucket
     * of a milk bucket) would not all fit in the outputs: room is reserved before work starts.
     */
    private @Nullable Craft plan(AssemblyRecipe recipe) {
        StackedItemContents contents = new StackedItemContents();
        List<ItemStack> items = new ArrayList<>(INPUTS);
        for (int i = 0; i < INPUTS; i++) {
            ItemStack stack = inputs.stack(i);
            items.add(stack);
            contents.accountStack(stack);
        }
        List<Holder<Item>> picked = new ArrayList<>();
        if (!contents.canCraft(recipe, picked::add)) return null;

        int[] take = new int[INPUTS];
        List<ItemStack> out = new ArrayList<>();
        out.add(recipe.assemble(new AssemblyRecipe.Input(items)));
        for (Holder<Item> item : picked) {
            for (int i = 0; i < INPUTS; i++) {
                ItemStack stack = items.get(i);
                if (take[i] < stack.getCount() && stack.is(item)) {
                    take[i]++;
                    ItemStackTemplate leftover = stack.getItem().getCraftingRemainder(stack);
                    if (leftover != null) out.add(leftover.create());
                    break;
                }
            }
        }

        // Tried for real in a transaction that is never committed: the outputs come out of it as they were.
        try (Transaction trial = Transaction.openRoot()) {
            for (ItemStack stack : out) {
                if (ResourceHandlerUtil.insertStacking(outputs, ItemResource.of(stack), stack.getCount(), trial) < stack.getCount()) return null;
            }
        }
        return new Craft(this, take, out);
    }

    // Surroundings

    void neighboursChanged() {
        signal = level.getBestNeighborSignal(worldPosition);
        coolingDirty = true;
    }

    /** Every face but the top, which belongs to the assembly on display. Each block counts its best coolant. */
    private double measureCooling() {
        var coolants = level.registryAccess().lookupOrThrow(Coolant.REGISTRY);
        double total = 0;
        for (Direction side : Direction.values()) {
            if (side == Direction.UP) continue;
            var block = level.getBlockState(worldPosition.relative(side)).typeHolder();
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

    /** Everything it holds, when it is broken. */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        for (ItemSlots handler : new ItemSlots[]{inputs, outputs}) {
            for (int i = 0; i < handler.size(); i++) {
                Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), handler.stack(i).copy());
            }
        }
    }

    // Capabilities

    // Control: FactoryIO's - a switch over everything, then a redstone condition on the strongest signal.

    public enum RedstoneMode {
        /** Redstone is ignored. */
        ALWAYS,
        /** Works while the signal is below the threshold: at 1, the native pause on any signal. */
        BELOW,
        /** Works while the signal is at the threshold or above. */
        AT_LEAST
    }

    /** Off by the switch, or held by the redstone condition. Read from a field: the signal is measured when a neighbour changes. */
    public boolean paused() {
        if (!switchedOn) return true;
        return switch (redstoneMode) {
            case ALWAYS -> false;
            case BELOW -> signal >= threshold;
            case AT_LEAST -> signal < threshold;
        };
    }

    public void toggleSwitch() {
        switchedOn = !switchedOn;
        setChanged();
    }

    public void setRedstoneMode(RedstoneMode mode) {
        redstoneMode = mode;
        setChanged();
    }

    public void stepThreshold(int delta) {
        threshold = Math.clamp(threshold + delta, 0, 15);
        setChanged();
    }

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
            ResourceHandler<ItemResource> other = neighbour(side);
            if (other == null) continue;
            if (autoImport && mode.takes()) move(other, inputView);
            // From the outputs themselves, not their extract-only view, so a remainder can go back in.
            if (autoExport && mode.gives()) move(outputs, other);
        }
    }

    /**
     * The first stack that moves at all, as far as it fits. In one transaction, which is the house
     * rule "simulate, compute what moves, then extract" done by the API: nothing leaves {@code from}
     * that {@code to} did not take, so there is nothing to put back or drop.
     */
    private static void move(ResourceHandler<ItemResource> from, ResourceHandler<ItemResource> to) {
        ResourceHandlerUtil.moveFirstStacking(from, to, resource -> true, Item.ABSOLUTE_MAX_STACK_SIZE, null);
    }

    private @Nullable ResourceHandler<ItemResource> neighbour(Direction side) {
        if (neighbours[side.ordinal()] == null) {
            neighbours[side.ordinal()] = BlockCapabilityCache.create(Capabilities.Item.BLOCK, (ServerLevel) level,
                    worldPosition.relative(side), side.getOpposite());
        }
        return neighbours[side.ordinal()].getCapability();
    }

    @SuppressWarnings("unchecked")
    private static BlockCapabilityCache<ResourceHandler<ItemResource>, @Nullable Direction>[] newCaches() {
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
    public @Nullable ResourceHandler<ItemResource> handler(@Nullable Direction side) {
        if (side == null || side == Direction.UP) return automation;
        return switch (faces[side.ordinal()]) {
            case NONE -> null;
            case INPUT -> inputView;
            case OUTPUT -> outputView;
            case BOTH -> automation;
        };
    }

    public ResourceHandler<ItemResource> automation() {
        return automation;
    }

    public Assembly.Status status() {
        return status;
    }

    // Persistence

    @Override
    protected void saveAdditional(ValueOutput tag) {
        super.saveAdditional(tag);
        // The keys and layouts 1.21.1 wrote, so a table carried over keeps its items and its recipe.
        inputs.save(tag, "inputs");
        outputs.save(tag, "outputs");
        writeSelection(tag);
        tag.putInt("step", assembly.step());
        tag.putDouble("progress", assembly.progress());
        tag.putDouble("heat", assembly.heat());
        tag.putBoolean("overheated", assembly.overheated());
        tag.putInt("faces", packedFaces());
        tag.putBoolean("auto_import", autoImport);
        tag.putBoolean("auto_export", autoExport);
        tag.putBoolean("switched_on", switchedOn);
        tag.putInt("redstone_mode", redstoneMode.ordinal());
        tag.putInt("threshold", threshold);
    }

    @Override
    protected void loadAdditional(ValueInput tag) {
        super.loadAdditional(tag);
        inputs.load(tag, "inputs");
        outputs.load(tag, "outputs");
        readSelection(tag);
        assembly.restore(tag.getIntOr("step", 0), tag.getDoubleOr("progress", 0), tag.getDoubleOr("heat", 0), tag.getBooleanOr("overheated", false));
        tag.getInt("faces").ifPresent(this::unpackFaces);
        autoImport = tag.getBooleanOr("auto_import", false);
        autoExport = tag.getBooleanOr("auto_export", false);
        switchedOn = tag.getBooleanOr("switched_on", true);
        redstoneMode = RedstoneMode.values()[Math.clamp(tag.getIntOr("redstone_mode", RedstoneMode.BELOW.ordinal()), 0, 2)];
        threshold = Math.clamp(tag.getIntOr("threshold", 1), 0, 15);
    }

    // Handlers

    /** A handler seen from outside: insert-only, within {@code room}, or extract-only. */
    private record OneWay(ItemSlots inner, boolean insert, ToIntFunction<ItemResource> room) implements ResourceHandler<ItemResource> {
        @Override
        public int size() {
            return inner.size();
        }

        @Override
        public ItemResource getResource(int index) {
            return inner.getResource(index);
        }

        @Override
        public long getAmountAsLong(int index) {
            return inner.getAmountAsLong(index);
        }

        @Override
        public long getCapacityAsLong(int index, ItemResource resource) {
            return inner.getCapacityAsLong(index, resource);
        }

        @Override
        public boolean isValid(int index, ItemResource resource) {
            return insert && room.applyAsInt(resource) > 0 && inner.isValid(index, resource);
        }

        /** Within the room left, read inside the transaction: what it already put in counts. */
        @Override
        public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
            int room = insert ? Math.min(amount, this.room.applyAsInt(resource)) : 0;
            return room <= 0 ? 0 : inner.insert(index, resource, room, transaction);
        }

        @Override
        public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
            return insert ? 0 : inner.extract(index, resource, amount, transaction);
        }
    }
}

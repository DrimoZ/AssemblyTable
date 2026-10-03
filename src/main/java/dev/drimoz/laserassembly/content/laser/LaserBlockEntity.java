package dev.drimoz.laserassembly.content.laser;

import dev.drimoz.laserassembly.ServerConfig;
import dev.drimoz.laserassembly.content.table.AssemblyTableBlockEntity;
import dev.drimoz.laserassembly.content.beam.LensItem;
import dev.drimoz.laserassembly.core.Step;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;
import dev.drimoz.laserassembly.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.energy.EnergyStorage;
import org.joml.Vector3f;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Takes FE, and fires it at one table as intensity. It keeps its table while that table wants beams,
 * and only looks elsewhere once it no longer does - a beam that jumped between tables every tick
 * would be unreadable, and "three lasers on this table" would mean nothing.
 */
public class LaserBlockEntity extends BlockEntity implements MenuProvider {

    public static final int MAX_INTENSITY = 5;
    public static final int ENERGY_CAPACITY = 10_000;
    public static final int AUGMENT_SLOTS = 2;
    public static final int BARREL_SLOTS = 3;
    /** Searching scans nearby chunks and casts rays: once a second is plenty. */
    private static final int RETARGET_TICKS = 20;

    public static final int DATA_ENERGY = 0;
    public static final int DATA_INTENSITY = 1;
    public static final int DATA_STATE = 2;
    public static final int DATA_COUNT = 3;
    /** What the laser is doing, for its screen: each but firing says why it holds fire. */
    public static final int STATE_NO_TARGET = 0;
    public static final int STATE_HOLDING = 1;
    public static final int STATE_FIRING = 2;
    public static final int STATE_NO_POWER = 3;
    public static final int STATE_TOO_WEAK = 4;
    public static final int STATE_TOO_STRONG = 5;
    public static final int STATE_WRONG_BEAM = 6;

    private final Power energy = new Power();

    /** One lens, which gives the beam its type. Read once when it changes, not on every shot. */
    private final ItemStackHandler lens = new ItemStackHandler(1) {
        @Override
        protected void onContentsChanged(int slot) {
            onLoad();
            setChanged();
            if (level != null && !level.isClientSide) sync();
        }

        @Override
        protected void onLoad() {
            beam = LensItem.beam(getStackInSlot(0));
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return LensItem.beam(stack) != null && !beamTakenElsewhere(this, slot, stack);
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }
    };

    /** Augments, one of each group. Their combined effect is read once when they change. */
    private final ItemStackHandler augments = new ItemStackHandler(AUGMENT_SLOTS) {
        @Override
        protected void onContentsChanged(int slot) {
            effects = null;
            setChanged();
            // The table's lasers tab shows "auto" for a laser with a Regulator.
            if (level != null && !level.isClientSide) sync();
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return AugmentItem.key(stack) != null && !groupTakenElsewhere(slot, stack);
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }
    };

    /** A Barrel's extra lenses: usable only as far as the Barrel augment opens them. */
    private final ItemStackHandler barrel = new ItemStackHandler(BARREL_SLOTS) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            // The table's lasers tab shows every lens a laser carries.
            if (level != null && !level.isClientSide) sync();
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return LensItem.beam(stack) != null && slot < effects().barrel() && !beamTakenElsewhere(this, slot, stack);
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }
    };

    /** The beam type it fires, null for raw: its lens's, or the Barrel's pick. Synced, for the beam's colour. */
    private @Nullable ResourceLocation beam;

    /** The client has no augments outside the laser's own menu: whether one is a Regulator comes synced. */
    private boolean regulatedOnClient;
    private final List<ResourceLocation> lensesOnClient = new ArrayList<>();

    /** Client render state: where the head points, eased towards the table by the renderer. Never saved nor synced. */
    public final Vector3f headAim = new Vector3f(0, 1, 0);
    public float headAimTime;

    /** What the augments add up to. */
    public record Effects(int range, double efficiency, boolean regulator, int barrel) {
        public static final Effects NONE = new Effects(0, 1, false, 0);
    }

    private @Nullable Effects effects;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case DATA_ENERGY -> energy.getEnergyStored();
                case DATA_INTENSITY -> intensity;
                case DATA_STATE -> state;
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

    private int intensity = 1;
    // Synced to clients, for the beam: sent when they change, never on a timer.
    private @Nullable BlockPos target;
    private boolean firing;

    private @Nullable AssemblyTableBlockEntity table;
    private int retargetIn;
    private int state = STATE_NO_TARGET;

    public LaserBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.LASER_ENTITY.get(), pos, state);
    }

    void serverTick() {
        BlockPos before = target;
        int intensityBefore = intensity;
        int stateBefore = state;
        if (--retargetIn <= 0) {
            retargetIn = RETARGET_TICKS;
            if (table == null || !reachable(table) || !table.wantsBeams()) {
                AssemblyTableBlockEntity found = search();
                // Nothing better: stay on a table still in sight, so the beam does not drop between crafts.
                if (found != null || (table != null && !reachable(table))) retarget(found);
            }
            target = table == null ? null : table.getBlockPos();
        }

        boolean fired = false;
        ResourceLocation beamBefore = beam;
        Step step = table == null || table.isRemoved() ? null : table.currentStep();
        if (step == null) {
            state = target == null ? STATE_NO_TARGET : STATE_HOLDING;
        } else {
            Step.Fit fit = aim(step, effects());
            if (fit == Step.Fit.OK) {
                // Spent in whole FE: a fraction would round to free power at low intensities.
                if (energy.spend(Mth.ceil(intensity * ServerConfig.tuning().fePerIntensity() * effects().efficiency()))) {
                    table.receiveBeam(beam, intensity);
                    fired = true;
                    state = STATE_FIRING;
                } else {
                    state = STATE_NO_POWER;
                }
            } else {
                state = fit == Step.Fit.TOO_WEAK ? STATE_TOO_WEAK : fit == Step.Fit.TOO_STRONG ? STATE_TOO_STRONG : STATE_WRONG_BEAM;
            }
        }
        if (fired != firing || !Objects.equals(before, target) || !Objects.equals(beamBefore, beam) || intensity != intensityBefore || state != stateBefore) {
            firing = fired;
            sync();
        }
    }

    /**
     * Settles which lens and what intensity to fire on {@code step}: a lens of the step's own type
     * wherever it sits, lens slot or Barrel; only failing that, a raw beam - an empty lens slot fits a
     * typed step at a tenth of its power, and taking it first left a Barrel full of the right lenses
     * unused. With a Regulator, at the intensity it picks. Reports how the lens itself fits when
     * nothing does - that is the message a player can act on.
     */
    private Step.Fit aim(Step step, Effects fx) {
        double rawFactor = ServerConfig.tuning().rawFactor();
        Step.Fit reported = null;
        int rawPower = -1;
        for (int i = 0; i <= fx.barrel(); i++) {
            ItemStack stack = i == 0 ? lens.getStackInSlot(0) : barrel.getStackInSlot(i - 1);
            if (i > 0 && stack.isEmpty()) continue;
            ResourceLocation candidate = LensItem.beam(stack);
            int power = fx.regulator() ? regulate(step, candidate, rawFactor) : intensity;
            Step.Fit fit = step.fit(candidate, power, rawFactor);
            if (reported == null) reported = fit;
            if (fit != Step.Fit.OK) continue;
            // A raw beam on a step that wants a lens is the fallback, not the pick.
            if (candidate == null && step.beam().isPresent()) {
                rawPower = power;
                continue;
            }
            return aimWith(candidate, power);
        }
        return rawPower > 0 ? aimWith(null, rawPower) : reported;
    }

    private Step.Fit aimWith(@Nullable ResourceLocation candidate, int power) {
        beam = candidate;
        if (power != intensity) {
            intensity = power;
            setChanged();
        }
        return Step.Fit.OK;
    }

    /**
     * The Regulator's pick: the highest intensity that fits without heating; failing that, the
     * lowest that fits at all; failing that the current one, which reports why it does not.
     */
    private int regulate(Step step, @Nullable ResourceLocation candidate, double rawFactor) {
        int lowestFitting = -1;
        for (int power = MAX_INTENSITY; power >= 1; power--) {
            if (step.fit(candidate, power, rawFactor) != Step.Fit.OK) continue;
            if (power <= step.thresholds().heatAbove()) return power;
            lowestFitting = power;
        }
        return lowestFitting > 0 ? lowestFitting : intensity;
    }

    /** Changes the table this laser feeds, telling both: the table lists the lasers aiming at it. */
    private void retarget(@Nullable AssemblyTableBlockEntity next) {
        if (table != null && !table.isRemoved()) table.unlink(worldPosition);
        table = next;
        if (table != null) table.link(worldPosition);
    }

    /** Broken or unloaded: off the table's list. */
    @Override
    public void setRemoved() {
        if (table != null && !table.isRemoved() && level != null && !level.isClientSide) table.unlink(worldPosition);
        super.setRemoved();
    }

    public int intensity() {
        return intensity;
    }

    /** Every lens it carries, lens slot first, then the Barrel's. */
    public List<ResourceLocation> lenses() {
        if (level != null && level.isClientSide) return lensesOnClient;
        List<ResourceLocation> carried = new ArrayList<>();
        ResourceLocation main = LensItem.beam(lens.getStackInSlot(0));
        if (main != null) carried.add(main);
        for (int i = 0; i < BARREL_SLOTS; i++) {
            ResourceLocation extra = LensItem.beam(barrel.getStackInSlot(i));
            if (extra != null) carried.add(extra);
        }
        return carried;
    }

    /** One of the STATE_ constants: what it does, or why it holds fire. Synced, for the lasers tab. */
    public int state() {
        return state;
    }

    public boolean regulated() {
        return level != null && level.isClientSide ? regulatedOnClient : effects().regulator();
    }

    public Effects effects() {
        if (effects == null) {
            // Not cached until the level is there: a laser loading with its chunk has no registries yet.
            if (level == null) return Effects.NONE;
            int range = 0, barrelSlots = 0;
            double efficiency = 1;
            boolean regulator = false;
            for (int i = 0; i < AUGMENT_SLOTS; i++) {
                AugmentType type = AugmentItem.type(augments.getStackInSlot(i), level.registryAccess()).orElse(null);
                if (type == null) continue;
                range += type.range();
                efficiency *= type.efficiency();
                regulator |= type.regulator();
                barrelSlots = Math.max(barrelSlots, type.barrel());
            }
            effects = new Effects(range, efficiency, regulator, barrelSlots);
        }
        return effects;
    }

    /**
     * One lens of each beam type per laser: a laser fires a single lens, so a second of the same
     * type would only ever sit there. It also spreads a shift-click across the type it lacks.
     */
    private boolean beamTakenElsewhere(ItemStackHandler handler, int slot, ItemStack stack) {
        ResourceLocation wanted = LensItem.beam(stack);
        if (handler != lens && wanted.equals(LensItem.beam(lens.getStackInSlot(0)))) return true;
        for (int i = 0; i < BARREL_SLOTS; i++) {
            if ((handler != barrel || i != slot) && wanted.equals(LensItem.beam(barrel.getStackInSlot(i)))) return true;
        }
        return false;
    }

    /** One augment of each group: a second Range on the same laser is refused, not stacked. */
    private boolean groupTakenElsewhere(int slot, ItemStack stack) {
        if (level == null) return false;
        ResourceLocation group = group(stack);
        for (int i = 0; i < AUGMENT_SLOTS; i++) {
            if (i != slot && group != null && group.equals(group(augments.getStackInSlot(i)))) return true;
        }
        return false;
    }

    private @Nullable ResourceLocation group(ItemStack stack) {
        ResourceLocation id = AugmentItem.id(stack);
        return id == null ? null : AugmentItem.type(stack, level.registryAccess()).map(type -> type.group(id)).orElse(id);
    }

    private int range() {
        return ServerConfig.laserRange() + effects().range();
    }

    /** FE in from any side; nothing may pull it back out, only the laser spends it. */
    private final class Power extends EnergyStorage {
        Power() {
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
            setChanged();
            return true;
        }
    }

    /** The nearest table in front, in reach and in sight; one that wants beams before one that does not. */
    private @Nullable AssemblyTableBlockEntity search() {
        int range = range();
        AssemblyTableBlockEntity best = null;
        double bestScore = Double.MAX_VALUE;
        int minX = (worldPosition.getX() - range) >> 4, maxX = (worldPosition.getX() + range) >> 4;
        int minZ = (worldPosition.getZ() - range) >> 4, maxZ = (worldPosition.getZ() + range) >> 4;
        for (int cx = minX; cx <= maxX; cx++) {
            for (int cz = minZ; cz <= maxZ; cz++) {
                // Never loads a chunk: one that is not loaded holds no table worth firing at.
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) continue;
                for (BlockEntity entity : chunk.getBlockEntities().values()) {
                    if (!(entity instanceof AssemblyTableBlockEntity candidate) || !reachable(candidate)) continue;
                    double score = worldPosition.distSqr(candidate.getBlockPos()) + (candidate.wantsBeams() ? 0 : 1_000_000);
                    if (score < bestScore) {
                        bestScore = score;
                        best = candidate;
                    }
                }
            }
        }
        return best;
    }

    /** In front of the laser, within range, and with nothing solid in between. */
    private boolean reachable(AssemblyTableBlockEntity candidate) {
        if (candidate.isRemoved() || candidate.getLevel() != level) return false;
        BlockPos pos = candidate.getBlockPos();
        Direction facing = getBlockState().getValue(LaserBlock.FACING);
        BlockPos offset = pos.subtract(worldPosition);
        if (offset.getX() * facing.getStepX() + offset.getY() * facing.getStepY() + offset.getZ() * facing.getStepZ() <= 0) return false;
        int range = range();
        if (worldPosition.distSqr(pos) > range * range) return false;
        BlockHitResult hit = level.clip(new ClipContext(muzzle(), beamEnd(pos), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
        return hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(pos);
    }

    /** Just outside the firing face, so the ray does not start inside the laser itself. */
    public Vec3 muzzle() {
        Direction facing = getBlockState().getValue(LaserBlock.FACING);
        return Vec3.atCenterOf(worldPosition).add(facing.getStepX() * 0.51, facing.getStepY() * 0.51, facing.getStepZ() * 0.51);
    }

    /** The middle of the table's top: where the assembly sits. */
    public static Vec3 beamEnd(BlockPos table) {
        return new Vec3(table.getX() + 0.5, table.getY() + 1.0, table.getZ() + 0.5);
    }

    // Menu: buttons 0 and 1 step the intensity down and up.

    public void stepIntensity(int delta) {
        intensity = Mth.clamp(intensity + delta, 1, MAX_INTENSITY);
        setChanged();
        if (level != null && !level.isClientSide) sync();
    }

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new LaserMenu(id, inventory, this, data);
    }

    public ItemStackHandler lens() {
        return lens;
    }

    public ItemStackHandler augments() {
        return augments;
    }

    public ItemStackHandler barrel() {
        return barrel;
    }

    /** Lens, augments and Barrel lenses, when the laser is broken. */
    void dropContents() {
        for (ItemStackHandler handler : new ItemStackHandler[]{lens, augments, barrel}) {
            for (int i = 0; i < handler.getSlots(); i++) {
                Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), handler.getStackInSlot(i));
            }
        }
    }

    public @Nullable ResourceLocation beam() {
        return beam;
    }

    public EnergyStorage energy() {
        return energy;
    }

    public @Nullable BlockPos target() {
        return target;
    }

    public boolean firing() {
        return firing;
    }

    // Sync and persistence

    private void sync() {
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        writeBeam(tag);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection connection, ClientboundBlockEntityDataPacket packet, HolderLookup.Provider registries) {
        readBeam(packet.getTag());
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        readBeam(tag);
    }

    private void writeBeam(CompoundTag tag) {
        if (target != null) tag.put("target", NbtUtils.writeBlockPos(target));
        tag.putBoolean("firing", firing);
        tag.putInt("intensity", intensity);
        tag.putBoolean("regulated", effects().regulator());
        tag.putInt("state", state);
        ListTag carried = new ListTag();
        for (ResourceLocation lens : lenses()) carried.add(StringTag.valueOf(lens.toString()));
        tag.put("lenses", carried);
        if (beam != null) tag.putString("beam", beam.toString());
    }

    private void readBeam(CompoundTag tag) {
        target = NbtUtils.readBlockPos(tag, "target").orElse(null);
        firing = tag.getBoolean("firing");
        intensity = Mth.clamp(tag.getInt("intensity"), 1, MAX_INTENSITY);
        regulatedOnClient = tag.getBoolean("regulated");
        state = tag.getInt("state");
        lensesOnClient.clear();
        for (Tag lens : tag.getList("lenses", Tag.TAG_STRING)) {
            ResourceLocation parsed = ResourceLocation.tryParse(lens.getAsString());
            if (parsed != null) lensesOnClient.add(parsed);
        }
        beam = tag.contains("beam") ? ResourceLocation.tryParse(tag.getString("beam")) : null;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("energy", energy.serializeNBT(registries));
        tag.putInt("intensity", intensity);
        tag.put("lens", lens.serializeNBT(registries));
        tag.put("augments", augments.serializeNBT(registries));
        tag.put("barrel", barrel.serializeNBT(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("energy")) energy.deserializeNBT(registries, tag.get("energy"));
        intensity = Mth.clamp(tag.getInt("intensity"), 1, MAX_INTENSITY);
        lens.deserializeNBT(registries, tag.getCompound("lens"));
        augments.deserializeNBT(registries, tag.getCompound("augments"));
        barrel.deserializeNBT(registries, tag.getCompound("barrel"));
        effects = null;
    }
}

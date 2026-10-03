package dev.drimoz.laserassembly.content.laser;

import dev.drimoz.laserassembly.ServerConfig;
import dev.drimoz.laserassembly.content.table.AssemblyTableBlockEntity;
import dev.drimoz.laserassembly.core.Step;
import dev.drimoz.laserassembly.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.Mth;
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
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Takes FE, and fires it at one table as intensity. It keeps its table while that table wants beams,
 * and only looks elsewhere once it no longer does - a beam that jumped between tables every tick
 * would be unreadable, and "three lasers on this table" would mean nothing.
 */
public class LaserBlockEntity extends BlockEntity implements MenuProvider {

    public static final int MAX_INTENSITY = 5;
    public static final int ENERGY_CAPACITY = 10_000;
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
        if (--retargetIn <= 0) {
            retargetIn = RETARGET_TICKS;
            if (table == null || !reachable(table) || !table.wantsBeams()) {
                AssemblyTableBlockEntity found = search();
                // Nothing better: stay on a table still in sight, so the beam does not drop between crafts.
                if (found != null || (table != null && !reachable(table))) table = found;
            }
            target = table == null ? null : table.getBlockPos();
        }

        boolean fired = false;
        Step.Fit fit = table == null || table.isRemoved() ? null : table.fit(null, intensity);
        if (fit == Step.Fit.OK) {
            // Spent in whole FE: a fraction would round to free power at low intensities.
            if (energy.spend(Mth.ceil(intensity * ServerConfig.tuning().fePerIntensity()))) {
                table.receiveBeam(null, intensity);
                fired = true;
                state = STATE_FIRING;
            } else {
                state = STATE_NO_POWER;
            }
        } else {
            state = target == null ? STATE_NO_TARGET : fit == null ? STATE_HOLDING
                    : fit == Step.Fit.TOO_WEAK ? STATE_TOO_WEAK : fit == Step.Fit.TOO_STRONG ? STATE_TOO_STRONG : STATE_WRONG_BEAM;
        }
        if (fired != firing || !Objects.equals(before, target)) {
            firing = fired;
            sync();
        }
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
        int range = ServerConfig.laserRange();
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
        int range = ServerConfig.laserRange();
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
    }

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new LaserMenu(id, this, data);
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
    }

    private void readBeam(CompoundTag tag) {
        target = NbtUtils.readBlockPos(tag, "target").orElse(null);
        firing = tag.getBoolean("firing");
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("energy", energy.serializeNBT(registries));
        tag.putInt("intensity", intensity);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("energy")) energy.deserializeNBT(registries, tag.get("energy"));
        intensity = Mth.clamp(tag.getInt("intensity"), 1, MAX_INTENSITY);
    }
}

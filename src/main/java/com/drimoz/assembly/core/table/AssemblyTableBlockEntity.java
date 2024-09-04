package com.drimoz.assembly.core.table;

import com.drimoz.assembly.Assembly;
import com.drimoz.assembly.Config;
import com.drimoz.assembly.core.utils.EnergyContainer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.wrapper.CombinedInvWrapper;

import javax.annotation.Nullable;

public class AssemblyTableBlockEntity extends BlockEntity implements MenuProvider {

    // Constants

    public static final int[] UPGRADE_SLOTS = createSlots(Config.ASSEMBLY_TABLE_UPGRADE_COUNT.get(), null);
    public static final int[] INPUT_SLOTS = createSlots(12, UPGRADE_SLOTS);
    public static final int[] OUTPUT_SLOTS = createSlots(12, INPUT_SLOTS);

    // Config Properties

    public static final boolean ALLOW_NO_LASER = Config.ASSEMBLY_TABLE_NO_LASER.get();
    public static final double NO_LASER_SPEED_MODIFIER = Config.ASSEMBLY_TABLE_NO_LASER_SPEED_MULTIPLIER.get();

    // Protected Properties

    protected EnergyContainer energyContainer;
    protected LazyOptional<IEnergyStorage> lazyEnergyContainer;

    protected ItemStackHandler upgradeContainer;
    protected LazyOptional<IItemHandler> lazyUpgradeContainer;
    protected ItemStackHandler inputContainer;
    protected LazyOptional<IItemHandler> lazyInputContainer;
    protected ItemStackHandler outputContainer;
    protected LazyOptional<IItemHandler> lazyOutputContainer;

    protected LazyOptional<CombinedInvWrapper> lazyIOContainer;

    // Private Properties

    private int currentEnergy;

    // LifeCycle

    public AssemblyTableBlockEntity(BlockPos pos, BlockState state) {
        super(Assembly.ASSEMBLY_TABLE_ENTITY.get(), pos, state);

        initItemHandlers();
        initEnergyHandler();
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.assembly.assembly_table");
    }

    // Interface - Capabilities

    @Override
    public void load(CompoundTag nbt) {
        super.load(nbt);
        this.upgradeContainer.deserializeNBT(nbt.getCompound("upgradeContainer"));
        this.inputContainer.deserializeNBT(nbt.getCompound("inputContainer"));
        this.outputContainer.deserializeNBT(nbt.getCompound("outputContainer"));
        if (ALLOW_NO_LASER) this.energyContainer.deserializeNBT(nbt.getCompound("energyContainer"));
    }

    @Override
    protected void saveAdditional(CompoundTag nbt) {
        super.saveAdditional(nbt);
        nbt.put("upgradeContainer", this.upgradeContainer.serializeNBT());
        nbt.put("inputContainer", this.inputContainer.serializeNBT());
        nbt.put("outputContainer", this.outputContainer.serializeNBT());
        if (ALLOW_NO_LASER && this.energyContainer != null) nbt.put("energyContainer", this.energyContainer.serializeNBT());
    }

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER) {
            if (side == null) {
                return lazyIOContainer.cast();
            } else if (side == Direction.NORTH) {
                return LazyOptional.of(() -> inputContainer).cast();
            } else if (side == Direction.SOUTH) {
                return LazyOptional.of(() -> outputContainer).cast();
            } else {
                return lazyIOContainer.cast();
            }
        }

        if (cap == ForgeCapabilities.ENERGY && ALLOW_NO_LASER) {
            return lazyEnergyContainer.cast();
        }

        return super.getCapability(cap, side);
    }

    @Override
    public void onLoad() {
        super.onLoad();

        if (ALLOW_NO_LASER && energyContainer != null) this.lazyEnergyContainer = LazyOptional.of(() -> energyContainer);

        this.lazyUpgradeContainer = LazyOptional.of(() -> upgradeContainer);
        this.lazyInputContainer = LazyOptional.of(() -> inputContainer);
        this.lazyOutputContainer = LazyOptional.of(() -> outputContainer);

        this.lazyIOContainer = LazyOptional.of(() -> new CombinedInvWrapper(inputContainer, outputContainer));
    }

    @Override
    public void invalidateCaps()  {
        super.invalidateCaps();

        if (this.lazyEnergyContainer != null) this.lazyEnergyContainer.invalidate();

        this.lazyUpgradeContainer.invalidate();
        this.lazyInputContainer.invalidate();
        this.lazyOutputContainer.invalidate();

        this.lazyIOContainer.invalidate();
    }

    // Interface - Menu

    @Override
    public AbstractContainerMenu createMenu(int i, Inventory inventory, Player player) {
        return null;
    }

    // Interface - Drop

    public void drops() {
        CombinedInvWrapper combinedInv = new CombinedInvWrapper(upgradeContainer, inputContainer, outputContainer);
        SimpleContainer inventory = new SimpleContainer(combinedInv.getSlots());

        for (int i = 0; i < combinedInv.getSlots(); i++) {
            inventory.setItem(i, combinedInv.getStackInSlot(i));
        }

        assert this.level != null;
        Containers.dropContents(this.level, this.worldPosition, inventory);
    }

    // Interface - Ticking

    public static void tick(Level level, BlockPos pos, BlockState state, AssemblyTableBlockEntity blockEntity) {
        // Implement your block's ticking logic here
    }




    // Inner Work - Slots

    private void initItemHandlers() {
        initUpgradeHandler();
        initInputHandler();
        initOutputHandler();

        lazyIOContainer = LazyOptional.of(() -> new CombinedInvWrapper(inputContainer, outputContainer));
    }

    private void initUpgradeHandler() {
        this.upgradeContainer = new ItemStackHandler(UPGRADE_SLOTS.length) {
            @Override
            protected void onContentsChanged(int slot) {
                setChanged();
            }

            @Override
            public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                return stack;
            }

            @Override
            public ItemStack extractItem(int slot, int amount, boolean simulate) {
                return ItemStack.EMPTY;
            }

        };
        this.lazyUpgradeContainer = LazyOptional.of(() -> this.upgradeContainer);
    }

    private void initInputHandler() {
        this.inputContainer = new ItemStackHandler(INPUT_SLOTS.length) {
            @Override
            protected void onContentsChanged(int slot) {
                setChanged();
            }

            @Override
            public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                return super.insertItem(slot, stack, simulate);
            }

            @Override
            public ItemStack extractItem(int slot, int amount, boolean simulate) {
                return super.extractItem(slot, amount, simulate);
            }

        };
        this.lazyInputContainer = LazyOptional.of(() -> this.inputContainer);
    }

    private void initOutputHandler() {
        this.outputContainer = new ItemStackHandler(OUTPUT_SLOTS.length) {
            @Override
            protected void onContentsChanged(int slot) {
                setChanged();
            }

            @Override
            public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                return stack;
            }

            @Override
            public ItemStack extractItem(int slot, int amount, boolean simulate) {

                return super.extractItem(slot, amount, simulate);
            }

        };
        this.lazyOutputContainer = LazyOptional.of(() -> this.outputContainer);
    }

    // Inner Work - Energy

    private void initEnergyHandler() {
        if (!ALLOW_NO_LASER) return;

        this.energyContainer = new EnergyContainer(
                Config.ASSEMBLY_TABLE_NO_LASER_ENERGY_CAPACITY.get(),
                Config.ASSEMBLY_TABLE_NO_LASER_ENERGY_INPUT_RATE.get(),
                0,
                0
        );
        this.lazyEnergyContainer = LazyOptional.of(() -> this.energyContainer);
    }

    // Inner Work - Utils

    private static int[] createSlots(int count, int[] baseArray) {
        if (count == 0) {
            return new int[0];
        }

        int startValue = 0;
        if (baseArray != null && baseArray.length > 0) {
            startValue = baseArray[baseArray.length - 1] + 1;
        }
        return java.util.stream.IntStream.range(startValue, startValue + count).toArray();
    }
}

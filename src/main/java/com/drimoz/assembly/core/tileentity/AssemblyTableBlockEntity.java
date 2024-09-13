package com.drimoz.assembly.core.tileentity;

import com.drimoz.assembly.Assembly;
import com.drimoz.assembly.Config;
import com.drimoz.assembly.client.menu.AssemblyTableMenu;
import com.drimoz.assembly.core.model.ShadowedRecipe;
import com.drimoz.assembly.core.utils.Constants;
import com.drimoz.assembly.network.AssemblyNetwork;
import com.drimoz.assembly.network.ProgressSyncS2C;
import com.drimoz.assembly.network.SelectedRecipesSyncS2C;
import com.drimoz.assembly.recipe.AssemblyTableRecipe;
import com.drimoz.assembly.core.model.EFactoryFace;
import com.drimoz.assembly.core.model.FactoryOptions;
import com.drimoz.assembly.core.model.EnergyContainer;
import com.drimoz.assembly.network.EnergySyncS2C;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.wrapper.CombinedInvWrapper;

import javax.annotation.Nullable;
import java.util.*;

public class AssemblyTableBlockEntity extends BlockEntity implements MenuProvider {

    // Constants

    public static final int UPGRADE_SLOT_COUNT = Config.ASSEMBLY_TABLE_UPGRADE_COUNT.get();
    public static final int INPUT_SLOT_COUNT = 24;
    public static final int SHADOW_SLOT_COUNT = 45;
    public static final int OUTPUT_SLOT_COUNT = 8;
    public static final int TOTAL_SLOTS = UPGRADE_SLOT_COUNT + INPUT_SLOT_COUNT + SHADOW_SLOT_COUNT + OUTPUT_SLOT_COUNT;


    public static final int[] UPGRADE_SLOTS = createSlots(UPGRADE_SLOT_COUNT, null);
    public static final int[] INPUT_SLOTS = createSlots(INPUT_SLOT_COUNT, UPGRADE_SLOTS);
    public static final int[] SHADOW_SLOTS = createSlots(SHADOW_SLOT_COUNT, INPUT_SLOTS);
    public static final int[] OUTPUT_SLOTS = createSlots(OUTPUT_SLOT_COUNT, SHADOW_SLOTS);

    // Config Properties

    public static final boolean ALLOW_NO_LASER = Config.ASSEMBLY_TABLE_NO_LASER.get();

    // Protected Properties

    protected final FactoryOptions settings;

    protected EnergyContainer energyContainer;
    protected LazyOptional<IEnergyStorage> lazyEnergyContainer;

    protected ItemStackHandler upgradeContainer;
    protected LazyOptional<IItemHandler> lazyUpgradeContainer;
    protected ItemStackHandler inputContainer;
    protected LazyOptional<IItemHandler> lazyInputContainer;
    protected ItemStackHandler shadowContainer;
    protected LazyOptional<IItemHandler> lazyShadowContainer;
    protected ItemStackHandler outputContainer;
    protected LazyOptional<IItemHandler> lazyOutputContainer;

    protected LazyOptional<CombinedInvWrapper> lazyIOContainer;

    // Private Properties

    private final LinkedList<ShadowedRecipe> shadowedRecipes = new LinkedList<>();
    private final LinkedList<ShadowedRecipe> selectedShadowedRecipes = new LinkedList<>();
    private List<Integer> selectedRecipeSlots = new ArrayList<>();
    private int recipeAccumulatedEnergy = 0;
    private int recipeTotalProgress = 0;

    // LifeCycle

    public AssemblyTableBlockEntity(BlockPos pos, BlockState state) {
        super(Assembly.ASSEMBLY_TABLE_ENTITY.get(), pos, state);

        settings = new FactoryOptions() {
            @Override
            public void onChanged() {
                setChanged();
            }
        };

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

        settings.read(nbt.getCompound("settings"));

        this.recipeAccumulatedEnergy = nbt.getInt("recipeAccumulatedEnergy");

        ListTag shadowedRecipesTag = (ListTag) nbt.get("shadowedRecipes");
        for (int i = 0; i < shadowedRecipesTag.size(); i++) {
            this.shadowedRecipes.add(ShadowedRecipe.deserializeNBT(shadowedRecipesTag.getCompound(i)));
        }

        ListTag selectedShadowedRecipesTag = (ListTag) nbt.get("selectedShadowedRecipes");
        for (int i = 0; i < selectedShadowedRecipesTag.size(); i++) {
            this.selectedShadowedRecipes.add(ShadowedRecipe.deserializeNBT(selectedShadowedRecipesTag.getCompound(i)));
        }

        this.upgradeContainer.deserializeNBT(nbt.getCompound("upgradeContainer"));

        this.inputContainer.deserializeNBT(nbt.getCompound("inputContainer"));
        this.outputContainer.deserializeNBT(nbt.getCompound("outputContainer"));

        this.energyContainer.deserializeNBT(nbt.getCompound("energyContainer"));
    }

    @Override
    protected void saveAdditional(CompoundTag nbt) {
        super.saveAdditional(nbt);

        CompoundTag settingsTag = new CompoundTag();
        settings.write(settingsTag);
        nbt.put("settings", settingsTag);

        nbt.putInt("recipeAccumulatedEnergy", this.recipeAccumulatedEnergy);

        ListTag shadowedRecipesTag = new ListTag();
        for (ShadowedRecipe recipe : this.shadowedRecipes) {
            shadowedRecipesTag.add(recipe.serializeNBT());
        }
        nbt.put("shadowedRecipes", shadowedRecipesTag);

        ListTag selectedShadowedRecipesTag = new ListTag();
        for (ShadowedRecipe recipe : this.selectedShadowedRecipes) {
            selectedShadowedRecipesTag.add(recipe.serializeNBT());
        }
        nbt.put("selectedShadowedRecipes", selectedShadowedRecipesTag);

        nbt.put("upgradeContainer", this.upgradeContainer.serializeNBT());

        nbt.put("inputContainer", this.inputContainer.serializeNBT());
        nbt.put("outputContainer", this.outputContainer.serializeNBT());

        nbt.put("energyContainer", this.energyContainer.serializeNBT());
    }

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER) {
            if (side != null) {
                switch (settings.getFace(side)) {
                    case INPUT:
                        return lazyInputContainer.cast();
                    case OUTPUT:
                        return lazyOutputContainer.cast();
                    case INPUT_OUTPUT:
                        return lazyIOContainer.cast();
                }
            }
            else {
                return LazyOptional.of(() -> new CombinedInvWrapper(inputContainer, shadowContainer, outputContainer)).cast();
            }
        }

        //if (cap == Assembly.SHADOW_CAP && side == null) return lazyShadowContainer.cast();

        if (cap == ForgeCapabilities.ENERGY) {
            if (side == null) return lazyEnergyContainer.cast();
            if ( settings.getFace(side) == EFactoryFace.ENERGY) return lazyEnergyContainer.cast();
        }

        return super.getCapability(cap, side);
    }

    @Override
    public void onLoad() {
        super.onLoad();

        this.lazyEnergyContainer = LazyOptional.of(() -> energyContainer);

        this.lazyUpgradeContainer = LazyOptional.of(() -> upgradeContainer);

        this.lazyInputContainer = LazyOptional.of(() -> inputContainer);
        this.lazyShadowContainer = LazyOptional.of(() -> shadowContainer);
        this.lazyOutputContainer = LazyOptional.of(() -> outputContainer);

        this.lazyIOContainer = LazyOptional.of(() -> new CombinedInvWrapper(inputContainer, outputContainer));
    }

    @Override
    public void invalidateCaps()  {
        super.invalidateCaps();

        this.lazyEnergyContainer.invalidate();

        this.lazyUpgradeContainer.invalidate();

        this.lazyInputContainer.invalidate();
        this.lazyShadowContainer.invalidate();
        this.lazyOutputContainer.invalidate();

        this.lazyIOContainer.invalidate();
    }

    // Interface - Menu

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        syncEntityCurrentEnergy();
        syncRecipeProgress();
        syncSelectedRecipeSlots();

        return new AssemblyTableMenu(id, inventory, this);
    }

    // Interface - Drop

    /**
     * Drops the contents of the block entity's inventory.
     * Creates a new SimpleContainer with the combined inventory of the upgradeContainer, inputContainer, and outputContainer.
     * Fills the SimpleContainer with the items from the combined inventory.
     * Drops the contents of the SimpleContainer at the block entity's world position.
     *
     * @assumes level is not null
     */
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

    /**
     * Toggles the selection of a recipe based on the provided slot ID.
     *
     * @param slotId the ID of the slot to toggle the recipe for
     */
    public void toggleRecipe(int slotId) {
        slotId -= Constants.Menu.TE_INVENTORY_FIRST_SLOT_INDEX + INPUT_SLOT_COUNT;

        for (ShadowedRecipe recipe : this.shadowedRecipes) {
            if (slotId == recipe.getSlot()) {
                recipe.toggleSelection();

                if (recipe.isSelected()) {
                    this.selectedShadowedRecipes.add(recipe);
                } else {
                    this.selectedShadowedRecipes.remove(recipe);
                }

                syncSelectedRecipeSlots();
                return;
            }
        }

    }

    public static void tick(Level level, BlockPos pos, BlockState state, AssemblyTableBlockEntity blockEntity) {
        if (level.isClientSide) return;
        if (blockEntity.isRemoved()) return;

        blockEntity.updateShadowSlots();

        blockEntity.performRecipe();
    }

    // Inner Work - Recipe Crafting

    private void performRecipe() {
        if (level == null || level.isClientSide()) return;
        if (!getBlockState().getValue(BlockStateProperties.ENABLED)) return;

        AssemblyTableRecipe recipe = getSelectedRecipe();
        if (recipe == null) {
            this.recipeAccumulatedEnergy = 0;
            syncRecipeProgress();
            return;
        }

        if (this.recipeAccumulatedEnergy < recipe.getTotalEnergy()) {
            int energy = this.energyContainer.useEnergy(Config.ASSEMBLY_TABLE_ENERGY_CONSUMPTION.get(), false);
            this.recipeAccumulatedEnergy += energy;
            syncRecipeProgress();
        }


        if (this.recipeAccumulatedEnergy >= recipe.getTotalEnergy() && craftRecipe(recipe)) {
            this.recipeAccumulatedEnergy -= recipe.getTotalEnergy();
        }
    }

    private AssemblyTableRecipe getSelectedRecipe () {
        AssemblyTableRecipe recipe = this.selectedShadowedRecipes.stream().filter(ShadowedRecipe::isSelected).findFirst().map(ShadowedRecipe::getRecipe).orElse(null);
        if (recipe != null && this.recipeTotalProgress != recipe.getTotalEnergy()) {
            this.recipeTotalProgress = recipe.getTotalEnergy();
        }
        return recipe;
    }

    private boolean craftRecipe (AssemblyTableRecipe recipe) {
        if (!isRecipeValid(recipe)) return false;
        boolean canPerform = false;
        int slot = -1;

        for (int i = 0; i < this.outputContainer.getSlots(); i++) {
            if (
                    this.outputContainer.getStackInSlot(i).isEmpty() ||
                            (
                                    this.outputContainer.getStackInSlot(i).getItem() == recipe.getResultItem(level.registryAccess()).getItem() &&
                                            this.outputContainer.getStackInSlot(i).getCount() + recipe.getResultItem(level.registryAccess()).getCount()
                                                    <= this.outputContainer.getStackInSlot(i).getMaxStackSize()
                                    )
            ) {
                canPerform = true;
                slot = i;
                break;
            }
        }
        if (!canPerform) return false;

        for (Ingredient ingredient : recipe.getIngredients()) {
            ItemStack stackToRemove = ItemStack.EMPTY;

            for (ItemStack stack : ingredient.getItems()) {
                int countFound = 0;
                for (int i = 0; i < this.inputContainer.getSlots(); i++) {
                    ItemStack slotStack = this.inputContainer.getStackInSlot(i);
                    if (slotStack.isEmpty()) continue;
                    if (ItemStack.isSameItemSameTags(stack, slotStack)) {
                        countFound += slotStack.getCount();
                        if (countFound >= stack.getCount()) break;
                    }
                }

                if (countFound >= stack.getCount()) {
                    stackToRemove = stack;
                    break;
                }
            }

            if (stackToRemove.isEmpty()) throw new IllegalStateException("Critical error: ingredient not found in input container");
            int countNeeded = stackToRemove.getCount();
            int countRemoved = 0;

            for (int i = 0; i < this.inputContainer.getSlots(); i++) {
                ItemStack slotStack = this.inputContainer.getStackInSlot(i);
                if (slotStack.isEmpty()) continue;
                if (ingredient.test(slotStack)) {
                    int removeCount = Math.min(slotStack.getCount(), countNeeded - countRemoved);
                    slotStack.shrink(removeCount);
                    countRemoved += removeCount;
                    if (countRemoved >= countNeeded) break;
                }
            }
        }

        // If inputs removed, add to output
        ItemStack stack = recipe.getResultItem(level.registryAccess());
        if (!this.outputContainer.getStackInSlot(slot).isEmpty()) {
            stack.setCount(stack.getCount() + this.outputContainer.getStackInSlot(slot).getCount());
        }
        this.outputContainer.setStackInSlot(slot, stack);

        return true;
    }

    // Inner Work - Shadow Slots Management

    private void updateShadowSlots () {
        setupMissingRecipes();
        getMatchingRecipes();
        clearInvalidShadows();
        populateShadowSlots();

        syncSelectedRecipeSlots();
    }

    private void setupMissingRecipes () {
        if (level == null || level.isClientSide()) return;

        List<ShadowedRecipe> toRemove = new ArrayList<>();
        for (ShadowedRecipe recipe : this.shadowedRecipes) {
            if (recipe.getRecipe() == null) {
                recipe.setRecipe(
                        level.getRecipeManager()
                                .getAllRecipesFor(AssemblyTableRecipe.Type.INSTANCE)
                                .stream()
                                .filter(r -> r.getId().equals(recipe.getRecipeId()))
                                .findFirst()
                                .orElse(null)
                );
            }

            if (recipe.getRecipe() == null) {
                toRemove.add(recipe);
            }
        }

        this.shadowedRecipes.removeAll(toRemove);
        this.selectedShadowedRecipes.removeAll(toRemove);
        toRemove.clear();

        for (ShadowedRecipe recipe : this.selectedShadowedRecipes) {
            if (recipe.getRecipe() == null) {
                recipe.setRecipe(
                        level.getRecipeManager()
                                .getAllRecipesFor(AssemblyTableRecipe.Type.INSTANCE)
                                .stream()
                                .filter(r -> r.getId().equals(recipe.getRecipeId()))
                                .findFirst()
                                .orElse(null)
                );
            }

            if (recipe.getRecipe() == null) {
                toRemove.add(recipe);
            }
        }

        this.selectedShadowedRecipes.removeAll(toRemove);
    }

    private void getMatchingRecipes () {
        if (level == null || level.isClientSide()) return;

        for (AssemblyTableRecipe recipe : level.getRecipeManager().getAllRecipesFor(AssemblyTableRecipe.Type.INSTANCE)) {
            if (isRecipeValid(recipe)) {
                ShadowedRecipe sr = new ShadowedRecipe(recipe);
                if (!this.shadowedRecipes.contains(sr)) this.shadowedRecipes.add(new ShadowedRecipe(recipe));
            }
        }
    }

    private void clearInvalidShadows () {
        // clear this.recipes from invalid recipes
        List<ShadowedRecipe> toRemove = new ArrayList<>();
        for (ShadowedRecipe sr : this.shadowedRecipes) {
            if (!isRecipeValid(sr.getRecipe())) {
                toRemove.add(sr);
            }
        }
        this.shadowedRecipes.removeAll(toRemove);
        this.selectedShadowedRecipes.removeAll(toRemove);
    }

    private void populateShadowSlots () {
        int shadowSlotIndex = 0;

        for (ShadowedRecipe recipe : this.shadowedRecipes) {
            ItemStack shadowItem = recipe.getRecipe().getResultItem(level.registryAccess()).copy();
            this.shadowContainer.setStackInSlot(shadowSlotIndex, shadowItem);
            recipe.setSlot(shadowSlotIndex);

            shadowSlotIndex++;
        }

        // Set others to empty if not empty
        for (int i = shadowSlotIndex; i < SHADOW_SLOT_COUNT; i++) {
            if (!this.shadowContainer.getStackInSlot(i).isEmpty())
                this.shadowContainer.setStackInSlot(i, ItemStack.EMPTY);
        }
    }

    // Inner Work - Recipe & Ingredient Validation

    private boolean isRecipeValid (AssemblyTableRecipe recipe){
        for (Ingredient ingredient : recipe.getIngredients()) {
            ItemStack foundItemStack = ItemStack.EMPTY;

            for (ItemStack stack : ingredient.getItems()) {
                int countFound = 0;

                for (int i = 0; i < this.inputContainer.getSlots(); i++) {
                    ItemStack slotStack = this.inputContainer.getStackInSlot(i);
                    if (slotStack.isEmpty()) continue;
                    if (ItemStack.isSameItemSameTags(stack, slotStack)) {
                        countFound += slotStack.getCount();
                        if (countFound >= stack.getCount()) break;
                    }
                }

                if (countFound >= stack.getCount()) {
                    foundItemStack = stack;
                    break;
                }
            }

            if (foundItemStack.isEmpty()) return false;
        }

        return true;
    }

    // Inner Work - Slots

    /**
     * Initializes item handlers for the assembly table, including upgrade, input, and output handlers.
     * Also sets up a lazy IO container for combined input and output inventory management.
     */
    private void initItemHandlers () {
        initUpgradeHandler();
        initInputHandler();
        initShadowHandler();
        initOutputHandler();

        lazyIOContainer = LazyOptional.of(() -> new CombinedInvWrapper(inputContainer, outputContainer));
    }

    private void initUpgradeHandler () {
        this.upgradeContainer = new ItemStackHandler(UPGRADE_SLOT_COUNT) {
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

    private void initInputHandler () {
        this.inputContainer = new ItemStackHandler(INPUT_SLOT_COUNT) {
            @Override
            protected void onContentsChanged(int slot) {
                setChanged();
            }

            @Override
            public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                if (!isValidInput(stack)) {
                    return stack;
                }
                return super.insertItem(slot, stack, simulate);
            }

            @Override
            public ItemStack extractItem(int slot, int amount, boolean simulate) {
                return super.extractItem(slot, amount, simulate);
            }

        };
        this.lazyInputContainer = LazyOptional.of(() -> this.inputContainer);
    }

    private void initShadowHandler () {
        this.shadowContainer = new ItemStackHandler(SHADOW_SLOT_COUNT) {
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
        this.lazyShadowContainer = LazyOptional.of(() -> this.shadowContainer);
    }

    private void initOutputHandler () {
        this.outputContainer = new ItemStackHandler(OUTPUT_SLOT_COUNT) {
            @Override
            protected void onContentsChanged(int slot) {
                setChanged();
            }

            @Override
            public ItemStack extractItem(int slot, int amount, boolean simulate) {
                return super.extractItem(slot, amount, simulate);
            }

            @Override
            public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                return stack;
            }
        };
        this.lazyOutputContainer = LazyOptional.of(() -> this.outputContainer);
    }

    /**
     * Checks if the given item stack is a valid input for the assembly table.
     *
     * @param stack    The item stack to check
     * @return True if the item stack is a valid input, false otherwise
     */
    public boolean isValidInput (ItemStack stack){
        Level level = this.getLevel();
        if (level == null || level.isClientSide()) {
            return false;
        }

        for (AssemblyTableRecipe recipe : level.getRecipeManager().getAllRecipesFor(AssemblyTableRecipe.Type.INSTANCE)) {
            for (Ingredient ingredient : recipe.getIngredients()) {
                if (ingredientMatches(ingredient, stack)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Checks if the given ItemStack matches the specified Ingredient.
     *
     * @param  ingredient  the Ingredient to check against
     * @param  stack       the ItemStack to check
     * @return true if the ItemStack matches the Ingredient, false otherwise
     */
    private boolean ingredientMatches (Ingredient ingredient, ItemStack stack){
        boolean result = ingredient.test(stack);
        return result;
    }

    // Inner Work - Energy

    private void initEnergyHandler () {
        if (!ALLOW_NO_LASER) return;


        this.energyContainer = new EnergyContainer(
                Config.ASSEMBLY_TABLE_ENERGY_CAPACITY.get(),
                Config.ASSEMBLY_TABLE_ENERGY_INPUT_RATE.get(),
                0,
                0
        ) {
            @Override
            public void onEnergyChanged() {
                super.onEnergyChanged();
                if (level != null && !level.isClientSide()) {
                    syncEntityCurrentEnergy();
                }
            }
        };
        this.lazyEnergyContainer = LazyOptional.of(() -> this.energyContainer);
    }

    // Inner Work - Utils

    /**
     * Creates an array of slot indices based on the given count and an optional base array.
     *
     * @param  count      the number of slots to create
     * @param  baseArray  the base array to extend, or null to start from 0
     * @return an array of slot indices
     */
    private static int[] createSlots ( int count, int[] baseArray){
        if (count == 0) {
            return new int[0];
        }

        int startValue = 0;
        if (baseArray != null && baseArray.length > 0) {
            startValue = baseArray[baseArray.length - 1] + 1;
        }
        return java.util.stream.IntStream.range(startValue, startValue + count).toArray();
    }

    // Sync Methods

    private void syncEntityCurrentEnergy() {
        if (level.isClientSide) return;

        AssemblyNetwork.sendToClients(new EnergySyncS2C(this.energyContainer.getCurrentEnergy(), worldPosition));
    }

    private void syncRecipeProgress() {
        if (level.isClientSide) return;

        AssemblyNetwork.sendToClients(new ProgressSyncS2C(this.recipeAccumulatedEnergy, this.recipeTotalProgress, worldPosition));
    }

    private void syncSelectedRecipeSlots() {
        if (level.isClientSide) return;

        this.selectedRecipeSlots.clear();
        for (ShadowedRecipe shadowedRecipe : this.selectedShadowedRecipes) {
            if (shadowedRecipe.isSelected()) {
                if (!this.selectedRecipeSlots.contains(shadowedRecipe.getSlot())) {
                    this.selectedRecipeSlots.add(shadowedRecipe.getSlot());
                }
            }
        }

        AssemblyNetwork.sendToClients(new SelectedRecipesSyncS2C(this.selectedRecipeSlots, worldPosition));
    }

    // Sync Setters

    public void setClientCurrentEnergy(int energy) {
        if (level.isClientSide) this.energyContainer.overrideCurrentEnergy(energy);
    }

    public void setClientCurrentProgress(int progress) {
        if (level.isClientSide) this.recipeAccumulatedEnergy = progress;
    }

    public void setClientTotalProgress(int progress) {
        if (level.isClientSide) this.recipeTotalProgress = progress;
    }

    public void setClientSelectedRecipeSlots(List<Integer> selectedRecipeSlots) {
        if (level.isClientSide) this.selectedRecipeSlots = selectedRecipeSlots;
    }

    // Accessors

    public List<Integer> getSelectedRecipeSlots() {
        return this.selectedRecipeSlots;
    }

    public Integer getCurrentEnergy() {
        return this.energyContainer.getCurrentEnergy();
    }

    public Integer getTotalEnergy() {
        return this.energyContainer.getEnergyCapacity();
    }

    public Integer getRecipeTotalProgress() {
        return this.recipeTotalProgress;
    }

    public Integer getRecipeCurrentProgress() {
        return this.recipeAccumulatedEnergy;
    }
}

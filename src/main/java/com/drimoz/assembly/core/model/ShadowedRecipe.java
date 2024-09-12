package com.drimoz.assembly.core.model;

import com.drimoz.assembly.Assembly;
import com.drimoz.assembly.recipe.AssemblyTableRecipe;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeManager;

import java.util.Objects;

public class ShadowedRecipe {

    private AssemblyTableRecipe recipe;
    private final ResourceLocation recipeId;
    private int slot;
    private boolean selected = false;

    protected ShadowedRecipe(ResourceLocation recipeId, int slot, boolean selected) {
        this.recipe = null;
        this.recipeId = recipeId;
        this.slot = slot;
        this.selected = selected;
    }

    public ShadowedRecipe(AssemblyTableRecipe recipe) {
        this.recipe = recipe;
        this.recipeId = recipe.getId();
        this.slot = -1;
    }

    public ResourceLocation getRecipeId() {
        return recipeId;
    }

    public AssemblyTableRecipe getRecipe() {
        return recipe;
    }

    public void setRecipe(AssemblyTableRecipe recipe) {
        if (this.recipe == null) this.recipe = recipe;
    }

    public int getSlot() {
        return slot;
    }

    public void setSlot(int slot) {
        this.slot = slot;
    }

    public boolean isSelected() {
        return selected;
    }

    public void toggleSelection() {
        this.selected = !this.selected;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ShadowedRecipe that = (ShadowedRecipe) o;
        return Objects.equals(recipe, that.recipe);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(recipe);
    }

    public CompoundTag serializeNBT() {
        CompoundTag nbt = new CompoundTag();
        nbt.putString("recipe", recipe.getId().getPath());
        nbt.putInt("slot", slot);
        nbt.putBoolean("selected", selected);
        return nbt;
    }

    public static ShadowedRecipe deserializeNBT(CompoundTag nbt) {
        return new ShadowedRecipe(
                new ResourceLocation(Assembly.MOD_ID, nbt.getString("recipe")),
                nbt.getInt("slot"),
                nbt.getBoolean("selected")
        );
    }
}

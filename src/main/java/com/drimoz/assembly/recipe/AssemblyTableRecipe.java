package com.drimoz.assembly.recipe;

import com.drimoz.assembly.Assembly;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;

public class AssemblyTableRecipe implements Recipe<SimpleContainer> {
    private final ResourceLocation id;
    private final NonNullList<Ingredient> ingredients;
    private final ItemStack output;
    private final int totalEnergy;
    private final boolean onlyLaser;
    private final boolean noLaser;

    public AssemblyTableRecipe(ResourceLocation id, NonNullList<Ingredient> ingredients, ItemStack output, int totalEnergy, boolean onlyLaser, boolean noLaser) {
        this.id = id;
        this.ingredients = ingredients;
        this.output = output;
        this.totalEnergy = totalEnergy;

        if (onlyLaser && noLaser) {
            this.onlyLaser = false;
            this.noLaser = false;
        } else {
            this.onlyLaser = onlyLaser;
            this.noLaser = noLaser;
        }

        Assembly.LOGGER.info(this.toString());
    }

    @Override
    public String toString() {
        StringBuilder ingredientsInfo = new StringBuilder();

        for (int i = 0; i < ingredients.size(); i++) {
            Ingredient ingredient = ingredients.get(i);
            ingredientsInfo.append("Ingredient ")
                    .append(i + 1)
                    .append(": ")
                    .append(ingredientToString(ingredient))
                    .append("\n");
        }

        return "AssemblyTableRecipe{" +
                "id=" + id +
                ", ingredients=[\n" + ingredientsInfo.toString() + "]" +
                ", output=" + output +
                ", totalEnergy=" + totalEnergy +
                ", onlyLaser=" + onlyLaser +
                ", noLaser=" + noLaser +
                '}';
    }

    private String ingredientToString(Ingredient ingredient) {
        // Convert Ingredient to JSON and check for item or tag
        JsonObject ingredientJson = (JsonObject) ingredient.toJson();
        if (ingredientJson.has("item")) {
            return "Item: " + GsonHelper.getAsString(ingredientJson, "item");
        } else if (ingredientJson.has("tag")) {
            return "Tag: " + GsonHelper.getAsString(ingredientJson, "tag");
        } else {
            return "Unknown Ingredient";
        }
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        return this.ingredients;
    }

    public int getTotalEnergy() {
        return totalEnergy;
    }

    public boolean isNoLaser() {
        return noLaser;
    }

    public boolean isOnlyLaser() {
        return onlyLaser;
    }

    @Override
    public ResourceLocation getId() {
        return id;
    }

    @Override
    public boolean matches(SimpleContainer inv, Level level) {
        if (inv.getContainerSize() < ingredients.size()) {
            return false;
        }

        for (int i = 0; i < ingredients.size(); i++) {
            Ingredient ingredient = ingredients.get(i);
            ItemStack itemInSlot = inv.getItem(i);

            if (!ingredient.test(itemInSlot)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack assemble(SimpleContainer inventory, RegistryAccess registryAccess) {
        return output.copy();
    }

    @Override
    public boolean canCraftInDimensions(int pWidth, int pHeight) {
        return true;
    }

    @Override
    public ItemStack getResultItem(RegistryAccess pRegistryAccess) {
        return output.copy();
    }

    @Override
    public RecipeType<?> getType() {
        return Type.INSTANCE;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return Serializer.INSTANCE;
    }

    public static class Type implements RecipeType<AssemblyTableRecipe> {
        public static final Type INSTANCE = new Type();
        public static final String ID = "assembling";
    }

    public static class Serializer implements RecipeSerializer<AssemblyTableRecipe> {
        public static final Serializer INSTANCE = new Serializer();
        public static final ResourceLocation ID = new ResourceLocation(Assembly.MOD_ID, "assembling");

        @Override
        public AssemblyTableRecipe fromJson(ResourceLocation recipeId, JsonObject serializedRecipe) {
            ItemStack output = ShapedRecipe.itemStackFromJson(GsonHelper.getAsJsonObject(serializedRecipe, "result"));

            JsonArray ingredients = GsonHelper.getAsJsonArray(serializedRecipe, "ingredients");
            NonNullList<Ingredient> inputs = NonNullList.withSize(ingredients.size(), Ingredient.EMPTY);

            for (int i = 0; i < inputs.size(); i++) {
                inputs.set(i, Ingredient.fromJson(ingredients.get(i)));
            }

            int totalEnergy = GsonHelper.getAsInt(serializedRecipe, "energy", 1);

            boolean onlyLaser = GsonHelper.getAsBoolean(serializedRecipe, "only_laser", false);
            boolean noLaser = GsonHelper.getAsBoolean(serializedRecipe, "no_laser", false);

            return new AssemblyTableRecipe(recipeId, inputs, output, totalEnergy, onlyLaser, noLaser);
        }

        @Override
        public AssemblyTableRecipe fromNetwork(ResourceLocation recipeId, FriendlyByteBuf buffer) {
            NonNullList<Ingredient> inputs = NonNullList.withSize(buffer.readInt(), Ingredient.EMPTY);
            for(int i = 0; i < inputs.size(); i++) {
                inputs.set(i, Ingredient.fromNetwork(buffer));
            }

            ItemStack output = buffer.readItem();

            int totalEnergy = buffer.readVarInt();
            boolean onlyLaser = buffer.readBoolean();
            boolean noLaser = buffer.readBoolean();

            return new AssemblyTableRecipe(recipeId, inputs, output, totalEnergy, onlyLaser, noLaser);
        }

        @Override
        public void toNetwork(FriendlyByteBuf buffer, AssemblyTableRecipe recipe) {
            buffer.writeInt(recipe.ingredients.size());

            for (Ingredient ingredient : recipe.getIngredients()) {
                ingredient.toNetwork(buffer);
            }

            buffer.writeItemStack(recipe.getResultItem(null), false);

            buffer.writeVarInt(recipe.totalEnergy);
            buffer.writeBoolean(recipe.onlyLaser);
            buffer.writeBoolean(recipe.noLaser);
        }
    }
}

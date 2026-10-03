package dev.drimoz.laserassembly.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.drimoz.laserassembly.core.Step;
import dev.drimoz.laserassembly.core.Thresholds.Overrides;
import dev.drimoz.laserassembly.core.Tuning;
import dev.drimoz.laserassembly.registry.ModRecipes;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.entity.player.StackedContents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.crafting.SizedIngredient;

import java.util.List;
import java.util.Optional;

/**
 * {@code laserassembly:assembling}. Either one raw step - {@code energy} - or up to three
 * {@code steps}, each with its own energy and beam.
 */
public final class AssemblyRecipe implements Recipe<AssemblyRecipe.Input> {

    public static final int MAX_INGREDIENTS = 12;
    public static final int MAX_STEPS = 3;
    public static final int MAX_COUNT = 64;

    /** The table's input slots. */
    public record Input(List<ItemStack> items) implements RecipeInput {
        @Override
        public ItemStack getItem(int index) {
            return items.get(index);
        }

        @Override
        public int size() {
            return items.size();
        }
    }

    private final List<SizedIngredient> ingredients;
    private final ItemStack result;
    private final Optional<Integer> energy;
    private final Overrides intensity;
    private final List<RecipeStep> steps;
    private final boolean keepComponents;
    /** Each ingredient repeated count times: the shape StackedContents matches against. */
    private final NonNullList<Ingredient> flattened = NonNullList.create();

    public AssemblyRecipe(List<SizedIngredient> ingredients, ItemStack result, Optional<Integer> energy,
                          Overrides intensity, List<RecipeStep> steps, boolean keepComponents) {
        this.ingredients = ingredients;
        this.result = result;
        this.energy = energy;
        this.intensity = intensity;
        this.steps = steps;
        this.keepComponents = keepComponents;
        for (SizedIngredient sized : ingredients) {
            for (int i = 0; i < sized.count(); i++) flattened.add(sized.ingredient());
        }
    }

    /**
     * The steps, thresholds resolved against {@code tuning}. Throws {@link IllegalArgumentException}
     * when the recipe contradicts it - possible only when the server config moved a default under a
     * value the recipe set explicitly.
     */
    public List<Step> steps(Tuning tuning) {
        if (steps.isEmpty()) return List.of(new RecipeStep(Optional.empty(), energy.orElseThrow(), Overrides.NONE, false).resolve(intensity, tuning));
        return steps.stream().map(step -> step.resolve(intensity, tuning)).toList();
    }

    public List<SizedIngredient> ingredients() {
        return ingredients;
    }

    /**
     * Matching goes through vanilla's recipe-book solver rather than a greedy pass, so an item that
     * fits two ingredients - oak planks for "planks" and for "oak planks" - goes where it is needed.
     */
    // ponytail: StackedContents tells items apart by id only, so an ingredient on components
    // (a specific potion) matches any stack of that item; a component-aware solver if a pack needs it.
    @Override
    public boolean matches(Input input, Level level) {
        StackedContents contents = new StackedContents();
        for (ItemStack stack : input.items()) contents.accountStack(stack);
        return contents.canCraft(this, null);
    }

    /** With {@code keep_components}, the result carries over the first ingredient's components: enchantments, damage, name. */
    @Override
    public ItemStack assemble(Input input, HolderLookup.Provider registries) {
        if (keepComponents) {
            for (ItemStack stack : input.items()) {
                if (ingredients.getFirst().ingredient().test(stack)) return stack.transmuteCopy(result.getItem(), result.getCount());
            }
        }
        return result.copy();
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        return flattened;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return result;
    }

    /** Keeps it out of the recipe book, which would otherwise warn about a category it does not know. */
    @Override
    public boolean isSpecial() {
        return true;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.ASSEMBLING_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return ModRecipes.ASSEMBLING.get();
    }

    // Serialization

    public static final MapCodec<AssemblyRecipe> CODEC = RecordCodecBuilder.<AssemblyRecipe>mapCodec(i -> i.group(
            SizedIngredient.NESTED_CODEC.listOf(1, MAX_INGREDIENTS).fieldOf("ingredients").forGetter(r -> r.ingredients),
            ItemStack.STRICT_CODEC.fieldOf("result").forGetter(r -> r.result),
            ExtraCodecs.POSITIVE_INT.optionalFieldOf("energy").forGetter(r -> r.energy),
            RecipeStep.OVERRIDES.forGetter(r -> r.intensity),
            RecipeStep.CODEC.listOf(0, MAX_STEPS).optionalFieldOf("steps", List.of()).forGetter(r -> r.steps),
            Codec.BOOL.optionalFieldOf("keep_components", false).forGetter(r -> r.keepComponents)
    ).apply(i, AssemblyRecipe::new)).validate(AssemblyRecipe::validate);

    public static final StreamCodec<RegistryFriendlyByteBuf, AssemblyRecipe> STREAM_CODEC = StreamCodec.composite(
            SizedIngredient.STREAM_CODEC.apply(ByteBufCodecs.list()), r -> r.ingredients,
            ItemStack.STREAM_CODEC, r -> r.result,
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs::optional), r -> r.energy,
            RecipeStep.OVERRIDES_STREAM, r -> r.intensity,
            RecipeStep.STREAM_CODEC.apply(ByteBufCodecs.list()), r -> r.steps,
            ByteBufCodecs.BOOL, r -> r.keepComponents,
            AssemblyRecipe::new);

    /** Refused and logged by the recipe loader, never coerced: the datapack author has to see it. */
    private static DataResult<AssemblyRecipe> validate(AssemblyRecipe recipe) {
        if (recipe.steps.isEmpty() == recipe.energy.isEmpty()) {
            return DataResult.error(() -> "give either \"energy\" for a single raw step, or \"steps\" each with their own energy - not both");
        }
        for (SizedIngredient sized : recipe.ingredients) {
            if (sized.count() > MAX_COUNT) return DataResult.error(() -> "ingredient count above " + MAX_COUNT + ": " + sized.count());
        }
        for (RecipeStep step : recipe.steps) {
            if (step.strict() && step.beam().isEmpty()) return DataResult.error(() -> "a strict step needs a beam");
        }
        try {
            recipe.steps(Tuning.DEFAULTS);
        } catch (IllegalArgumentException e) {
            return DataResult.error(e::getMessage);
        }
        return DataResult.success(recipe);
    }
}

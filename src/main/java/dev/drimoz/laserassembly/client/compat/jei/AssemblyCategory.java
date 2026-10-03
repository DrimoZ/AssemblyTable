package dev.drimoz.laserassembly.client.compat.jei;

import dev.drimoz.laserassembly.LaserAssembly;
import dev.drimoz.laserassembly.ServerConfig;
import dev.drimoz.laserassembly.client.gui.GuiSprites;
import dev.drimoz.laserassembly.content.beam.BeamType;
import dev.drimoz.laserassembly.core.Step;
import dev.drimoz.laserassembly.recipe.AssemblyRecipe;
import dev.drimoz.laserassembly.registry.ModBlocks;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.common.crafting.SizedIngredient;

import java.util.Arrays;
import java.util.List;

/**
 * An assembling recipe as a player reads it: the ingredients - one row when they fit, two at most,
 * only the slots they fill - an arrow, the result, and under the arrow the lens each step needs, in
 * order. The lenses are real slots: a click shows how to make one, and hovering says the step's
 * beam, energy and minimum intensity. A coloured strip said the same less plainly.
 */
final class AssemblyCategory implements IRecipeCategory<RecipeHolder<AssemblyRecipe>> {

    static final RecipeType<RecipeHolder<AssemblyRecipe>> TYPE =
            RecipeType.createRecipeHolderType(LaserAssembly.id("assembling"));

    private static final int COLUMNS = 6;
    private static final int SLOT = 18;
    /** Between the ingredients and the result: the arrow above, the steps' lenses below - three at most. */
    private static final int MIDDLE_X = COLUMNS * SLOT + 4;
    private static final int MIDDLE_WIDTH = 3 * SLOT;
    private static final int RESULT_X = MIDDLE_X + MIDDLE_WIDTH + 4;
    private static final int WIDTH = RESULT_X + SLOT;
    private static final int HEIGHT = 2 * SLOT;

    private final IDrawable icon;

    AssemblyCategory(IGuiHelper helper) {
        this.icon = helper.createDrawableItemStack(new ItemStack(ModBlocks.ASSEMBLY_TABLE_ITEM.get()));
    }

    @Override
    public RecipeType<RecipeHolder<AssemblyRecipe>> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return ModBlocks.ASSEMBLY_TABLE.get().getName();
    }

    @Override
    public int getWidth() {
        return WIDTH;
    }

    @Override
    public int getHeight() {
        return HEIGHT;
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, RecipeHolder<AssemblyRecipe> holder, IFocusGroup focuses) {
        List<SizedIngredient> ingredients = holder.value().ingredients();
        for (int i = 0; i < ingredients.size(); i++) {
            builder.addSlot(RecipeIngredientRole.INPUT, inputX(i) + 1, inputY(i, ingredients.size()) + 1)
                    .addItemStacks(Arrays.asList(ingredients.get(i).getItems()));
        }
        List<Step> steps = steps(holder);
        for (int i = 0; i < steps.size(); i++) {
            Step step = steps.get(i);
            Component line = Component.translatable(LaserAssembly.MOD_ID + ".gui.recipe.step",
                    step.beam().map(BeamType::name).orElse(Component.translatable(LaserAssembly.MOD_ID + ".beam.raw")),
                    step.energy(), (int) step.thresholds().min());
            builder.addSlot(RecipeIngredientRole.CATALYST, stepX(i, steps.size()) + 1, SLOT + 1)
                    .addItemStack(tool(step))
                    .addRichTooltipCallback((view, tooltip) -> tooltip.add(line));
        }
        builder.addSlot(RecipeIngredientRole.OUTPUT, RESULT_X + 1, SLOT / 2 + 1)
                .addItemStack(holder.value().getResultItem(Minecraft.getInstance().level.registryAccess()));
    }

    @Override
    public void draw(RecipeHolder<AssemblyRecipe> holder, IRecipeSlotsView slots, GuiGraphics graphics, double mouseX, double mouseY) {
        int count = holder.value().ingredients().size();
        for (int i = 0; i < count; i++) GuiSprites.slot(graphics, inputX(i), inputY(i, count), false);
        int steps = steps(holder).size();
        for (int i = 0; i < steps; i++) GuiSprites.slot(graphics, stepX(i, steps), SLOT, false);
        GuiSprites.slot(graphics, RESULT_X, SLOT / 2, false);
        GuiSprites.arrow(graphics, MIDDLE_X + (MIDDLE_WIDTH - 20) / 2, 2, 0);
    }

    private static int inputX(int i) {
        return (i % COLUMNS) * SLOT;
    }

    /** One row sits level with the result; two fill the height. */
    private static int inputY(int i, int count) {
        return count <= COLUMNS ? SLOT / 2 : (i / COLUMNS) * SLOT;
    }

    private static int stepX(int i, int count) {
        return MIDDLE_X + (MIDDLE_WIDTH - count * SLOT) / 2 + i * SLOT;
    }

    /** What a step asks for: its beam's lens, or any laser for a raw beam. */
    private static ItemStack tool(Step step) {
        return step.beam()
                .flatMap(beam -> Minecraft.getInstance().level.registryAccess().registry(BeamType.REGISTRY).map(beams -> beams.get(beam)))
                .map(type -> new ItemStack(type.item()))
                .orElseGet(() -> new ItemStack(ModBlocks.LASER_ITEM.get()));
    }

    private static List<Step> steps(RecipeHolder<AssemblyRecipe> holder) {
        try {
            return holder.value().steps(ServerConfig.tuning());
        } catch (IllegalArgumentException e) {
            return List.of();
        }
    }
}

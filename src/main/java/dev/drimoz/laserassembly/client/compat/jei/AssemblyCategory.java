package dev.drimoz.laserassembly.client.compat.jei;

import dev.drimoz.laserassembly.LaserAssembly;
import dev.drimoz.laserassembly.ServerConfig;
import dev.drimoz.laserassembly.client.BeamColors;
import dev.drimoz.laserassembly.client.gui.GuiSprites;
import dev.drimoz.laserassembly.content.beam.BeamType;
import dev.drimoz.laserassembly.core.Step;
import dev.drimoz.laserassembly.recipe.AssemblyRecipe;
import dev.drimoz.laserassembly.registry.ModBlocks;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.ITooltipBuilder;
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
 * An assembling recipe as the table runs it: ingredients 4x3 with their counts, the arrow, the result,
 * and beneath, the steps as the screen's strip - a segment per step in its beam's colour, its beam,
 * intensity and energy on hover.
 */
final class AssemblyCategory implements IRecipeCategory<RecipeHolder<AssemblyRecipe>> {

    static final RecipeType<RecipeHolder<AssemblyRecipe>> TYPE =
            RecipeType.createRecipeHolderType(LaserAssembly.id("assembling"));

    private static final int WIDTH = 132;
    private static final int STRIP_Y = 58;
    private static final int HEIGHT = STRIP_Y + 6;
    private static final int RESULT_X = 112;

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
            builder.addSlot(RecipeIngredientRole.INPUT, 1 + (i % 4) * 18, 1 + (i / 4) * 18)
                    .addItemStacks(Arrays.asList(ingredients.get(i).getItems()));
        }
        builder.addSlot(RecipeIngredientRole.OUTPUT, RESULT_X + 1, 19)
                .addItemStack(holder.value().getResultItem(Minecraft.getInstance().level.registryAccess()));
    }

    @Override
    public void draw(RecipeHolder<AssemblyRecipe> holder, IRecipeSlotsView slots, GuiGraphics graphics, double mouseX, double mouseY) {
        for (int i = 0; i < 12; i++) GuiSprites.slot(graphics, (i % 4) * 18, (i / 4) * 18, false);
        GuiSprites.slot(graphics, RESULT_X, 18, false);
        GuiSprites.arrow(graphics, 80, 20, 0);

        List<Step> steps = steps(holder);
        long total = steps.stream().mapToLong(Step::energy).sum();
        int x = 0;
        for (int i = 0; i < steps.size(); i++) {
            int width = i == steps.size() - 1 ? WIDTH - x : (int) (WIDTH * steps.get(i).energy() / total);
            graphics.fill(x, STRIP_Y, x + width, STRIP_Y + 5, 0xFF000000 | BeamColors.rgb(steps.get(i).beam().orElse(null)));
            if (i > 0) graphics.fill(x, STRIP_Y, x + 1, STRIP_Y + 5, 0xFF373737);
            x += width;
        }
    }

    @Override
    public void getTooltip(ITooltipBuilder tooltip, RecipeHolder<AssemblyRecipe> holder, IRecipeSlotsView slots, double mouseX, double mouseY) {
        if (mouseY < STRIP_Y || mouseY >= STRIP_Y + 5) return;
        for (Step step : steps(holder)) {
            Component beam = step.beam().map(BeamType::name).orElse(Component.translatable(LaserAssembly.MOD_ID + ".beam.raw"));
            tooltip.add(Component.translatable(LaserAssembly.MOD_ID + ".gui.recipe.step", beam, step.energy(), (int) step.thresholds().min()));
        }
    }

    private static List<Step> steps(RecipeHolder<AssemblyRecipe> holder) {
        try {
            return holder.value().steps(ServerConfig.tuning());
        } catch (IllegalArgumentException e) {
            return List.of();
        }
    }
}

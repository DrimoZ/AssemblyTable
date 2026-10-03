package dev.drimoz.laserassembly.client.compat.jei;

import dev.drimoz.laserassembly.LaserAssembly;
import dev.drimoz.laserassembly.registry.ModBlocks;
import dev.drimoz.laserassembly.registry.ModRecipes;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

/** Only JEI loads this class: the mod runs the same without it. */
@mezz.jei.api.JeiPlugin
public final class JeiPlugin implements IModPlugin {

    @Override
    public ResourceLocation getPluginUid() {
        return LaserAssembly.id("jei");
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(new AssemblyCategory(registration.getJeiHelpers().getGuiHelper()));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        registration.addRecipes(AssemblyCategory.TYPE, Minecraft.getInstance().level.getRecipeManager().getAllRecipesFor(ModRecipes.ASSEMBLING.get()));
    }

    /** The table runs them; lasers drive it. */
    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalysts(AssemblyCategory.TYPE, ModBlocks.ASSEMBLY_TABLE_ITEM.get(), ModBlocks.LASER_ITEM.get());
    }
}

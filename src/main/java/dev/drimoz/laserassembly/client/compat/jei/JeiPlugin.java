package dev.drimoz.laserassembly.client.compat.jei;

import dev.drimoz.laserassembly.LaserAssembly;
import dev.drimoz.laserassembly.registry.ModBlocks;
import dev.drimoz.laserassembly.registry.ModRecipes;
import mezz.jei.api.IModPlugin;
import dev.drimoz.laserassembly.client.table.AssemblyTableScreen;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.renderer.Rect2i;

import java.util.List;
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

    /** The side tabs are part of the screen: JEI's item list must not lie over them. */
    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGuiContainerHandler(AssemblyTableScreen.class, new IGuiContainerHandler<>() {
            @Override
            public List<Rect2i> getGuiExtraAreas(AssemblyTableScreen screen) {
                return screen.tabAreas();
            }
        });
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        runtime = jeiRuntime;
    }

    private static IJeiRuntime runtime;

    /** Opens JEI on the table's recipes, as a player clicking its catalyst would: for the store captures. */
    public static void showRecipes() {
        if (runtime != null) runtime.getRecipesGui().showTypes(List.of(AssemblyCategory.TYPE));
    }

    /** The table runs them; lasers drive it. */
    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalysts(AssemblyCategory.TYPE, ModBlocks.ASSEMBLY_TABLE_ITEM.get(), ModBlocks.LASER_ITEM.get());
    }
}

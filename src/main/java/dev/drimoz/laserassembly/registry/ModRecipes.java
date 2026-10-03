package dev.drimoz.laserassembly.registry;

import com.mojang.serialization.MapCodec;
import dev.drimoz.laserassembly.LaserAssembly;
import dev.drimoz.laserassembly.recipe.AssemblyRecipe;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModRecipes {

    private static final DeferredRegister<RecipeType<?>> TYPES = DeferredRegister.create(Registries.RECIPE_TYPE, LaserAssembly.MOD_ID);
    private static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS = DeferredRegister.create(Registries.RECIPE_SERIALIZER, LaserAssembly.MOD_ID);

    public static final DeferredHolder<RecipeType<?>, RecipeType<AssemblyRecipe>> ASSEMBLING =
            TYPES.register("assembling", RecipeType::simple);

    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<AssemblyRecipe>> ASSEMBLING_SERIALIZER =
            SERIALIZERS.register("assembling", () -> new RecipeSerializer<>() {
                @Override
                public MapCodec<AssemblyRecipe> codec() {
                    return AssemblyRecipe.CODEC;
                }

                @Override
                public StreamCodec<RegistryFriendlyByteBuf, AssemblyRecipe> streamCodec() {
                    return AssemblyRecipe.STREAM_CODEC;
                }
            });

    public static void register(IEventBus modBus) {
        TYPES.register(modBus);
        SERIALIZERS.register(modBus);
    }

    private ModRecipes() {}
}

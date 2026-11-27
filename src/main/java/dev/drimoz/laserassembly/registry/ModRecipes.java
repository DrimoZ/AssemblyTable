package dev.drimoz.laserassembly.registry;

import dev.drimoz.laserassembly.LaserAssembly;
import dev.drimoz.laserassembly.recipe.AssemblyRecipe;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ModRecipes {

    private static final DeferredRegister<RecipeType<?>> TYPES = DeferredRegister.create(Registries.RECIPE_TYPE, LaserAssembly.MOD_ID);
    private static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS = DeferredRegister.create(Registries.RECIPE_SERIALIZER, LaserAssembly.MOD_ID);

    public static final DeferredHolder<RecipeType<?>, RecipeType<AssemblyRecipe>> ASSEMBLING =
            TYPES.register("assembling", RecipeType::simple);

    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<AssemblyRecipe>> ASSEMBLING_SERIALIZER =
            SERIALIZERS.register("assembling", () -> new RecipeSerializer<>(AssemblyRecipe.CODEC, AssemblyRecipe.STREAM_CODEC));

    public static void register(IEventBus modBus) {
        TYPES.register(modBus);
        SERIALIZERS.register(modBus);
        // Since 1.21.2 clients get no recipes unless asked for: the picker, Jade and JEI all read them there.
        NeoForge.EVENT_BUS.addListener(OnDatapackSyncEvent.class, event -> event.sendRecipes(ASSEMBLING.get()));
    }

    // Lookup, on either side. One index per side: in single player both run in one process, and a
    // shared one would be rebuilt by each thread in turn.

    private static final Index SERVER = new Index();
    private static final Index CLIENT = new Index();
    private static RecipeMap received = RecipeMap.EMPTY;

    /** What the server sent this client, from {@code RecipesReceivedEvent}. */
    public static void received(RecipeMap recipes) {
        received = recipes;
    }

    /** Every assembling recipe, sorted by id: the order the picker shows and its buttons index. */
    public static List<RecipeHolder<AssemblyRecipe>> all(Level level) {
        return index(level).sorted;
    }

    public static @Nullable AssemblyRecipe byId(Level level, Identifier id) {
        RecipeHolder<AssemblyRecipe> holder = index(level).byId.get(id);
        return holder == null ? null : holder.value();
    }

    /** Rebuilt only when the recipes are: a /reload swaps the map. Ticks read it, so no lookup allocates. */
    private static Index index(Level level) {
        Index index = level instanceof ServerLevel ? SERVER : CLIENT;
        RecipeMap source = level instanceof ServerLevel server ? server.recipeAccess().recipeMap() : received;
        if (source != index.source) {
            Map<Identifier, RecipeHolder<AssemblyRecipe>> byId = new HashMap<>();
            for (RecipeHolder<AssemblyRecipe> holder : source.byType(ASSEMBLING.get())) byId.put(holder.id().identifier(), holder);
            index.sorted = byId.values().stream().sorted(Comparator.comparing(holder -> holder.id().identifier().toString())).toList();
            index.byId = byId;
            index.source = source;
        }
        return index;
    }

    private static final class Index {
        @Nullable RecipeMap source;
        Map<Identifier, RecipeHolder<AssemblyRecipe>> byId = Map.of();
        List<RecipeHolder<AssemblyRecipe>> sorted = List.of();
    }

    private ModRecipes() {}
}

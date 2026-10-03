package dev.drimoz.laserassembly.recipe;

import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import dev.drimoz.laserassembly.core.Tuning;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RecipeTest {

    static DataResult<AssemblyRecipe> parse(String json) {
        return AssemblyRecipe.CODEC.codec().parse(JsonOps.INSTANCE, JsonParser.parseString(json));
    }

    static final String INGREDIENTS = """
            "ingredients": [{ "ingredient": { "item": "minecraft:iron_ingot" }, "count": 3 }],
            "result": { "id": "minecraft:trident" }""";

    @Test
    void readsRawAndSteppedRecipesAndRefusesMistakes() {
        assertEquals(1, parse("{" + INGREDIENTS + ", \"energy\": 1000 }").getOrThrow().steps(Tuning.DEFAULTS).size());
        assertEquals(2, parse("{" + INGREDIENTS + """
                , "steps": [{ "beam": "laserassembly:cut", "energy": 500 },
                            { "beam": "laserassembly:weld", "energy": 500, "intensity": 3, "strict": true }] }""")
                .getOrThrow().steps(Tuning.DEFAULTS).size());

        assertTrue(parse("{" + INGREDIENTS + " }").isError(), "neither energy nor steps");
        assertTrue(parse("{" + INGREDIENTS + ", \"energy\": 1, \"steps\": [{ \"energy\": 1 }] }").isError(), "both");
        assertTrue(parse("{" + INGREDIENTS + ", \"steps\": [{ \"energy\": 1, \"strict\": true }] }").isError(), "strict raw step");
        assertTrue(parse("{" + INGREDIENTS + ", \"energy\": 1, \"intensity\": 5, \"heat_above\": 2 }").isError(), "heats below its minimum");
    }

    @Test
    void anItemFittingTwoIngredientsGoesWhereItIsNeeded() {
        AssemblyRecipe recipe = parse("""
                { "ingredients": [
                    { "ingredient": [{ "item": "minecraft:oak_planks" }, { "item": "minecraft:birch_planks" }] },
                    { "ingredient": { "item": "minecraft:oak_planks" } } ],
                  "result": { "id": "minecraft:stick" }, "energy": 1 }""").getOrThrow();

        // A greedy pass gives the oak to the first ingredient and finds no oak left for the second.
        List<ItemStack> fits = List.of(new ItemStack(Items.OAK_PLANKS), new ItemStack(Items.BIRCH_PLANKS));
        assertTrue(recipe.matches(new AssemblyRecipe.Input(fits), null));
        assertFalse(recipe.matches(new AssemblyRecipe.Input(List.of(new ItemStack(Items.BIRCH_PLANKS, 2))), null));
    }
}

package dev.drimoz.laserassembly.recipe;

import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import dev.drimoz.laserassembly.core.Tuning;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.RegistryOps;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RecipeTest {

    /** With registry access, as the recipe loader reads them: without it, an ingredient can only be a list. */
    static DataResult<AssemblyRecipe> parse(String json) {
        var ops = RegistryOps.create(JsonOps.INSTANCE, RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
        return AssemblyRecipe.CODEC.codec().parse(ops, JsonParser.parseString(json));
    }

    static final String INGREDIENTS = """
            "ingredients": [{ "ingredient": "minecraft:iron_ingot", "count": 3 }],
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
}

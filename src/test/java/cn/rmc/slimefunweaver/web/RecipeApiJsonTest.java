package cn.rmc.slimefunweaver.web;

import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class RecipeApiJsonTest {

    @Test
    public void parsesRecipePayloadWithWhitespaceAndSkipsNullRecipeType() {
        String json = "{ \"items\" : { \"TEST_ITEM\" : { \"recipes\" : [" +
                "{ \"type\" : \"slimefun:null\", \"input\" : [\"AIR\"], \"output\" : \"TEST_ITEM\" }," +
                "{ \"type\" : \"slimefun:enhanced_crafting_table\", \"input\" : [\"IRON_INGOT\", \"slimefun:SPECIAL\\nITEM\"], \"output\" : \"TEST_ITEM\", \"outputAmount\" : 3, \"processingTime\" : 12 }" +
                "] } } }";

        Map<String, List<Map<String, Object>>> parsed = RecipeApiHandler.parseRecipeSavePayload(json);

        assertEquals(1, parsed.size());
        assertEquals(1, parsed.get("TEST_ITEM").size());
        Map<String, Object> recipe = parsed.get("TEST_ITEM").get(0);
        assertEquals("slimefun:enhanced_crafting_table", recipe.get("type"));
        assertEquals(3, recipe.get("output-amount"));
        assertEquals(12, recipe.get("processing-time"));
        assertEquals("slimefun:SPECIAL\nITEM", ((List<?>) recipe.get("input")).get(1));
    }

    @Test
    public void omitsItemWhenAllSubmittedRecipesAreNullTypes() {
        String json = "{\"items\":{\"TEST_ITEM\":{\"recipes\":[{\"type\":\"null\"}]}}}";

        Map<String, List<Map<String, Object>>> parsed = RecipeApiHandler.parseRecipeSavePayload(json);

        assertFalse(parsed.containsKey("TEST_ITEM"));
    }
}

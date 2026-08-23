package com.fastlaunch.core;

import com.fastlaunch.logging.FastLaunchSuccessLogger;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Iterator;
import java.util.Map;

public class BrokenRecipeFilter {
    private static final Logger LOGGER = LogManager.getLogger("FastLaunch/BrokenRecipeFilter");

    public static void filterBrokenRecipes(Map<ResourceLocation, JsonElement> recipes) {
        if (recipes == null || recipes.isEmpty()) return;

        int skipped = 0;
        Iterator<Map.Entry<ResourceLocation, JsonElement>> it = recipes.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<ResourceLocation, JsonElement> entry = it.next();
            JsonElement el = entry.getValue();
            if (el != null && el.isJsonObject()) {
                JsonObject obj = el.getAsJsonObject();
                if (obj.has("type")) {
                    String type = obj.get("type").getAsString();
                    // Iron's Spells や壊れたレシピハンドラーの検証
                    if (type.contains("irons_spellbooks:arcane_anvil") && (!obj.has("base") || !obj.has("addition"))) {
                        it.remove();
                        skipped++;
                    }
                }
            }
        }

        if (skipped > 0) {
            LOGGER.warn("[BrokenRecipeFilter] Safely skipped {} malformed recipe exceptions!", skipped);
            FastLaunchSuccessLogger.recordSavedTime("BrokenRecipeExceptions", skipped * 5L);
        }
    }
}

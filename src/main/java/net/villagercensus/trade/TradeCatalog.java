package net.villagercensus.trade;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.villagercensus.Reference;

public class TradeCatalog
{
    private static final String RESOURCE = "/villagercensus/trade_categories.json";
    private static final TradeCatalog INSTANCE = new TradeCatalog();

    private final Map<String, List<TradeCategory>> byProfession = new HashMap<>();
    private boolean loaded;

    public static TradeCatalog get()
    {
        return INSTANCE;
    }

    public synchronized void ensureLoaded()
    {
        if (!this.loaded)
        {
            this.load();
            this.loaded = true;
        }
    }

    private void load()
    {
        try (InputStream in = TradeCatalog.class.getResourceAsStream(RESOURCE))
        {
            if (in == null)
            {
                Reference.logger().warn("Trade catalog resource {} not found", RESOURCE);
                return;
            }

            JsonElement root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8));

            if (!root.isJsonArray())
            {
                return;
            }

            for (JsonElement element : root.getAsJsonArray())
            {
                JsonObject professionObject = element.getAsJsonObject();
                String profession = professionObject.get("profession").getAsString();
                JsonArray categories = professionObject.getAsJsonArray("categories");
                List<TradeCategory> list = new ArrayList<>();

                for (JsonElement categoryElement : categories)
                {
                    JsonObject categoryObject = categoryElement.getAsJsonObject();
                    TradeCategory category = new TradeCategory();
                    category.profession = profession;
                    category.id = categoryObject.get("id").getAsString();
                    category.label = categoryObject.has("label") ? categoryObject.get("label").getAsString() : category.id;
                    category.cost1 = readStringList(categoryObject, "cost1");
                    category.cost2 = readStringList(categoryObject, "cost2");
                    category.result = readStringList(categoryObject, "result");
                    list.add(category);
                }

                this.byProfession.put(profession, list);
            }
        }
        catch (Exception e)
        {
            Reference.logger().warn("Failed to load the trade catalog", e);
        }
    }

    private static List<String> readStringList(JsonObject object, String key)
    {
        if (!object.has(key) || object.get(key).isJsonNull())
        {
            return List.of();
        }

        JsonElement element = object.get(key);

        if (element.isJsonArray())
        {
            List<String> list = new ArrayList<>();

            for (JsonElement entry : element.getAsJsonArray())
            {
                list.add(entry.getAsString());
            }

            return list;
        }

        return List.of(element.getAsString());
    }

    /**
     * Returns the first matching semantic category for the trade, or null if none matches.
     */
    public TradeCategory match(String professionId, String cost1, String cost2, String result)
    {
        this.ensureLoaded();
        List<TradeCategory> list = this.byProfession.get(professionId);

        if (list == null)
        {
            return null;
        }

        for (TradeCategory category : list)
        {
            if (category.matches(professionId, cost1, cost2, result))
            {
                return category;
            }
        }

        return null;
    }

    public LinkedHashMap<String, TradeCategory> allCategories()
    {
        this.ensureLoaded();
        LinkedHashMap<String, TradeCategory> all = new LinkedHashMap<>();

        for (List<TradeCategory> list : this.byProfession.values())
        {
            for (TradeCategory category : list)
            {
                all.put(category.profession + "/" + category.id, category);
            }
        }

        return all;
    }
}

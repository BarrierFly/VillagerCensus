package net.villagercensus.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import com.google.common.collect.ImmutableList;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import fi.dy.masa.malilib.config.ConfigUtils;
import fi.dy.masa.malilib.config.IConfigBase;
import fi.dy.masa.malilib.config.IConfigHandler;
import fi.dy.masa.malilib.config.options.ConfigBoolean;
import fi.dy.masa.malilib.config.options.ConfigInteger;
import fi.dy.masa.malilib.config.options.ConfigString;
import fi.dy.masa.malilib.config.options.ConfigStringList;
import fi.dy.masa.malilib.util.FileUtils;
import fi.dy.masa.malilib.util.data.json.JsonUtils;
import net.villagercensus.Reference;

public class Configs implements IConfigHandler
{
    private static final String CONFIG_FILE_NAME = Reference.MOD_ID + ".json";
    private static final String GENERIC_KEY = Reference.MOD_ID + ".config.generic";

    public static class Generic
    {
        public static final ConfigString TRIGGER_ITEM = new ConfigString("triggerItem", "minecraft:enchanted_book").apply(GENERIC_KEY);
        public static final ConfigBoolean RECORD_ALL_TRADES = new ConfigBoolean("recordAllTrades", true).apply(GENERIC_KEY);
        public static final ConfigStringList SELECTED_CATEGORIES = new ConfigStringList("selectedCategories", ImmutableList.of()).apply(GENERIC_KEY);
        public static final ConfigBoolean GLOWING_MARKER = new ConfigBoolean("glowingMarker", true).apply(GENERIC_KEY);
        public static final ConfigBoolean RECORD_COORDINATES = new ConfigBoolean("recordCoordinates", true).apply(GENERIC_KEY);
        public static final ConfigBoolean HUD_ENABLED = new ConfigBoolean("hudEnabled", true).apply(GENERIC_KEY);
        public static final ConfigInteger OFFERS_TIMEOUT = new ConfigInteger("offersTimeoutTicks", 10, 1, 200).apply(GENERIC_KEY);
        public static final ConfigBoolean AUTO_SAVE_DRAFT = new ConfigBoolean("autoSaveDraft", true).apply(GENERIC_KEY);
        public static final ConfigBoolean DEBUG = new ConfigBoolean("debugLog", false).apply(GENERIC_KEY);

        public static final ImmutableList<IConfigBase> OPTIONS = ImmutableList.of(
                TRIGGER_ITEM,
                RECORD_ALL_TRADES,
                SELECTED_CATEGORIES,
                GLOWING_MARKER,
                RECORD_COORDINATES,
                HUD_ENABLED,
                OFFERS_TIMEOUT,
                AUTO_SAVE_DRAFT,
                DEBUG
        );
    }

    public static void loadFromFile()
    {
        Path configFile = FileUtils.getConfigDirectoryAsPath().resolve(CONFIG_FILE_NAME);

        if (Files.exists(configFile) && Files.isReadable(configFile))
        {
            JsonElement element = JsonUtils.parseJsonFile(configFile);

            if (element != null && element.isJsonObject())
            {
                JsonObject root = element.getAsJsonObject();
                ConfigUtils.readConfigBase(root, "Generic", Generic.OPTIONS);
            }
        }
    }

    public static void saveToFile()
    {
        Path dir = FileUtils.getConfigDirectoryAsPath();

        if (!Files.exists(dir))
        {
            FileUtils.createDirectoriesIfMissing(dir);
        }

        if (Files.isDirectory(dir))
        {
            JsonObject root = new JsonObject();
            ConfigUtils.writeConfigBase(root, "Generic", Generic.OPTIONS);
            JsonUtils.writeJsonToFile(root, dir.resolve(CONFIG_FILE_NAME));
        }
    }

    public static boolean debug()
    {
        return Generic.DEBUG.getBooleanValue();
    }

    public static Optional<String> selectedCategory(String id)
    {
        return Optional.of(id);
    }

    @Override
    public void load()
    {
        loadFromFile();
    }

    @Override
    public void save()
    {
        saveToFile();
    }
}

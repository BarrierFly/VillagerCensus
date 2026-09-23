package net.villagercensus.config;

import com.google.common.collect.ImmutableList;

import fi.dy.masa.malilib.config.options.ConfigHotkey;
import net.villagercensus.Reference;

public class Hotkeys
{
    private static final String HOTKEY_KEY = Reference.MOD_ID + ".config.hotkeys";

    public static final ConfigHotkey OPEN_CONFIG_GUI = new ConfigHotkey("openConfigGui", "").apply(HOTKEY_KEY);
    public static final ConfigHotkey TOGGLE_STATS = new ConfigHotkey("toggleStats", "").apply(HOTKEY_KEY);
    public static final ConfigHotkey UNDO_LAST = new ConfigHotkey("undoLast", "").apply(HOTKEY_KEY);

    public static final ImmutableList<ConfigHotkey> HOTKEY_LIST = ImmutableList.of(
            OPEN_CONFIG_GUI,
            TOGGLE_STATS,
            UNDO_LAST
    );
}

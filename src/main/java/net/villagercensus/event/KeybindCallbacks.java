package net.villagercensus.event;

import fi.dy.masa.malilib.config.options.ConfigHotkey;
import fi.dy.masa.malilib.hotkeys.IHotkeyCallback;
import fi.dy.masa.malilib.hotkeys.IKeybind;
import fi.dy.masa.malilib.hotkeys.KeyAction;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.interfaces.IClientTickHandler;
import net.minecraft.client.Minecraft;
import net.villagercensus.census.CensusManager;
import net.villagercensus.config.Hotkeys;
import net.villagercensus.gui.GuiConfigs;

public class KeybindCallbacks implements IHotkeyCallback, IClientTickHandler
{
    private static final KeybindCallbacks INSTANCE = new KeybindCallbacks();

    public static KeybindCallbacks getInstance()
    {
        return INSTANCE;
    }

    private KeybindCallbacks()
    {
    }

    public void setCallbacks()
    {
        for (ConfigHotkey hotkey : Hotkeys.HOTKEY_LIST)
        {
            hotkey.getKeybind().setCallback(this);
        }
    }

    @Override
    public boolean onKeyAction(KeyAction action, IKeybind key)
    {
        if (key == Hotkeys.OPEN_CONFIG_GUI.getKeybind())
        {
            GuiBase.openGui(new GuiConfigs());
            return true;
        }
        else if (key == Hotkeys.TOGGLE_STATS.getKeybind())
        {
            CensusManager.getInstance().togglePaused();
            return true;
        }
        else if (key == Hotkeys.UNDO_LAST.getKeybind())
        {
            CensusManager.getInstance().undo();
            return true;
        }

        return false;
    }

    @Override
    public void onClientTick(Minecraft mc)
    {
        CensusManager.getInstance().onClientTick(mc);
    }
}

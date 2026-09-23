package net.villagercensus.gui;

import java.util.ArrayList;
import java.util.List;

import fi.dy.masa.malilib.config.IConfigBase;
import fi.dy.masa.malilib.gui.GuiConfigsBase;
import net.villagercensus.Reference;
import net.villagercensus.config.Configs;
import net.villagercensus.config.Hotkeys;

public class GuiConfigs extends GuiConfigsBase
{
    public GuiConfigs()
    {
        super(10, 50, Reference.MOD_ID, null, "villagercensus.gui.title.configs", Reference.MOD_VERSION);
    }

    @Override
    public List<ConfigOptionWrapper> getConfigs()
    {
        List<IConfigBase> configs = new ArrayList<>();
        configs.addAll(Configs.Generic.OPTIONS);
        configs.addAll(Hotkeys.HOTKEY_LIST);
        return ConfigOptionWrapper.createFor(configs);
    }
}

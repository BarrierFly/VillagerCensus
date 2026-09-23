package net.villagercensus.compat.modmenu;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

import net.villagercensus.gui.GuiConfigs;

public class ModMenuImpl implements ModMenuApi
{
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory()
    {
        return screen ->
        {
            GuiConfigs gui = new GuiConfigs();
            gui.setParent(screen);
            return gui;
        };
    }
}

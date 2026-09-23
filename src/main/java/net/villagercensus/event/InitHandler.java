package net.villagercensus.event;

import fi.dy.masa.malilib.config.ConfigManager;
import fi.dy.masa.malilib.event.InputEventHandler;
import fi.dy.masa.malilib.event.RenderEventHandler;
import fi.dy.masa.malilib.event.TickHandler;
import fi.dy.masa.malilib.event.WorldLoadHandler;
import fi.dy.masa.malilib.interfaces.IInitializationHandler;
import fi.dy.masa.malilib.registry.Registry;
import fi.dy.masa.malilib.util.data.ModInfo;
import net.villagercensus.Reference;
import net.villagercensus.config.Configs;
import net.villagercensus.gui.GuiConfigs;
import net.villagercensus.render.CensusHud;

public class InitHandler implements IInitializationHandler
{
    @Override
    public void registerModHandlers()
    {
        ConfigManager.getInstance().registerConfigHandler(Reference.MOD_ID, new Configs());
        Registry.CONFIG_SCREEN.registerConfigScreenFactory(
                new ModInfo(Reference.MOD_ID, Reference.MOD_NAME, GuiConfigs::new)
        );

        InputEventHandler.getKeybindManager().registerKeybindProvider(new InputHandler());

        WorldLoadListener worldLoadListener = new WorldLoadListener();
        WorldLoadHandler.getInstance().registerWorldLoadPreHandler(worldLoadListener);
        WorldLoadHandler.getInstance().registerWorldLoadPostHandler(worldLoadListener);

        TickHandler.getInstance().registerClientTickHandler(KeybindCallbacks.getInstance());
        KeybindCallbacks.getInstance().setCallbacks();

        //? if >= 26.1 {
        RenderEventHandler.getInstance().registerInGameGuiRenderer(new CensusHud());
        //?} else {
        /*RenderEventHandler.getInstance().registerGameOverlayRenderer(new CensusHud());
        *//*?}*/

        Reference.logger().info("Villager Census initialized ({} {})", Reference.MOD_NAME, Reference.MOD_VERSION);
    }
}

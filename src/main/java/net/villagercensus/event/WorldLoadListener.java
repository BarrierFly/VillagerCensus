package net.villagercensus.event;

import fi.dy.masa.malilib.interfaces.IWorldLoadListener;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.villagercensus.census.CensusManager;

public class WorldLoadListener implements IWorldLoadListener
{
    @Override
    public void onWorldLoadPre(ClientLevel worldBefore, ClientLevel worldAfter, Minecraft mc)
    {
        CensusManager.getInstance().onWorldLoadPre(worldBefore, worldAfter);
    }

    @Override
    public void onWorldLoadPost(ClientLevel worldBefore, ClientLevel worldAfter, Minecraft mc)
    {
        CensusManager.getInstance().onWorldLoadPost(worldBefore, worldAfter);
    }
}

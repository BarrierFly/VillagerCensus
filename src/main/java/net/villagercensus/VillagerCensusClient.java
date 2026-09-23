package net.villagercensus;

import net.fabricmc.api.ClientModInitializer;
import net.villagercensus.command.CensusCommand;

public class VillagerCensusClient implements ClientModInitializer
{
    @Override
    public void onInitializeClient()
    {
        CensusCommand.register();
    }
}

package net.villagercensus;

import net.fabricmc.api.ClientModInitializer;
/*? if >= 26.1 {*/
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.resources.Identifier;
/*?} else {*/
/*import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
*//*?}*/
import net.villagercensus.command.CensusCommand;
import net.villagercensus.render.CensusHud;

public class VillagerCensusClient implements ClientModInitializer
{
    private static final CensusHud HUD = new CensusHud();

    @Override
    public void onInitializeClient()
    {
        CensusCommand.register();

        //? if >= 26.1 {
        HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR,
                Identifier.fromNamespaceAndPath(Reference.MOD_ID, "census_hud"), HUD::extractRenderState);
        //?} else {
        /*HudRenderCallback.EVENT.register((graphics, deltaTracker) -> HUD.render(graphics, deltaTracker));
        *//*?}*/

        Reference.logger().info("Villager Census HUD registered");
    }
}

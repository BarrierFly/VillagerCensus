package net.villagercensus.util;

import net.minecraft.client.Minecraft;
//? if >= 26.2 {
import java.util.Optional;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.TeamColor;
//?} else {
/*import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.scores.PlayerTeam;
*//*?}*/

public class Compat
{
    //? if >= 26.2 {
    public static Screen currentScreen(Minecraft mc)
    {
        return mc.gui.screen();
    }
    //?} else {
    /*public static Screen currentScreen(Minecraft mc)
    {
        return mc.screen;
    }
    *//*?}*/

    /**
     * 26.2 switched team colors from ChatFormatting to the TeamColor enum wrapped in an Optional.
     */
    public static void setTeamColor(PlayerTeam team, boolean good)
    {
        //? if >= 26.2 {
        team.setColor(Optional.of(good ? TeamColor.GREEN : TeamColor.RED));
        //?} else {
        /*team.setColor(good ? ChatFormatting.GREEN : ChatFormatting.RED);
        *//*?}*/
    }
}

package net.villagercensus.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

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
}

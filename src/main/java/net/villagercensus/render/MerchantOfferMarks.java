package net.villagercensus.render;

import net.minecraft.client.gui.screens.inventory.MerchantScreen;
//? if >= 26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else {
/*import net.minecraft.client.gui.GuiGraphics;
*//*?}*/
import net.minecraft.world.item.trading.MerchantOffers;
import net.villagercensus.census.CensusManager;

/**
 * Draws a green frame around the competitive enchanted-book rows of an open merchant screen.
 * Only active while the book-competitiveness marker mode is on; the flags come from
 * {@link CensusManager#bookScreenFlags}.
 */
public class MerchantOfferMarks
{
    private static final int MARK_COLOR = 0xFF2ECC40;
    private static final int SCREEN_WIDTH = 276;
    private static final int SCREEN_HEIGHT = 166;
    private static final int ROW_HEIGHT = 20;
    private static final int ROW_TOP = 17;
    private static final int ROW_MAX = 7;

    //? if >= 26.1 {
    public static void draw(GuiGraphicsExtractor graphics, MerchantScreen screen, int scrollOff)
    //?} else {
    /*public static void draw(GuiGraphics graphics, MerchantScreen screen, int scrollOff)
    *//*?}*/
    {
        MerchantOffers offers = screen.getMenu().getOffers();
        boolean[] flags = CensusManager.getInstance().bookScreenFlags(offers, screen.getMenu().containerId);

        if (flags == null)
        {
            return;
        }

        int xo = (screen.width - SCREEN_WIDTH) / 2;
        int yo = (screen.height - SCREEN_HEIGHT) / 2;
        int visible = Math.min(offers.size(), ROW_MAX);

        for (int row = 0; row < visible; row++)
        {
            int index = row + scrollOff;

            if (index >= flags.length || !flags[index])
            {
                continue;
            }

            int x0 = xo + 4;
            int y0 = yo + ROW_TOP + row * ROW_HEIGHT + 1;
            int x1 = xo + 94;
            int y1 = y0 + ROW_HEIGHT;

            graphics.fill(x0, y0, x1, y0 + 1, MARK_COLOR);
            graphics.fill(x0, y1 - 1, x1, y1, MARK_COLOR);
            graphics.fill(x0, y0, x0 + 1, y1, MARK_COLOR);
            graphics.fill(x1 - 1, y0, x1, y1, MARK_COLOR);
        }
    }
}

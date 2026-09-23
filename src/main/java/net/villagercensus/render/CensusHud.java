package net.villagercensus.render;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
/*? if >= 26.1 {*/
import net.minecraft.client.gui.GuiGraphicsExtractor;
/*?} else {*/
/*import net.minecraft.client.gui.GuiGraphics;
*//*?}*/
import net.villagercensus.census.CensusManager;
import net.villagercensus.census.CensusSession;
import net.villagercensus.census.VillagerRecord;
import net.villagercensus.config.Configs;
import net.villagercensus.util.Compat;

public class CensusHud
{
    private static final int COLOR = 0xFFFFFFFF;

    //? if >= 26.1 {
    public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker)
    {
        Font font = Minecraft.getInstance().font;
        int y = 4;

        for (String line : this.buildLines())
        {
            graphics.text(font, line, 5, y + 1, 0xFF000000);
            graphics.text(font, line, 4, y, COLOR);
            y += 10;
        }
    }
    //?} else {
    /*public void render(GuiGraphics graphics, DeltaTracker deltaTracker)
    {
        Font font = Minecraft.getInstance().font;
        int y = 4;

        for (String line : this.buildLines())
        {
            graphics.drawString(font, line, 4, y, COLOR, true);
            y += 10;
        }
    }
    *//*?}*/

    private List<String> buildLines()
    {
        List<String> lines = new ArrayList<>();

        if (!Configs.Generic.HUD_ENABLED.getBooleanValue())
        {
            return lines;
        }

        Minecraft mc = Minecraft.getInstance();

        if (mc.player == null || Compat.currentScreen(mc) != null)
        {
            return lines;
        }

        CensusManager manager = CensusManager.getInstance();
        CensusSession session = manager.getSession();

        if (session == null)
        {
            if (manager.getDraftTotal() > 0)
            {
                lines.add("Census draft: " + manager.getDraftName());
                lines.add("loaded " + manager.getDraftLoaded() + "/" + manager.getDraftTotal());
                lines.add("/census resume  |  /census discard");
            }

            return lines;
        }

        lines.add("Census: " + session.rawName + " @ " + session.dimension + (session.paused ? " [paused]" : ""));
        lines.add("count " + session.totalCount() + " | professions " + session.professionCounts().size()
                + " | babies " + session.babyCount());

        if (manager.getPendingUpdate() != null)
        {
            lines.add("pending update: right-click again to confirm");
        }

        if (manager.getVerifyTotal() > 0)
        {
            lines.add("verify " + manager.getVerifyLoaded() + "/" + manager.getVerifyTotal());
        }

        VillagerRecord last = session.getLastTarget();

        if (last != null)
        {
            lines.add(last.baby ? "last: baby" : "last: " + last.professionId + " Lv" + last.level);
            lines.add("  pos " + last.blockX + "," + last.blockY + "," + last.blockZ
                    + "  hp " + (int) last.health + "/" + (int) last.maxHealth);

            if (last.remark != null && !last.remark.isEmpty())
            {
                lines.add("  remark: " + last.remark);
            }
        }

        return lines;
    }
}

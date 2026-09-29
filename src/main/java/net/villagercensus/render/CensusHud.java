package net.villagercensus.render;

import java.util.ArrayList;
import java.util.List;

import fi.dy.masa.malilib.util.StringUtils;
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
                lines.add(StringUtils.translate("villagercensus.hud.draft", manager.getDraftName()));
                lines.add(StringUtils.translate("villagercensus.hud.draft_loaded",
                        manager.getDraftLoaded(), manager.getDraftTotal()));
                lines.add(StringUtils.translate("villagercensus.hud.draft_commands"));
            }

            return lines;
        }

        lines.add(StringUtils.translate("villagercensus.hud.session", session.rawName, session.dimension)
                + (session.paused ? StringUtils.translate("villagercensus.hud.paused") : ""));
        lines.add(StringUtils.translate("villagercensus.hud.count", session.totalCount(),
                session.professionCounts().size(), session.babyCount()));

        if (Configs.Generic.GLOWING_MARKER.getBooleanValue())
        {
            lines.add(StringUtils.translate(manager.isReverseMarkers()
                    ? "villagercensus.hud.marker_reversed"
                    : "villagercensus.hud.marker_normal"));
        }

        if (manager.getPendingUpdate() != null)
        {
            lines.add(StringUtils.translate("villagercensus.hud.pending_update"));
        }

        if (manager.getVerifyTotal() > 0)
        {
            lines.add(StringUtils.translate("villagercensus.hud.verify",
                    manager.getVerifyLoaded(), manager.getVerifyTotal()));
        }

        VillagerRecord last = session.getLastTarget();

        if (last != null)
        {
            lines.add(last.baby
                    ? StringUtils.translate("villagercensus.hud.last_baby")
                    : StringUtils.translate("villagercensus.hud.last", last.professionId, last.level));
            lines.add(last.hasCoordinates
                    ? StringUtils.translate("villagercensus.hud.pos_hp",
                            last.blockX, last.blockY, last.blockZ, (int) last.health, (int) last.maxHealth)
                    : StringUtils.translate("villagercensus.hud.hp",
                            (int) last.health, (int) last.maxHealth));

            if (last.remark != null && !last.remark.isEmpty())
            {
                lines.add(StringUtils.translate("villagercensus.hud.remark", last.remark));
            }
        }

        return lines;
    }
}

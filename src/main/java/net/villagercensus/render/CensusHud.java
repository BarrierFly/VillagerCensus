package net.villagercensus.render;

import java.util.ArrayList;
import java.util.List;

import fi.dy.masa.malilib.config.HudAlignment;
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
    private static final int SHADOW = 0xFF000000;
    private static final int LINE_HEIGHT = 10;

    //? if >= 26.1 {
    public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker)
    {
        List<String> lines = this.buildLines();

        if (lines.isEmpty())
        {
            return;
        }

        Font font = Minecraft.getInstance().font;
        Layout layout = this.layout(font, lines, graphics.guiWidth(), graphics.guiHeight());

        for (int i = 0; i < lines.size(); i++)
        {
            int y = layout.y + i * LINE_HEIGHT;
            graphics.text(font, lines.get(i), layout.x + 1, y + 1, SHADOW);
            graphics.text(font, lines.get(i), layout.x, y, COLOR);
        }
    }
    //?} else {
    /*public void render(GuiGraphics graphics, DeltaTracker deltaTracker)
    {
        List<String> lines = this.buildLines();

        if (lines.isEmpty())
        {
            return;
        }

        Font font = Minecraft.getInstance().font;
        Layout layout = this.layout(font, lines, graphics.guiWidth(), graphics.guiHeight());

        for (int i = 0; i < lines.size(); i++)
        {
            int y = layout.y + i * LINE_HEIGHT;
            graphics.drawString(font, lines.get(i), layout.x + 1, y + 1, SHADOW, false);
            graphics.drawString(font, lines.get(i), layout.x, y, COLOR, false);
        }
    }
    *//*?}*/

    private Layout layout(Font font, List<String> lines, int screenWidth, int screenHeight)
    {
        HudAlignment alignment = (HudAlignment) Configs.Generic.HUD_POSITION.getOptionListValue();
        int offsetX = Configs.Generic.HUD_OFFSET_X.getIntegerValue();
        int offsetY = Configs.Generic.HUD_OFFSET_Y.getIntegerValue();
        int maxWidth = 0;

        for (String line : lines)
        {
            maxWidth = Math.max(maxWidth, font.width(line));
        }

        int blockHeight = lines.size() * LINE_HEIGHT;
        int x;
        int y;

        switch (alignment)
        {
            case TOP_RIGHT, BOTTOM_RIGHT -> x = screenWidth - offsetX - maxWidth;
            case CENTER -> x = (screenWidth - maxWidth) / 2 + offsetX;
            default -> x = offsetX;
        }

        switch (alignment)
        {
            case BOTTOM_LEFT, BOTTOM_RIGHT -> y = screenHeight - offsetY - blockHeight;
            case CENTER -> y = (screenHeight - blockHeight) / 2 + offsetY;
            default -> y = offsetY;
        }

        return new Layout(x, y);
    }

    private record Layout(int x, int y)
    {
    }

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
            if (manager.getDraftTotal() > 0 && !manager.isDraftPromptPending())
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

        if (Configs.Generic.GLOWING_MARKER.getBooleanValue() && manager.markersVisible(mc))
        {
            lines.add(StringUtils.translate(manager.isBookMarkers()
                    ? "villagercensus.hud.marker_books"
                    : manager.isReverseMarkers()
                            ? "villagercensus.hud.marker_reversed"
                            : "villagercensus.hud.marker_normal"));
        }

        if (manager.isWaitingForOffers())
        {
            lines.add(StringUtils.translate("villagercensus.hud.waiting_offers",
                    manager.getWaitingTicks(), Configs.Generic.OFFERS_TIMEOUT.getIntegerValue()));
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
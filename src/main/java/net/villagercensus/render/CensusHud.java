package net.villagercensus.render;

import java.util.ArrayList;
import java.util.List;

import fi.dy.masa.malilib.interfaces.IRenderer;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.render.RenderUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.util.profiling.ProfilerFiller;
import net.villagercensus.census.CensusManager;
import net.villagercensus.census.CensusSession;
import net.villagercensus.census.VillagerRecord;
import net.villagercensus.config.Configs;
import net.villagercensus.util.Compat;

public class CensusHud implements IRenderer
{
    //? if >= 26.1 {
    @Override
    public void onExtractGuiOverlayPost(GuiContext ctx, float partialTicks, ProfilerFiller profiler)
    {
        this.render(ctx);
    }
    //?} else {
    /*@Override
    public void onRenderGameOverlayPostAdvanced(GuiContext ctx, float partialTicks, ProfilerFiller profiler)
    {
        this.render(ctx);
    }
    *//*?}*/

    private void render(GuiContext ctx)
    {
        if (!Configs.Generic.HUD_ENABLED.getBooleanValue())
        {
            return;
        }

        CensusManager manager = CensusManager.getInstance();
        CensusSession session = manager.getSession();

        if (session == null)
        {
            return;
        }

        Minecraft mc = Minecraft.getInstance();

        if (mc.player == null || Compat.currentScreen(mc) != null)
        {
            return;
        }

        List<String> lines = new ArrayList<>();
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

        final int x = 4;
        final int y = 4;
        final int lineHeight = 10;
        int width = 0;

        for (String line : lines)
        {
            width = Math.max(width, mc.font.width(line));
        }

        width = Math.max(width, 150) + 8;
        int height = lines.size() * lineHeight + 6;

        RenderUtils.drawRect(ctx, x, y, width, height, 0x90000000);

        int textY = y + 3;

        for (String line : lines)
        {
            RenderUtils.renderText(ctx, x + 4, textY, 0xFFFFFFFF, line);
            textY += lineHeight;
        }
    }
}

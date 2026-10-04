package net.villagercensus.gui;

import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.button.ButtonBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.button.IButtonActionListener;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.util.StringUtils;
import net.villagercensus.census.CensusManager;
import net.villagercensus.data.WorldId;

/**
 * Shown after joining a world that still holds a resumable draft (planning Q31). Offers to
 * continue the session, keep the draft for later, or archive it. Villagers that are not loaded
 * are only reported as "not verified this time" and are never dropped from the draft.
 */
public class GuiDraftResume extends GuiBase
{
    private static final int LINE_X = 20;
    private static final int FIRST_LINE_Y = 28;
    private static final int LINE_SPACING = 12;
    private static final int NOTE_COLOR = 0xFFAAAA00;

    private final CensusManager manager;

    public GuiDraftResume(CensusManager manager)
    {
        this.manager = manager;
        this.setTitle(StringUtils.translate("villagercensus.gui.title.resume"));
    }

    @Override
    public void initGui()
    {
        super.initGui();

        int x = 12;
        int y = this.height - 30;
        int gap = 4;

        x += this.addButton(x, y, Action.CONTINUE) + gap;
        x += this.addButton(x, y, Action.DEFER) + gap;
        this.addButton(x, y, Action.DISCARD);
    }

    private int addButton(int x, int y, Action action)
    {
        String label = StringUtils.translate(action.getKey());
        int width = this.getStringWidth(label) + 10;
        this.addButton(new ButtonGeneric(x, y, width, 20, label), new Listener(action, this.manager));
        return width;
    }

    @Override
    public void drawContents(GuiContext ctx, int mouseX, int mouseY, float partialTicks)
    {
        super.drawContents(ctx, mouseX, mouseY, partialTicks);

        int y = FIRST_LINE_Y;
        y = this.line(ctx, y, 0xFFFFFFFF, "villagercensus.gui.resume.file",
                this.manager.getDraftName() != null ? this.manager.getDraftName() : "-");
        y = this.line(ctx, y, 0xFFFFFFFF, "villagercensus.gui.resume.world", WorldId.current());
        y = this.line(ctx, y, 0xFFFFFFFF, "villagercensus.gui.resume.dimension",
                CensusManager.currentDimension());
        y = this.line(ctx, y, 0xFFFFFFFF, "villagercensus.gui.resume.loaded",
                this.manager.getDraftLoaded(), this.manager.getDraftTotal());
        this.line(ctx, y, NOTE_COLOR, "villagercensus.gui.resume.unverified_note");
    }

    private int line(GuiContext ctx, int y, int color, String key, Object... args)
    {
        this.drawStringWithShadow(ctx, StringUtils.translate(key, args), LINE_X, y, color);
        return y + LINE_SPACING;
    }

    private enum Action
    {
        CONTINUE("villagercensus.gui.button.resume_continue"),
        DEFER("villagercensus.gui.button.resume_defer"),
        DISCARD("villagercensus.gui.button.resume_discard");

        private final String key;

        Action(String key)
        {
            this.key = key;
        }

        String getKey()
        {
            return this.key;
        }
    }

    private record Listener(Action action, CensusManager manager) implements IButtonActionListener
    {
        @Override
        public void actionPerformedWithButton(ButtonBase button, int mouseButton)
        {
            switch (this.action)
            {
                case CONTINUE -> {
                    if (this.manager.resumeSession())
                    {
                        GuiBase.openGui(null);
                    }
                }
                case DEFER -> {
                    this.manager.deferDraft();
                    GuiBase.openGui(null);
                }
                case DISCARD -> {
                    this.manager.discardDraft();
                    GuiBase.openGui(null);
                }
            }
        }
    }
}
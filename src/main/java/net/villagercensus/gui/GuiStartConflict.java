package net.villagercensus.gui;

import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.GuiTextFieldGeneric;
import fi.dy.masa.malilib.gui.button.ButtonBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.button.IButtonActionListener;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.util.StringUtils;
import net.villagercensus.census.CensusManager;
import net.villagercensus.census.CensusSession;
import net.villagercensus.data.DraftStorage;
import net.villagercensus.data.ReportWriter;
import net.villagercensus.data.WorldId;

/**
 * Confirmation screen for {@code /census start <name>} when the name collides with an existing
 * draft or report (planning Q37). The options depend on the collision and the exact file name the
 * session will write is previewed before anything is created.
 */
public class GuiStartConflict extends GuiBase
{
    private static final int LINE_X = 20;
    private static final int FIRST_LINE_Y = 28;
    private static final int LINE_SPACING = 12;
    private static final int NOTE_COLOR = 0xFFAAAA00;
    private static final int RENAME_FIELD_WIDTH = 160;
    private static final int RENAME_FIELD_HEIGHT = 20;

    private final CensusManager manager;
    private final DraftStorage.NameConflict conflict;
    private final String requestedName;

    private GuiTextFieldGeneric renameField;

    public GuiStartConflict(CensusManager manager)
    {
        this.manager = manager;
        this.conflict = manager.getPendingConflict();
        this.requestedName = manager.getPendingStartName();
        this.setTitle(StringUtils.translate("villagercensus.gui.title.start_conflict"));
    }

    @Override
    public void initGui()
    {
        super.initGui();

        boolean rename = this.conflict != null
                && this.conflict.kind == DraftStorage.ConflictKind.COMPLETED_REPORT;

        int x = 12;
        int y = this.height - 30;
        int gap = 4;

        if (rename)
        {
            this.renameField = new GuiTextFieldGeneric(x, y, RENAME_FIELD_WIDTH, RENAME_FIELD_HEIGHT, this.font);
            this.renameField.setMaxLength(64);
            this.renameField.setValue(this.requestedName != null ? this.requestedName : "");
            this.addTextField(this.renameField, null);
            x += RENAME_FIELD_WIDTH + gap;
        }

        for (CensusManager.StartChoice choice : this.choices())
        {
            String label = StringUtils.translate(choice.getKey());
            int width = this.getStringWidth(label) + 10;
            this.addButton(new ButtonGeneric(x, y, width, 20, label), new Listener(choice, this));
            x += width + gap;
        }
    }

    private CensusManager.StartChoice[] choices()
    {
        DraftStorage.ConflictKind kind = this.conflict != null
                ? this.conflict.kind
                : DraftStorage.ConflictKind.NONE;

        return switch (kind)
        {
            case DRAFT -> new CensusManager.StartChoice[]
            {
                CensusManager.StartChoice.CONTINUE_DRAFT,
                CensusManager.StartChoice.RESTART,
                CensusManager.StartChoice.CANCEL
            };
            case DIMENSION_MISMATCH -> new CensusManager.StartChoice[]
            {
                CensusManager.StartChoice.FORCE_CONTINUE,
                CensusManager.StartChoice.ARCHIVE_AND_START,
                CensusManager.StartChoice.CANCEL
            };
            case CORRUPT -> new CensusManager.StartChoice[]
            {
                CensusManager.StartChoice.ARCHIVE_AND_START,
                CensusManager.StartChoice.CANCEL
            };
            case COMPLETED_REPORT -> new CensusManager.StartChoice[]
            {
                CensusManager.StartChoice.KEEP_NAME,
                CensusManager.StartChoice.RENAME,
                CensusManager.StartChoice.CANCEL
            };
            default -> new CensusManager.StartChoice[]
            {
                CensusManager.StartChoice.KEEP_NAME,
                CensusManager.StartChoice.CANCEL
            };
        };
    }

    @Override
    public void drawContents(GuiContext ctx, int mouseX, int mouseY, float partialTicks)
    {
        super.drawContents(ctx, mouseX, mouseY, partialTicks);

        int y = FIRST_LINE_Y;
        y = this.line(ctx, y, "villagercensus.gui.conflict.name",
                this.requestedName != null ? this.requestedName : "-");
        y = this.line(ctx, y, "villagercensus.gui.conflict.kind",
                StringUtils.translate(this.kindKey()));

        if (this.conflict != null && this.conflict.path != null)
        {
            y = this.line(ctx, y, "villagercensus.gui.conflict.existing",
                    this.conflict.path.getFileName().toString());
        }

        if (this.conflict != null && this.conflict.recordedDimension != null)
        {
            y = this.line(ctx, y, "villagercensus.gui.conflict.recorded_dimension",
                    this.conflict.recordedDimension, CensusManager.currentDimension());
        }

        if (this.conflict != null && this.conflict.reportCount > 1)
        {
            y = this.line(ctx, y, "villagercensus.gui.conflict.report_count", this.conflict.reportCount);
        }

        this.line(ctx, y, NOTE_COLOR, "villagercensus.gui.conflict.preview", this.filePreview());
    }

    private String kindKey()
    {
        DraftStorage.ConflictKind kind = this.conflict != null
                ? this.conflict.kind
                : DraftStorage.ConflictKind.NONE;

        return "villagercensus.gui.conflict.kind." + kind.name().toLowerCase(java.util.Locale.ROOT);
    }

    /**
     * The exact report file name the new session will write. Uses the same helpers the report
     * writer does, so the preview always matches what lands on disk.
     */
    private String filePreview()
    {
        String name = this.chosenName();
        CensusSession probe = new CensusSession();
        probe.rawName = name;
        probe.safeName = WorldId.safe(name);
        probe.worldId = WorldId.current();
        probe.dimension = CensusManager.currentDimension();
        return ReportWriter.reportPath(probe).getFileName().toString();
    }

    private String chosenName()
    {
        if (this.renameField != null)
        {
            String typed = this.renameField.getValue();

            if (typed != null && !typed.trim().isEmpty())
            {
                return typed.trim();
            }
        }

        return this.requestedName != null ? this.requestedName : "unnamed";
    }

    private int line(GuiContext ctx, int y, String key, Object... args)
    {
        return this.line(ctx, y, 0xFFFFFFFF, key, args);
    }

    private int line(GuiContext ctx, int y, int color, String key, Object... args)
    {
        this.drawStringWithShadow(ctx, StringUtils.translate(key, args), LINE_X, y, color);
        return y + LINE_SPACING;
    }

    private record Listener(CensusManager.StartChoice choice, GuiStartConflict gui) implements IButtonActionListener
    {
        @Override
        public void actionPerformedWithButton(ButtonBase button, int mouseButton)
        {
            if (this.choice == CensusManager.StartChoice.CANCEL)
            {
                this.gui.manager.resolveStartConflict(CensusManager.StartChoice.CANCEL, null);
            }
            else
            {
                this.gui.manager.resolveStartConflict(this.choice, this.gui.chosenName());
            }

            GuiBase.openGui(null);
        }
    }
}
package net.villagercensus.gui.widget;

import java.util.List;

import fi.dy.masa.malilib.gui.widgets.WidgetListEntryBase;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.render.RenderUtils;
import net.villagercensus.trade.TradeCategory;

public class WidgetTradeCategoryEntry extends WidgetListEntryBase<TradeCategory>
{
    private final boolean isOdd;
    private final String display;

    public WidgetTradeCategoryEntry(int x, int y, int width, int height, boolean isOdd,
                                    TradeCategory entry, int listIndex)
    {
        super(x, y, width, height, entry, listIndex);

        this.isOdd = isOdd;
        this.display = buildDisplay(entry);
    }

    @Override
    public void render(GuiContext ctx, int mouseX, int mouseY, boolean selected)
    {
        if (selected || this.isMouseOver(mouseX, mouseY))
        {
            RenderUtils.drawRect(ctx, this.x, this.y, this.width, this.height, 0xA0707070);
        }
        else if (this.isOdd)
        {
            RenderUtils.drawRect(ctx, this.x, this.y, this.width, this.height, 0xA0101010);
        }
        else
        {
            RenderUtils.drawRect(ctx, this.x, this.y, this.width, this.height, 0xA0303030);
        }

        if (selected)
        {
            RenderUtils.drawOutline(ctx, this.x, this.y, this.width, this.height, 0xFF90D0F0);
        }

        int yOffset = (this.height - this.fontHeight) / 2 + 1;
        String check = selected ? "[x] " : "[ ] ";
        this.drawStringWithShadow(ctx, this.x + 3, this.y + yOffset,
                selected ? 0xFF90D0F0 : 0xFFFFFFFF, check + this.display);

        super.render(ctx, mouseX, mouseY, selected);
    }

    public static String buildDisplay(TradeCategory category)
    {
        StringBuilder sb = new StringBuilder();
        sb.append(shortId(category.profession)).append(": ").append(category.id);
        String summary = tradeSummary(category);

        if (!summary.isEmpty())
        {
            sb.append("  (").append(summary).append(')');
        }

        return sb.toString();
    }

    public static String tradeSummary(TradeCategory category)
    {
        StringBuilder sb = new StringBuilder();
        sb.append(joinItems(category.cost1));

        if (category.cost2 != null && !category.cost2.isEmpty())
        {
            sb.append(" + ").append(joinItems(category.cost2));
        }

        sb.append(" -> ").append(joinItems(category.result));
        return sb.toString();
    }

    private static String joinItems(List<String> items)
    {
        if (items == null || items.isEmpty())
        {
            return "*";
        }

        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < items.size(); i++)
        {
            if (i > 0)
            {
                sb.append('/');
            }

            sb.append(shortId(items.get(i)));
        }

        return sb.toString();
    }

    private static String shortId(String id)
    {
        if (id == null || id.isEmpty())
        {
            return "?";
        }

        int colon = id.indexOf(':');
        return colon >= 0 ? id.substring(colon + 1) : id;
    }
}

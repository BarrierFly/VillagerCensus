package net.villagercensus.gui.widget;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import fi.dy.masa.malilib.gui.widgets.WidgetListEntryBase;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.util.StringUtils;
import net.villagercensus.trade.TradeCategory;
import net.villagercensus.util.Names;

public class WidgetTradeCategoryEntry extends WidgetListEntryBase<TradeCategory>
{
    private static final String EMERALD = "minecraft:emerald";
    private static final List<String> COLORS = List.of(
            "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
            "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black");
    private static final Set<String> COLOR_KINDS = Set.of(
            "banner", "wool", "carpet", "bed", "dye", "terracotta", "glazed_terracotta", "candle");

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
        return Names.professionName(category.profession) + "  " + tradeSummary(category);
    }

    public static String tradeSummary(TradeCategory category)
    {
        return costSummary(category) + " -> " + summarize(category.result);
    }

    private static String costSummary(TradeCategory category)
    {
        LinkedHashSet<String> items = new LinkedHashSet<>();
        boolean emerald = false;

        for (String cost : category.cost1)
        {
            if (EMERALD.equals(cost))
            {
                emerald = true;
            }
            else
            {
                items.add(cost);
            }
        }

        for (String cost : category.cost2)
        {
            if (EMERALD.equals(cost))
            {
                emerald = true;
            }
            else
            {
                items.add(cost);
            }
        }

        List<String> ordered = new ArrayList<>();

        if (emerald)
        {
            ordered.add(EMERALD);
        }

        ordered.addAll(items);
        return summarize(ordered);
    }

    private static String summarize(List<String> itemIds)
    {
        if (itemIds == null || itemIds.isEmpty())
        {
            return StringUtils.translate("villagercensus.trade_kind.any");
        }

        List<String> unique = new ArrayList<>(new LinkedHashSet<>(itemIds));

        if (unique.size() == 1)
        {
            return Names.itemName(unique.get(0));
        }

        boolean allMaps = true;

        for (String id : unique)
        {
            if (!isMap(id))
            {
                allMaps = false;
                break;
            }
        }

        if (allMaps)
        {
            return StringUtils.translate("villagercensus.trade_kind.map")
                    + StringUtils.translate("villagercensus.trade_kind.variants");
        }

        boolean allBoats = true;

        for (String id : unique)
        {
            if (!isBoat(id))
            {
                allBoats = false;
                break;
            }
        }

        if (allBoats)
        {
            return StringUtils.translate("villagercensus.trade_kind.boat")
                    + StringUtils.translate("villagercensus.trade_kind.variants");
        }

        String base = stripColor(path(unique.get(0)));
        boolean sameKind = true;

        for (String id : unique)
        {
            if (!stripColor(path(id)).equals(base))
            {
                sameKind = false;
                break;
            }
        }

        if (sameKind && COLOR_KINDS.contains(base))
        {
            return StringUtils.translate("villagercensus.trade_kind." + base)
                    + StringUtils.translate("villagercensus.trade_kind.variants");
        }

        if (unique.size() <= 3)
        {
            return join(unique);
        }

        return Names.itemName(unique.get(0))
                + StringUtils.translate("villagercensus.trade_kind.more", unique.size() - 1);
    }

    private static String join(List<String> ids)
    {
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < ids.size(); i++)
        {
            if (i > 0)
            {
                sb.append(" + ");
            }

            sb.append(Names.itemName(ids.get(i)));
        }

        return sb.toString();
    }

    private static String path(String itemId)
    {
        int colon = itemId.indexOf(':');
        return colon >= 0 ? itemId.substring(colon + 1) : itemId;
    }

    private static boolean isMap(String itemId)
    {
        String p = path(itemId);
        return p.equals("map") || p.equals("filled_map") || p.endsWith("_map");
    }

    private static boolean isBoat(String itemId)
    {
        String p = path(itemId);
        return p.endsWith("_boat") || p.endsWith("_raft");
    }

    private static String stripColor(String p)
    {
        for (String color : COLORS)
        {
            if (p.startsWith(color + "_"))
            {
                return p.substring(color.length() + 1);
            }

            if (p.endsWith("_" + color))
            {
                return p.substring(0, p.length() - color.length() - 1);
            }
        }

        return p;
    }
}

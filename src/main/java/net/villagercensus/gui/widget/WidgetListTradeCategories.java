package net.villagercensus.gui.widget;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import fi.dy.masa.malilib.gui.LeftRight;
import fi.dy.masa.malilib.gui.MaLiLibIcons;
import fi.dy.masa.malilib.gui.widgets.WidgetListBase;
import fi.dy.masa.malilib.gui.widgets.WidgetSearchBar;
import net.villagercensus.trade.TradeCatalog;
import net.villagercensus.trade.TradeCategory;

public class WidgetListTradeCategories extends WidgetListBase<TradeCategory, WidgetTradeCategoryEntry>
{
    public WidgetListTradeCategories(int x, int y, int width, int height)
    {
        super(x, y, width, height, null);

        this.allowMultiSelection = true;
        this.allowKeyboardNavigation = true;
        this.browserEntryHeight = 12;
        this.browserEntriesOffsetY = 17;
        this.widgetSearchBar = new WidgetSearchBar(x + 2, y + 4, width - 14, 14, 0, MaLiLibIcons.SEARCH, LeftRight.LEFT);
    }

    @Override
    protected Collection<TradeCategory> getAllEntries()
    {
        return TradeCatalog.get().allCategories().values();
    }

    @Override
    protected WidgetTradeCategoryEntry createListEntryWidget(int x, int y, int listIndex, boolean isOdd, TradeCategory entry)
    {
        return new WidgetTradeCategoryEntry(x, y, this.browserEntryWidth, this.getBrowserEntryHeightFor(entry),
                isOdd, entry, listIndex);
    }

    @Override
    protected List<String> getEntryStringsForFilter(TradeCategory entry)
    {
        List<String> list = new ArrayList<>();
        list.add(entry.profession.toLowerCase());
        list.add(entry.id.toLowerCase());

        if (entry.label != null)
        {
            list.add(entry.label.toLowerCase());
        }

        for (String alias : entry.aliases)
        {
            list.add(alias.toLowerCase());
        }

        list.add(WidgetTradeCategoryEntry.tradeSummary(entry).toLowerCase());
        return list;
    }

    public void setInitiallySelected(Collection<String> keys)
    {
        this.selectedEntries.clear();

        for (TradeCategory category : this.getAllEntries())
        {
            if (category.selectedBy(keys))
            {
                this.selectedEntries.add(category);
            }
        }
    }

    public List<String> getSelectedKeys()
    {
        List<String> keys = new ArrayList<>();

        for (TradeCategory category : this.selectedEntries)
        {
            keys.add(key(category));
        }

        keys.sort(String::compareTo);
        return keys;
    }

    public void selectAllVisible()
    {
        this.selectedEntries.addAll(this.listContents);
    }

    public void clearAll()
    {
        this.selectedEntries.clear();
    }

    private static String key(TradeCategory category)
    {
        return category.profession + "/" + category.id;
    }
}

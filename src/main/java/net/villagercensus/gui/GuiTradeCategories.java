package net.villagercensus.gui;

import java.util.List;

import net.minecraft.client.gui.screens.Screen;

import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.GuiListBase;
import fi.dy.masa.malilib.gui.button.ButtonBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.button.IButtonActionListener;
import fi.dy.masa.malilib.util.StringUtils;
import net.villagercensus.config.Configs;
import net.villagercensus.gui.widget.WidgetListTradeCategories;
import net.villagercensus.gui.widget.WidgetTradeCategoryEntry;
import net.villagercensus.trade.TradeCategory;

public class GuiTradeCategories extends GuiListBase<TradeCategory, WidgetTradeCategoryEntry, WidgetListTradeCategories>
{
    public GuiTradeCategories(Screen parent)
    {
        super(10, 30);

        this.title = StringUtils.translate("villagercensus.gui.title.trade_categories");
        this.setParent(parent);
    }

    @Override
    protected int getBrowserWidth()
    {
        return this.width - 20;
    }

    @Override
    protected int getBrowserHeight()
    {
        return this.height - 60;
    }

    @Override
    protected WidgetListTradeCategories createListWidget(int listX, int listY)
    {
        return new WidgetListTradeCategories(listX, listY, this.getBrowserWidth(), this.getBrowserHeight());
    }

    @Override
    public void initGui()
    {
        super.initGui();

        WidgetListTradeCategories list = this.getListWidget();

        if (list != null)
        {
            list.setInitiallySelected(Configs.Generic.SELECTED_CATEGORIES.getStrings());
        }

        int x = 12;
        int y = this.height - 28;

        x += this.createButton(x, y, ButtonType.SELECT_ALL) + 2;
        x += this.createButton(x, y, ButtonType.CLEAR) + 2;
        x += this.createButton(x, y, ButtonType.OK) + 2;
        this.createButton(x, y, ButtonType.CANCEL);
    }

    private int createButton(int x, int y, ButtonType type)
    {
        String label = type.getDisplayName();
        int width = this.getStringWidth(label) + 10;

        this.addButton(new ButtonGeneric(x, y, width, 20, label), new ButtonListener(type, this));
        return width;
    }

    private record ButtonListener(ButtonType type, GuiTradeCategories parent) implements IButtonActionListener
    {
        @Override
        public void actionPerformedWithButton(ButtonBase button, int mouseButton)
        {
            WidgetListTradeCategories list = this.parent.getListWidget();

            switch (this.type)
            {
                case SELECT_ALL -> {
                    if (list != null)
                    {
                        list.selectAllVisible();
                    }
                }
                case CLEAR -> {
                    if (list != null)
                    {
                        list.clearAll();
                    }
                }
                case OK -> {
                    if (list != null)
                    {
                        List<String> selected = list.getSelectedKeys();
                        Configs.Generic.SELECTED_CATEGORIES.setStrings(selected);
                        Configs.saveToFile();
                    }

                    GuiBase.openGui(this.parent.getParent());
                }
                case CANCEL -> GuiBase.openGui(this.parent.getParent());
            }
        }
    }

    private enum ButtonType
    {
        SELECT_ALL("villagercensus.gui.button.select_all"),
        CLEAR("villagercensus.gui.button.clear"),
        OK("malilib.gui.button.ok"),
        CANCEL("malilib.gui.button.cancel");

        private final String translationKey;

        ButtonType(String translationKey)
        {
            this.translationKey = translationKey;
        }

        public String getDisplayName()
        {
            return StringUtils.translate(this.translationKey);
        }
    }
}

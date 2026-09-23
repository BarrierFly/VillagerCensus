package net.villagercensus.census;

import java.util.ArrayList;
import java.util.List;

public class TradeEntry
{
    public String categoryId = "";
    public String cost1Id = "";
    public int cost1Count;
    public String cost2Id = "";
    public int cost2Count;
    public String sellId = "";
    public int sellCount;
    public boolean enchantedBook;
    public List<EnchantEntry> enchantments = new ArrayList<>();

    public TradeEntry()
    {
    }

    public TradeEntry(String categoryId, String cost1Id, int cost1Count, String cost2Id, int cost2Count,
                      String sellId, int sellCount, boolean enchantedBook, List<EnchantEntry> enchantments)
    {
        this.categoryId = categoryId;
        this.cost1Id = cost1Id;
        this.cost1Count = cost1Count;
        this.cost2Id = cost2Id;
        this.cost2Count = cost2Count;
        this.sellId = sellId;
        this.sellCount = sellCount;
        this.enchantedBook = enchantedBook;
        this.enchantments = enchantments;
    }

    public String key()
    {
        return this.cost1Id + "|" + this.cost1Count + "|" + this.cost2Id + "|" + this.cost2Count + "|"
                + this.sellId + "|" + this.sellCount + "|" + this.enchantmentKey();
    }

    public String enchantmentKey()
    {
        StringBuilder sb = new StringBuilder();

        for (EnchantEntry entry : this.enchantments)
        {
            sb.append(entry.id).append('@').append(entry.level).append(';');
        }

        return sb.toString();
    }
}

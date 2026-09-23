package net.villagercensus.census;

public class EnchantEntry
{
    public String id = "";
    public int level;
    public int maxLevel = -1;

    public EnchantEntry()
    {
    }

    public EnchantEntry(String id, int level, int maxLevel)
    {
        this.id = id;
        this.level = level;
        this.maxLevel = maxLevel;
    }
}

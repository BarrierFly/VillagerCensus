package net.villagercensus.census;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import fi.dy.masa.malilib.util.StringUtils;

public class VillagerRecord
{
    public UUID uuid;
    public String professionId = "minecraft:none";
    public boolean baby;
    public int level;
    public boolean hasTraded;
    public float health;
    public float maxHealth;
    public int blockX;
    public int blockY;
    public int blockZ;
    public boolean hasCoordinates = true;
    public String dimension = "";
    public String customName;
    public boolean hasTradeData;
    public boolean weakAssociation;
    public DataStatus status = DataStatus.FULL;
    public String remark = "";
    public long order;
    public List<TradeEntry> trades = new ArrayList<>();

    public VillagerRecord()
    {
    }

    public VillagerRecord copy()
    {
        VillagerRecord r = new VillagerRecord();
        r.uuid = this.uuid;
        r.professionId = this.professionId;
        r.baby = this.baby;
        r.level = this.level;
        r.hasTraded = this.hasTraded;
        r.health = this.health;
        r.maxHealth = this.maxHealth;
        r.blockX = this.blockX;
        r.blockY = this.blockY;
        r.blockZ = this.blockZ;
        r.hasCoordinates = this.hasCoordinates;
        r.dimension = this.dimension;
        r.customName = this.customName;
        r.hasTradeData = this.hasTradeData;
        r.weakAssociation = this.weakAssociation;
        r.status = this.status;
        r.remark = this.remark;
        r.order = this.order;
        r.trades = new ArrayList<>(this.trades);
        return r;
    }

    public boolean hasAnyEnchantedBook()
    {
        for (TradeEntry entry : this.trades)
        {
            if (entry.enchantedBook)
            {
                return true;
            }
        }

        return false;
    }

    /**
     * Full record-field comparison, per the Q34 answer: profession, level, traded state,
     * trade list, custom name, baby flag, plus health and coordinates.
     */
    public boolean sameAs(VillagerRecord other)
    {
        if (other == null)
        {
            return false;
        }

        return Objects.equals(this.professionId, other.professionId)
                && this.baby == other.baby
                && this.level == other.level
                && this.hasTraded == other.hasTraded
                && Float.compare(this.health, other.health) == 0
                && Float.compare(this.maxHealth, other.maxHealth) == 0
                && (!this.hasCoordinates || !other.hasCoordinates || this.samePosition(other))
                && Objects.equals(this.dimension, other.dimension)
                && Objects.equals(this.customName, other.customName)
                && this.hasTradeData == other.hasTradeData
                && this.sameTrades(other);
    }

    private boolean samePosition(VillagerRecord other)
    {
        return this.blockX == other.blockX && this.blockY == other.blockY && this.blockZ == other.blockZ;
    }

    private boolean sameTrades(VillagerRecord other)
    {
        if (this.trades.size() != other.trades.size())
        {
            return false;
        }

        List<String> a = new ArrayList<>();
        List<String> b = new ArrayList<>();

        for (TradeEntry t : this.trades)
        {
            a.add(t.key());
        }

        for (TradeEntry t : other.trades)
        {
            b.add(t.key());
        }

        java.util.Collections.sort(a);
        java.util.Collections.sort(b);
        return a.equals(b);
    }

    public List<String> diffLines(VillagerRecord other)
    {
        List<String> lines = new ArrayList<>();

        if (other == null)
        {
            lines.add(StringUtils.translate("villagercensus.diff.no_previous"));
            return lines;
        }

        if (!Objects.equals(this.professionId, other.professionId))
        {
            lines.add(StringUtils.translate("villagercensus.diff.profession", other.professionId, this.professionId));
        }
        if (this.level != other.level)
        {
            lines.add(StringUtils.translate("villagercensus.diff.level", other.level, this.level));
        }
        if (this.hasTraded != other.hasTraded)
        {
            lines.add(StringUtils.translate("villagercensus.diff.traded", other.hasTraded, this.hasTraded));
        }
        if (this.baby != other.baby)
        {
            lines.add(StringUtils.translate("villagercensus.diff.baby", other.baby, this.baby));
        }
        if (Float.compare(this.health, other.health) != 0)
        {
            lines.add(StringUtils.translate("villagercensus.diff.health", other.health, this.health));
        }
        if (this.hasCoordinates && other.hasCoordinates && !this.samePosition(other))
        {
            lines.add(StringUtils.translate("villagercensus.diff.pos",
                    other.blockX, other.blockY, other.blockZ, this.blockX, this.blockY, this.blockZ));
        }
        if (!Objects.equals(this.customName, other.customName))
        {
            lines.add(StringUtils.translate("villagercensus.diff.name", String.valueOf(other.customName),
                    String.valueOf(this.customName)));
        }
        if (this.hasTradeData != other.hasTradeData)
        {
            lines.add(StringUtils.translate("villagercensus.diff.trade_data", other.hasTradeData, this.hasTradeData));
        }
        if (!this.sameTrades(other))
        {
            lines.add(StringUtils.translate("villagercensus.diff.trades", other.trades.size(), this.trades.size()));
        }

        return lines;
    }
}

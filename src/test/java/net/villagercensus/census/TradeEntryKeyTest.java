package net.villagercensus.census;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class TradeEntryKeyTest
{
    @Test
    void theKeyCoversCountsAndIds()
    {
        TradeEntry a = trade("minecraft:emerald", 1, "minecraft:book", 1);
        TradeEntry b = trade("minecraft:emerald", 1, "minecraft:book", 1);
        TradeEntry c = trade("minecraft:emerald", 2, "minecraft:book", 1);

        assertEquals(a.key(), b.key());
        assertFalse(a.key().equals(c.key()));
    }

    @Test
    void theKeyIgnoresTheCategoryLabel()
    {
        TradeEntry a = trade("minecraft:emerald", 1, "minecraft:book", 1);
        TradeEntry b = trade("minecraft:emerald", 1, "minecraft:book", 1);
        b.categoryId = "some_other_category";

        assertEquals(a.key(), b.key());
    }

    @Test
    void theSecondCostParticipates()
    {
        TradeEntry a = trade("minecraft:emerald", 1, "minecraft:book", 1);
        TradeEntry b = trade("minecraft:emerald", 1, "minecraft:book", 1);
        b.cost2Id = "minecraft:paper";
        b.cost2Count = 3;

        assertFalse(a.key().equals(b.key()));
    }

    @Test
    void enchantmentLevelsParticipate()
    {
        TradeEntry a = trade("minecraft:emerald", 1, "minecraft:book", 1);
        a.enchantments.add(new EnchantEntry("minecraft:mending", 1, 1));

        TradeEntry b = trade("minecraft:emerald", 1, "minecraft:book", 1);
        b.enchantments.add(new EnchantEntry("minecraft:mending", 2, 1));

        assertFalse(a.key().equals(b.key()));
        assertEquals("minecraft:mending@1;", a.enchantmentKey());
        assertEquals("", trade("minecraft:emerald", 1, "minecraft:book", 1).enchantmentKey());
    }

    @Test
    void dataStatusRoundTripsThroughItsId()
    {
        for (DataStatus status : List.of(DataStatus.values()))
        {
            assertEquals(status, DataStatus.fromId(status.getId()));
        }

        assertEquals(DataStatus.FULL, DataStatus.fromId("not-a-status"));
    }

    private static TradeEntry trade(String cost1, int cost1Count, String sell, int sellCount)
    {
        return new TradeEntry("cat", cost1, cost1Count, "", 0, sell, sellCount, false, new java.util.ArrayList<>());
    }

    @Test
    void reportTimeFormattingIsStableAndReversible()
    {
        long stamp = 1_700_000_000_000L;
        String formatted = net.villagercensus.data.ReportWriter.formatTime(stamp);

        assertTrue(formatted.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}"), formatted);
        assertEquals(formatted, net.villagercensus.data.ReportWriter.formatTime(stamp));
    }
}
package net.villagercensus.census;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class VillagerRecordComparisonTest
{
    private static VillagerRecord base()
    {
        VillagerRecord record = new VillagerRecord();
        record.uuid = UUID.randomUUID();
        record.professionId = "minecraft:librarian";
        record.level = 3;
        record.hasTraded = true;
        record.health = 20.0F;
        record.maxHealth = 20.0F;
        record.blockX = 10;
        record.blockY = 64;
        record.blockZ = -3;
        record.hasCoordinates = true;
        record.dimension = "minecraft:overworld";
        record.hasTradeData = true;
        record.trades.add(new TradeEntry("sell_book", "minecraft:emerald", 1, "", 0, "minecraft:book", 1,
                false, new java.util.ArrayList<>()));
        return record;
    }

    @Test
    void identicalRecordsCompareEqual()
    {
        assertTrue(base().sameAs(base()));
    }

    @Test
    void tradeOrderDoesNotCountAsAChange()
    {
        VillagerRecord a = base();
        a.trades.add(ink());
        VillagerRecord b = base();
        b.trades.add(0, ink());

        assertEquals(2, a.trades.size());
        assertEquals(2, b.trades.size());
        assertTrue(a.sameAs(b), "reordering the same trades must not be reported as a change");
    }

    private static TradeEntry ink()
    {
        return new TradeEntry("buy_ink", "minecraft:emerald", 2, "", 0, "minecraft:ink_cyan", 1,
                false, new java.util.ArrayList<>());
    }

    @Test
    void aDifferentTradeCountsAsAChange()
    {
        VillagerRecord a = base();
        VillagerRecord b = base();
        b.trades.add(new TradeEntry("sell_ink", "minecraft:emerald", 1, "", 0, "minecraft:ink_cyan", 1,
                false, new java.util.ArrayList<>()));

        assertFalse(a.sameAs(b));
    }

    @Test
    void coordinatesAreIgnoredWhenTheyWereNotRecorded()
    {
        VillagerRecord a = base();
        VillagerRecord b = base();
        b.hasCoordinates = false;
        b.blockX = 999;

        assertTrue(a.sameAs(b), "with recordCoordinates off, position differences are skipped");
    }

    @Test
    void coordinatesAreComparedWhenRecorded()
    {
        VillagerRecord a = base();
        VillagerRecord b = base();
        b.blockZ = -4;

        assertFalse(a.sameAs(b));
    }

    @Test
    void everyFieldOfTheComparisonParticipates()
    {
        VillagerRecord reference = base();

        VillagerRecord profession = base();
        profession.professionId = "minecraft:smith";
        assertFalse(reference.sameAs(profession), "profession");

        VillagerRecord level = base();
        level.level = 4;
        assertFalse(reference.sameAs(level), "level");

        VillagerRecord traded = base();
        traded.hasTraded = false;
        assertFalse(reference.sameAs(traded), "traded state");

        VillagerRecord health = base();
        health.health = 12.0F;
        assertFalse(reference.sameAs(health), "health");

        VillagerRecord baby = base();
        baby.baby = true;
        assertFalse(reference.sameAs(baby), "baby flag");

        VillagerRecord name = base();
        name.customName = "Bob";
        assertFalse(reference.sameAs(name), "custom name");

        VillagerRecord noTrades = base();
        noTrades.hasTradeData = false;
        assertFalse(reference.sameAs(noTrades), "trade data availability");
    }

    @Test
    void enchantmentLevelsArePartOfTheTradeKey()
    {
        VillagerRecord a = base();
        a.trades.clear();
        a.trades.add(enchanted("minecraft:silk_touch", 1));
        VillagerRecord b = base();
        b.trades.clear();
        b.trades.add(enchanted("minecraft:silk_touch", 1));
        VillagerRecord c = base();
        c.trades.clear();
        c.trades.add(enchanted("minecraft:silk_touch", 2));

        assertTrue(a.sameAs(b));
        assertFalse(a.sameAs(c), "a different enchantment level is a trade change");
    }

    @Test
    void hasAnyEnchantedBookLooksAtTheFlag()
    {
        VillagerRecord record = base();
        assertFalse(record.hasAnyEnchantedBook());

        TradeEntry book = new TradeEntry("sell_enchanted_book", "minecraft:emerald", 1, "", 0,
                "minecraft:enchanted_book", 1, true, List.of());
        record.trades.add(book);
        assertTrue(record.hasAnyEnchantedBook());
    }

    private static TradeEntry enchanted(String id, int level)
    {
        TradeEntry trade = new TradeEntry("sell_enchanted_book", "minecraft:emerald", 1, "", 0,
                "minecraft:book", 1, true, new java.util.ArrayList<>());
        trade.enchantments.add(new EnchantEntry(id, level, 1));
        return trade;
    }
}
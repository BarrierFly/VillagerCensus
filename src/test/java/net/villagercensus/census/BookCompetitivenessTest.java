package net.villagercensus.census;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class BookCompetitivenessTest
{
    private static TradeEntry bookTrade(String enchantId, int level, int price)
    {
        TradeEntry trade = new TradeEntry();
        trade.cost1Id = "minecraft:emerald";
        trade.cost1Count = price;
        trade.cost2Id = "minecraft:book";
        trade.cost2Count = 1;
        trade.sellId = "minecraft:enchanted_book";
        trade.sellCount = 1;
        trade.enchantedBook = true;
        trade.enchantments.add(new EnchantEntry(enchantId, level, level));
        return trade;
    }

    private static VillagerRecord librarian(UUID uuid, String dimension, TradeEntry... trades)
    {
        VillagerRecord record = new VillagerRecord();
        record.uuid = uuid;
        record.professionId = "minecraft:librarian";
        record.hasTradeData = true;
        record.dimension = dimension;

        for (TradeEntry trade : trades)
        {
            record.trades.add(trade);
        }

        return record;
    }

    private static EnchantEntry ench(String id, int level)
    {
        return new EnchantEntry(id, level, level);
    }

    private static TradeEntry multiBookTrade(int price, EnchantEntry... enchantments)
    {
        TradeEntry trade = new TradeEntry();
        trade.cost1Id = "minecraft:emerald";
        trade.cost1Count = price;
        trade.cost2Id = "minecraft:book";
        trade.cost2Count = 1;
        trade.sellId = "minecraft:enchanted_book";
        trade.sellCount = 1;
        trade.enchantedBook = true;

        for (EnchantEntry entry : enchantments)
        {
            trade.enchantments.add(entry);
        }

        return trade;
    }

    @Test
    void emptySessionYieldsNoEntries()
    {
        assertTrue(BookCompetitiveness.compute(new ArrayList<>()).isEmpty());
    }

    @Test
    void librarianWithoutBookTradesIsNotCompetitive()
    {
        VillagerRecord record = librarian(UUID.randomUUID(), "minecraft:overworld");
        record.trades.add(new TradeEntry());

        Map<UUID, Boolean> result = BookCompetitiveness.compute(List.of(record));

        assertEquals(1, result.size());
        assertFalse(result.get(record.uuid));
    }

    @Test
    void singleLibrarianWithBookTradeIsCompetitive()
    {
        UUID uuid = UUID.randomUUID();
        VillagerRecord record = librarian(uuid, "minecraft:overworld", bookTrade("minecraft:protection", 3, 20));

        Map<UUID, Boolean> result = BookCompetitiveness.compute(List.of(record));

        assertTrue(result.get(uuid));
    }

    @Test
    void strictlyCheaperSameBookEliminatesTheOthers()
    {
        UUID cheap = UUID.randomUUID();
        UUID expensive = UUID.randomUUID();
        VillagerRecord first = librarian(cheap, "minecraft:overworld", bookTrade("minecraft:protection", 3, 10));
        VillagerRecord second = librarian(expensive, "minecraft:overworld", bookTrade("minecraft:protection", 3, 15));

        Map<UUID, Boolean> result = BookCompetitiveness.compute(List.of(first, second));

        assertTrue(result.get(cheap));
        assertFalse(result.get(expensive));
    }

    @Test
    void equalPricesDoNotEliminateEachOther()
    {
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        VillagerRecord first = librarian(firstId, "minecraft:overworld", bookTrade("minecraft:protection", 3, 12));
        VillagerRecord second = librarian(secondId, "minecraft:overworld", bookTrade("minecraft:protection", 3, 12));

        Map<UUID, Boolean> result = BookCompetitiveness.compute(List.of(first, second));

        assertTrue(result.get(firstId));
        assertTrue(result.get(secondId));
    }

    @Test
    void crossDimensionRecordsStillCompete()
    {
        UUID nether = UUID.randomUUID();
        UUID overworld = UUID.randomUUID();
        VillagerRecord first = librarian(nether, "minecraft:the_nether", bookTrade("minecraft:mending", 1, 8));
        VillagerRecord second = librarian(overworld, "minecraft:overworld", bookTrade("minecraft:mending", 1, 30));

        Map<UUID, Boolean> result = BookCompetitiveness.compute(List.of(first, second));

        assertTrue(result.get(nether));
        assertFalse(result.get(overworld));
    }

    @Test
    void differentCostItemsAreComparedSeparately()
    {
        UUID emerald = UUID.randomUUID();
        UUID block = UUID.randomUUID();
        VillagerRecord first = librarian(emerald, "minecraft:overworld", bookTrade("minecraft:protection", 3, 10));
        VillagerRecord second = librarian(block, "minecraft:overworld", bookTrade("minecraft:protection", 3, 40));
        second.trades.get(0).cost1Id = "minecraft:emerald_block";

        Map<UUID, Boolean> result = BookCompetitiveness.compute(List.of(first, second));

        assertTrue(result.get(emerald));
        assertTrue(result.get(block));
    }

    @Test
    void differentEnchantmentLevelsAreComparedSeparately()
    {
        UUID high = UUID.randomUUID();
        UUID low = UUID.randomUUID();
        VillagerRecord first = librarian(high, "minecraft:overworld", bookTrade("minecraft:protection", 4, 10));
        VillagerRecord second = librarian(low, "minecraft:overworld", bookTrade("minecraft:protection", 3, 40));

        Map<UUID, Boolean> result = BookCompetitiveness.compute(List.of(first, second));

        assertTrue(result.get(high));
        assertTrue(result.get(low));
    }

    @Test
    void multiEnchantmentComboOnlyCompetesWithinItself()
    {
        UUID doubleCombo = UUID.randomUUID();
        UUID singleCombo = UUID.randomUUID();
        VillagerRecord first = librarian(doubleCombo, "minecraft:overworld",
                multiBookTrade(10, ench("minecraft:protection", 3), ench("minecraft:mending", 1)));
        VillagerRecord second = librarian(singleCombo, "minecraft:overworld", bookTrade("minecraft:protection", 3, 40));

        Map<UUID, Boolean> result = BookCompetitiveness.compute(List.of(first, second));

        // One book with two enchantments is a different combination than each single-enchantment
        // book, so the two trades never compete.
        assertTrue(result.get(doubleCombo));
        assertTrue(result.get(singleCombo));
    }

    @Test
    void identicalMultiEnchantmentCombosCompete()
    {
        UUID cheaper = UUID.randomUUID();
        UUID pricier = UUID.randomUUID();
        VillagerRecord first = librarian(cheaper, "minecraft:overworld",
                multiBookTrade(10, ench("minecraft:protection", 3), ench("minecraft:mending", 1)));
        VillagerRecord second = librarian(pricier, "minecraft:overworld",
                multiBookTrade(25, ench("minecraft:protection", 3), ench("minecraft:mending", 1)));

        Map<UUID, Boolean> result = BookCompetitiveness.compute(List.of(first, second));

        assertTrue(result.get(cheaper));
        assertFalse(result.get(pricier));
    }

    @Test
    void enchantmentCombosAreNormalizedBeforeComparison()
    {
        UUID cheaper = UUID.randomUUID();
        UUID pricier = UUID.randomUUID();
        VillagerRecord first = librarian(cheaper, "minecraft:overworld",
                multiBookTrade(10, ench("minecraft:protection", 3), ench("minecraft:mending", 1)));
        VillagerRecord second = librarian(pricier, "minecraft:overworld",
                multiBookTrade(25, ench("minecraft:mending", 1), ench("minecraft:protection", 3)));

        Map<UUID, Boolean> result = BookCompetitiveness.compute(List.of(first, second));

        assertFalse(result.get(pricier));
    }

    @Test
    void babiesAndNonLibrariansAreExcludedFromTheResult()
    {
        UUID babyId = UUID.randomUUID();
        UUID farmerId = UUID.randomUUID();
        VillagerRecord baby = librarian(babyId, "minecraft:overworld", bookTrade("minecraft:protection", 3, 10));
        baby.baby = true;
        VillagerRecord farmer = librarian(farmerId, "minecraft:overworld", bookTrade("minecraft:protection", 3, 10));
        farmer.professionId = "minecraft:farmer";

        Map<UUID, Boolean> result = BookCompetitiveness.compute(List.of(baby, farmer));

        assertTrue(result.isEmpty());
    }

    @Test
    void nonLibrarianTradesDoNotEliminateLibrarianOnes()
    {
        UUID librarianId = UUID.randomUUID();
        VillagerRecord librarian = librarian(librarianId, "minecraft:overworld", bookTrade("minecraft:protection", 3, 15));
        VillagerRecord farmer = librarian(UUID.randomUUID(), "minecraft:overworld", bookTrade("minecraft:protection", 3, 10));
        farmer.professionId = "minecraft:farmer";

        Map<UUID, Boolean> result = BookCompetitiveness.compute(List.of(librarian, farmer));

        assertTrue(result.get(librarianId));
    }

    @Test
    void librariansWithoutTradeDataAreNotMarked()
    {
        UUID noData = UUID.randomUUID();
        VillagerRecord record = librarian(noData, "minecraft:overworld");
        record.hasTradeData = false;

        Map<UUID, Boolean> result = BookCompetitiveness.compute(List.of(record));

        assertFalse(result.containsKey(noData));
    }

    @Test
    void recordWithoutUuidStillSuppressesPricierTrades()
    {
        UUID identified = UUID.randomUUID();
        VillagerRecord anonymous = librarian(null, "minecraft:overworld", bookTrade("minecraft:mending", 1, 5));
        VillagerRecord identifiedRecord = librarian(identified, "minecraft:overworld", bookTrade("minecraft:mending", 1, 20));

        Map<UUID, Boolean> result = BookCompetitiveness.compute(List.of(anonymous, identifiedRecord));

        assertEquals(1, result.size());
        assertFalse(result.get(identified));
    }

    @Test
    void cheapestTradeWinsWithinTheSameLibrarian()
    {
        UUID uuid = UUID.randomUUID();
        VillagerRecord record = librarian(uuid, "minecraft:overworld",
                bookTrade("minecraft:sharpness", 4, 25), bookTrade("minecraft:sharpness", 4, 18));

        Map<UUID, Boolean> result = BookCompetitiveness.compute(List.of(record));

        assertTrue(result.get(uuid));
    }
}

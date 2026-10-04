package net.villagercensus.census;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class CensusSessionTest
{
    private static CensusSession newSession()
    {
        return new CensusSession("hall", "hall", "world", "minecraft:overworld", 1000L);
    }

    private static VillagerRecord record(UUID uuid, String profession, int level)
    {
        VillagerRecord record = new VillagerRecord();
        record.uuid = uuid;
        record.professionId = profession;
        record.level = level;
        record.dimension = "minecraft:overworld";
        return record;
    }

    @Test
    void sessionKeyCombinesWorldDimensionAndName()
    {
        assertEquals("world|minecraft:overworld|hall", newSession().sessionKey());
    }

    @Test
    void isActiveTracksTheEndTime()
    {
        CensusSession session = newSession();
        assertTrue(session.isActive());

        session.endTime = 5L;
        assertFalse(session.isActive());
    }

    @Test
    void addRecordIndexesByUuidAndNumbersInOrder()
    {
        CensusSession session = newSession();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        session.addRecord(record(first, "minecraft:librarian", 3));
        session.addRecord(record(second, "minecraft:smith", 2));

        assertEquals(2, session.totalCount());
        assertEquals(1L, session.records.get(0).order);
        assertEquals(2L, session.records.get(1).order);
        assertSame(session.records.get(1), session.getByUuid(second));
        assertSame(session.records.get(1), session.getLastTarget());
    }

    @Test
    void rebuildIndexRestoresTheLookupAfterDeserialization()
    {
        CensusSession session = newSession();
        UUID uuid = UUID.randomUUID();
        session.records.add(record(uuid, "minecraft:cartographer", 4));

        session.byUuid = new java.util.LinkedHashMap<>();
        session.rebuildIndex();

        assertNotNull(session.getByUuid(uuid));
    }

    @Test
    void updateRecordReplacesInPlaceAndKeepsTheOrder()
    {
        CensusSession session = newSession();
        UUID uuid = UUID.randomUUID();
        VillagerRecord original = record(uuid, "minecraft:librarian", 3);
        session.addRecord(original);

        VillagerRecord updated = record(uuid, "minecraft:smith", 4);
        session.updateRecord(original, updated);

        assertEquals(1, session.totalCount());
        assertEquals(1L, session.records.get(0).order);
        assertSame(updated, session.getByUuid(uuid));
    }

    @Test
    void undoRemovesNewRecordsAndRestoresUpdatedOnes()
    {
        CensusSession session = newSession();
        UUID first = UUID.randomUUID();
        VillagerRecord original = record(first, "minecraft:librarian", 3);
        session.addRecord(original);

        UndoEntry added = session.undo();
        assertTrue(added.wasNew);
        assertEquals(0, session.totalCount());
        assertNull(session.getByUuid(first));

        session.addRecord(record(first, "minecraft:librarian", 3));
        VillagerRecord stored = session.getByUuid(first);
        VillagerRecord replacement = record(first, "minecraft:smith", 4);
        session.updateRecord(stored, replacement);

        UndoEntry updated = session.undo();
        assertFalse(updated.wasNew);
        assertEquals(1, session.totalCount());
        assertEquals("minecraft:librarian", session.getByUuid(first).professionId);
    }

    @Test
    void undoOnAnEmptyStackReturnsNull()
    {
        assertNull(newSession().undo());
    }

    @Test
    void professionCountsSkipBabiesAndKeepFirstSeenOrder()
    {
        CensusSession session = newSession();
        session.addRecord(record(UUID.randomUUID(), "minecraft:librarian", 3));
        session.addRecord(record(UUID.randomUUID(), "minecraft:smith", 2));

        VillagerRecord baby = record(UUID.randomUUID(), "minecraft:none", 1);
        baby.baby = true;
        session.addRecord(baby);

        session.addRecord(record(UUID.randomUUID(), "minecraft:librarian", 4));

        assertEquals(List.of("minecraft:librarian", "minecraft:smith"),
                List.copyOf(session.professionCounts().keySet()));
        assertEquals(2, session.professionCounts().get("minecraft:librarian"));
        assertEquals(1, session.babyCount());
        assertEquals(4, session.totalCount());
    }

    @Test
    void copyIsDeepForTheTradeList()
    {
        VillagerRecord record = record(UUID.randomUUID(), "minecraft:smith", 3);
        record.trades.add(new TradeEntry("sell_iron", "minecraft:emerald", 1, "", 0, "minecraft:iron_ingot", 1,
                false, new java.util.ArrayList<>()));

        VillagerRecord copy = record.copy();
        copy.trades.clear();
        copy.remark = "changed";

        assertEquals(1, record.trades.size());
        assertEquals("", record.remark);
        assertEquals(record.uuid, copy.uuid);
    }
}
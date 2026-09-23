package net.villagercensus.census;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class CensusSession
{
    public String rawName;
    public String safeName;
    public String worldId;
    public String dimension;
    public long startTime;
    public long endTime;
    public long orderCounter;
    public UUID lastTargetUuid;
    public boolean paused;

    public List<VillagerRecord> records = new ArrayList<>();

    public transient Map<UUID, VillagerRecord> byUuid = new LinkedHashMap<>();
    public transient Deque<UndoEntry> undoStack = new ArrayDeque<>();

    public CensusSession()
    {
    }

    public CensusSession(String rawName, String safeName, String worldId, String dimension, long startTime)
    {
        this.rawName = rawName;
        this.safeName = safeName;
        this.worldId = worldId;
        this.dimension = dimension;
        this.startTime = startTime;
    }

    public void rebuildIndex()
    {
        this.byUuid = new LinkedHashMap<>();

        for (VillagerRecord record : this.records)
        {
            if (record.uuid != null)
            {
                this.byUuid.put(record.uuid, record);
            }
        }

        if (this.undoStack == null)
        {
            this.undoStack = new ArrayDeque<>();
        }
    }

    public String sessionKey()
    {
        return this.worldId + "|" + this.dimension + "|" + this.safeName;
    }

    public boolean isActive()
    {
        return this.endTime == 0L;
    }

    public VillagerRecord getByUuid(UUID uuid)
    {
        return this.byUuid.get(uuid);
    }

    public void addRecord(VillagerRecord record)
    {
        record.order = ++this.orderCounter;
        this.records.add(record);

        if (record.uuid != null)
        {
            this.byUuid.put(record.uuid, record);
        }

        this.lastTargetUuid = record.uuid;
        this.undoStack.push(new UndoEntry(record.uuid, true, null, record));
    }

    public void updateRecord(VillagerRecord oldRecord, VillagerRecord updated)
    {
        updated.order = oldRecord.order;
        int index = this.records.indexOf(oldRecord);

        if (index >= 0)
        {
            this.records.set(index, updated);
        }

        if (updated.uuid != null)
        {
            this.byUuid.put(updated.uuid, updated);
        }

        this.lastTargetUuid = updated.uuid;
        this.undoStack.push(new UndoEntry(updated.uuid, false, oldRecord.copy(), updated));
    }

    public UndoEntry undo()
    {
        if (this.undoStack.isEmpty())
        {
            return null;
        }

        UndoEntry entry = this.undoStack.pop();

        if (entry.wasNew)
        {
            this.records.remove(entry.after);
            this.byUuid.remove(entry.uuid);
        }
        else
        {
            int index = this.records.indexOf(entry.after);

            if (index >= 0)
            {
                this.records.set(index, entry.before);
            }

            if (entry.uuid != null)
            {
                this.byUuid.put(entry.uuid, entry.before);
            }
        }

        this.lastTargetUuid = entry.uuid;
        return entry;
    }

    public VillagerRecord getLastTarget()
    {
        if (this.lastTargetUuid == null)
        {
            return null;
        }

        return this.byUuid.get(this.lastTargetUuid);
    }

    public int totalCount()
    {
        return this.records.size();
    }

    public int babyCount()
    {
        int count = 0;

        for (VillagerRecord record : this.records)
        {
            if (record.baby)
            {
                count++;
            }
        }

        return count;
    }

    /**
     * Profession counts for adult villagers, ordered by the earliest first-statistic order
     * of the current members (section 4.4). The "baby" total is appended by the report writer.
     */
    public LinkedHashMap<String, Integer> professionCounts()
    {
        Map<String, Integer> counts = new LinkedHashMap<>();
        Map<String, Long> firstOrder = new LinkedHashMap<>();

        for (VillagerRecord record : this.records)
        {
            if (record.baby)
            {
                continue;
            }

            String key = record.professionId;
            counts.merge(key, 1, Integer::sum);

            Long existing = firstOrder.get(key);

            if (existing == null || record.order < existing)
            {
                firstOrder.put(key, record.order);
            }
        }

        List<String> keys = new ArrayList<>(counts.keySet());
        keys.sort((a, b) -> Long.compare(firstOrder.getOrDefault(a, 0L), firstOrder.getOrDefault(b, 0L)));

        LinkedHashMap<String, Integer> ordered = new LinkedHashMap<>();

        for (String key : keys)
        {
            ordered.put(key, counts.get(key));
        }

        return ordered;
    }
}

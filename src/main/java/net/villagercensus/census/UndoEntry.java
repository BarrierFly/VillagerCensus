package net.villagercensus.census;

import java.util.UUID;

public class UndoEntry
{
    public final UUID uuid;
    public final boolean wasNew;
    public final VillagerRecord before;
    public final VillagerRecord after;

    public UndoEntry(UUID uuid, boolean wasNew, VillagerRecord before, VillagerRecord after)
    {
        this.uuid = uuid;
        this.wasNew = wasNew;
        this.before = before;
        this.after = after;
    }
}

package net.villagercensus.census;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Session-scope competitiveness verdict for librarian enchanted-book trades (section 1.2 of the
 * implementation plan). A book trade is competitive when no other participating book trade with
 * the same enchantment combination and the same cost items has a strictly lower price; equal
 * prices do not eliminate each other. A librarian is competitive when at least one of its book
 * trades is. Only adult librarians with captured trade data are marked at all; librarians whose
 * offers never arrived stay unmarked (the capture flow already warns via {@code trade_data_failed}).
 *
 * <p>Pure logic over the record model: no Minecraft classes, unit-testable on a plain JVM.
 */
public final class BookCompetitiveness
{
    public static final String LIBRARIAN = "minecraft:librarian";

    private BookCompetitiveness()
    {
    }

    /**
     * Computes the verdict per marked librarian record. Records that are not adult librarians
     * with trade data are absent from the result; records without a UUID (unresolved TXT forks)
     * still take part in the price comparison but produce no entry since they can never become a
     * glow target.
     */
    public static Map<UUID, Boolean> compute(List<VillagerRecord> records)
    {
        Map<String, Integer> lowest = new HashMap<>();

        for (VillagerRecord record : records)
        {
            if (!isMarked(record))
            {
                continue;
            }

            for (TradeEntry trade : record.trades)
            {
                if (!trade.enchantedBook)
                {
                    continue;
                }

                String group = groupKey(trade);
                Integer lowestPrice = lowest.get(group);

                if (lowestPrice == null || trade.cost1Count < lowestPrice)
                {
                    lowest.put(group, trade.cost1Count);
                }
            }
        }

        Map<UUID, Boolean> result = new HashMap<>();

        for (VillagerRecord record : records)
        {
            if (!isMarked(record) || record.uuid == null)
            {
                continue;
            }

            boolean competitive = false;

            for (TradeEntry trade : record.trades)
            {
                if (trade.enchantedBook && lowest.getOrDefault(groupKey(trade), -1) == trade.cost1Count)
                {
                    competitive = true;
                    break;
                }
            }

            result.put(record.uuid, competitive);
        }

        return result;
    }

    /**
     * Merges the session records with trade data observed through channels other than this mod's
     * own census flow (e.g. offers-hud probes). At most one record per UUID survives: the observed
     * one, which is the more recent fact, otherwise the recorded one. Observed UUIDs that are not
     * recorded at all are appended at the end. Records without a UUID (unresolved TXT forks) keep
     * their place and still take part in the price comparison.
     *
     * <p>Pure logic over the record model, so the merge is unit-testable on a plain JVM.
     */
    public static List<VillagerRecord> mergeObserved(List<VillagerRecord> records,
                                                     Map<UUID, VillagerRecord> observed)
    {
        if (observed == null || observed.isEmpty())
        {
            return records;
        }

        List<VillagerRecord> merged = new ArrayList<>(records.size() + observed.size());
        Set<UUID> seen = new HashSet<>();

        for (VillagerRecord record : records)
        {
            UUID uuid = record.uuid;
            VillagerRecord replacement = uuid != null ? observed.get(uuid) : null;

            if (replacement != null)
            {
                if (!seen.add(uuid))
                {
                    continue;
                }

                merged.add(replacement);
            }
            else
            {
                merged.add(record);

                if (uuid != null)
                {
                    seen.add(uuid);
                }
            }
        }

        for (Map.Entry<UUID, VillagerRecord> entry : observed.entrySet())
        {
            if (seen.add(entry.getKey()))
            {
                merged.add(entry.getValue());
            }
        }

        return merged;
    }

    /**
     * Records whose book trades are compared and that get a green/red marker: adult librarians
     * whose offers were captured. A librarian with trade data but no book trade stays in the
     * result as "not competitive" (red); without trade data it is not marked at all.
     */
    private static boolean isMarked(VillagerRecord record)
    {
        return record != null && !record.baby
                && LIBRARIAN.equals(record.professionId) && record.hasTradeData;
    }

    /**
     * Trades only compete within the same enchantment combination and the same cost items; the
     * comparison scope is the whole session, so the dimension is deliberately not part of the key.
     */
    static String groupKey(TradeEntry trade)
    {
        return enchantmentKey(trade) + "|" + trade.cost1Id + "|" + trade.cost2Id;
    }

    /**
     * Normalized enchantment-combo key: all (id, level) pairs sorted and joined, so a multi-
     * enchantment result (modded sources) only matches when the whole combination is identical.
     */
    static String enchantmentKey(TradeEntry trade)
    {
        List<EnchantEntry> entries = new ArrayList<>(trade.enchantments);
        entries.sort(Comparator.<EnchantEntry, String>comparing(entry -> entry.id).thenComparingInt(entry -> entry.level));

        StringBuilder sb = new StringBuilder();

        for (EnchantEntry entry : entries)
        {
            sb.append(entry.id).append('@').append(entry.level).append(';');
        }

        return sb.toString();
    }
}

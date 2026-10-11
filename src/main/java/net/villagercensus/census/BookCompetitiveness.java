package net.villagercensus.census;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Session-scope competitiveness verdict for librarian enchanted-book trades (section 1.2 of the
 * implementation plan). A book trade is competitive when no other participating book trade with
 * the same enchantment combination and the same cost items has a strictly lower price; equal
 * prices do not eliminate each other. A librarian is competitive when at least one of its book
 * trades is; adults without trade data deliberately fall into "not competitive" (red) as a
 * conservative reminder that their offers were never captured.
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
     * Computes the verdict per adult librarian record. Records that are not adult librarians are
     * absent from the result; records without a UUID (unresolved TXT forks) still take part in the
     * price comparison but produce no entry since they can never become a glow target.
     */
    public static Map<UUID, Boolean> compute(List<VillagerRecord> records)
    {
        Map<String, Integer> lowest = new HashMap<>();
        Map<String, List<TradeEntry>> byGroup = new HashMap<>();

        for (VillagerRecord record : records)
        {
            if (!contributes(record))
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
                byGroup.computeIfAbsent(group, key -> new ArrayList<>()).add(trade);
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

            if (record.hasTradeData)
            {
                for (TradeEntry trade : record.trades)
                {
                    if (trade.enchantedBook && lowest.getOrDefault(groupKey(trade), -1) == trade.cost1Count)
                    {
                        competitive = true;
                        break;
                    }
                }
            }

            result.put(record.uuid, competitive);
        }

        return result;
    }

    /**
     * Records whose book trades take part in the price comparison. Adults without trade data are
     * still marked (red) but contribute nothing to compare against.
     */
    private static boolean contributes(VillagerRecord record)
    {
        return isMarked(record) && record.hasTradeData;
    }

    private static boolean isMarked(VillagerRecord record)
    {
        return record != null && !record.baby && LIBRARIAN.equals(record.professionId);
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

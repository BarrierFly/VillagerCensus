package net.villagercensus.data;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import fi.dy.masa.malilib.util.StringUtils;
import net.villagercensus.Reference;
import net.villagercensus.census.CensusSession;
import net.villagercensus.census.DataStatus;
import net.villagercensus.census.EnchantEntry;
import net.villagercensus.census.TradeEntry;
import net.villagercensus.census.VillagerRecord;

/**
 * Rebuilds a session from a human-readable {@code .census.txt} report, used by /census fork when
 * the optional JSON sidecar was not written. Registry ids are embedded in the report for items
 * and professions, so a best-effort session (without UUIDs) can be reconstructed.
 */
public class ReportReader
{
    private static final Pattern ITEM_COUNT = Pattern.compile("^(.*)\\s+x(\\d+)$");
    private static final Pattern ITEM_ID = Pattern.compile(".*\\(([^()]+:[^()]+)\\)\\s*$");
    private static final Pattern ENCHANT_WITH_ID = Pattern.compile("^(.*?)\\s*\\(([^()]+:[^()]+)\\)\\s*(.*)$");
    private static final Pattern ENCHANT_NO_ID = Pattern.compile("^(.*?)\\s+([IVXLCDM]+|\\d+)(?:/(\\d+))?$");
    private static final Pattern HEALTH = Pattern.compile(".*?(-?\\d+(?:\\.\\d+)?)/(-?\\d+(?:\\.\\d+)?).*");
    private static final Pattern COORDS = Pattern.compile(".*?(-?\\d+),\\s*(-?\\d+),\\s*(-?\\d+).*");
    private static final Pattern PROFESSION_ID = Pattern.compile(".*\\(([^()]+:[^()]+)\\)\\s*$");

    public static CensusSession readText(Path file)
    {
        List<String> lines;

        try
        {
            lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        }
        catch (IOException e)
        {
            Reference.logger().warn("Failed to read census report {}", file, e);
            return null;
        }

        try
        {
            return parse(lines);
        }
        catch (Exception e)
        {
            Reference.logger().warn("Failed to parse census report {}", file, e);
            return null;
        }
    }

    private static CensusSession parse(List<String> lines)
    {
        int metaIndex = indexOfMeta(lines);

        if (metaIndex < 0 || metaIndex + 2 >= lines.size())
        {
            return null;
        }

        CensusSession session = new CensusSession();
        session.rawName = valueAfterColon(lines.get(metaIndex));
        session.worldId = valueAfterColon(lines.get(metaIndex + 1));
        session.dimension = valueAfterColon(lines.get(metaIndex + 2));
        session.safeName = WorldId.safe(session.rawName);

        int tradesIndex = lastSectionHeader(lines);

        if (tradesIndex >= 0)
        {
            parseRecords(lines, tradesIndex + 1, session);
        }

        session.rebuildIndex();
        return session;
    }

    private static void parseRecords(List<String> lines, int start, CensusSession session)
    {
        String tradedYes = StringUtils.translate("villagercensus.report.traded_yes");
        String tradedNo = StringUtils.translate("villagercensus.report.traded_no");
        String remarkLabel = StringUtils.translate("villagercensus.report.remark");
        Map<String, DataStatus> statusByText = statusTexts();

        String professionId = "minecraft:none";
        VillagerRecord current = null;

        for (int i = start; i < lines.size(); i++)
        {
            String raw = lines.get(i);

            if (raw.isEmpty())
            {
                continue;
            }

            if (raw.startsWith("      "))
            {
                String content = raw.substring(6);

                if (content.startsWith(remarkLabel + ": "))
                {
                    if (current != null)
                    {
                        current.remark = content.substring(remarkLabel.length() + 2);
                    }
                }
                else if (current != null)
                {
                    TradeEntry trade = parseTrade(content);

                    if (trade != null)
                    {
                        current.trades.add(trade);
                    }
                }

                continue;
            }

            if (raw.startsWith("  #"))
            {
                current = parseRecord(raw.trim(), tradedYes, tradedNo, statusByText);
                current.professionId = professionId;
                current.dimension = session.dimension;
                current.hasTradeData = current.status == DataStatus.FULL || current.status == DataStatus.WEAK;
                current.weakAssociation = current.status == DataStatus.WEAK;
                session.records.add(current);
                continue;
            }

            // A non-indented, non-empty line is a profession group header.
            Matcher matcher = PROFESSION_ID.matcher(raw.trim());

            if (matcher.matches())
            {
                professionId = matcher.group(1);
                current = null;
            }
        }
    }

    private static VillagerRecord parseRecord(String line, String tradedYes, String tradedNo,
                                              Map<String, DataStatus> statusByText)
    {
        VillagerRecord record = new VillagerRecord();
        record.hasCoordinates = false;
        String[] parts = line.split(" \\| ");

        if (parts.length > 0 && parts[0].startsWith("#"))
        {
            try
            {
                record.order = Long.parseLong(parts[0].substring(1).trim());
            }
            catch (NumberFormatException e)
            {
                record.order = 0L;
            }
        }

        if (parts.length > 1)
        {
            String levelPart = parts[1].trim();

            if (levelPart.startsWith("Lv"))
            {
                record.baby = false;
                record.level = parseInt(levelPart.substring(2).trim(), 0);
            }
            else
            {
                record.baby = true;
            }
        }

        if (parts.length > 2)
        {
            String traded = parts[2].trim();

            if (traded.equals(tradedYes))
            {
                record.hasTraded = true;
            }
            else if (traded.equals(tradedNo))
            {
                record.hasTraded = false;
            }
        }

        if (parts.length > 3)
        {
            Matcher health = HEALTH.matcher(parts[3]);
            record.maxHealth = -1.0F;

            if (health.matches())
            {
                record.health = parseFloat(health.group(1), 0.0F);
                record.maxHealth = parseFloat(health.group(2), -1.0F);
            }
        }

        for (int k = 4; k < parts.length; k++)
        {
            String part = parts[k].trim();

            if (part.isEmpty())
            {
                continue;
            }

            if (part.startsWith("\"") && part.endsWith("\"") && part.length() >= 2)
            {
                record.customName = part.substring(1, part.length() - 1);
                continue;
            }

            Matcher coords = COORDS.matcher(part);

            if (part.indexOf(',') >= 0 && coords.matches())
            {
                record.blockX = parseInt(coords.group(1), 0);
                record.blockY = parseInt(coords.group(2), 0);
                record.blockZ = parseInt(coords.group(3), 0);
                record.hasCoordinates = true;
                continue;
            }

            DataStatus status = statusByText.get(part);

            if (status != null)
            {
                record.status = status;
            }
        }

        return record;
    }

    private static TradeEntry parseTrade(String text)
    {
        String value = text.trim();
        String categoryId = "";

        if (value.endsWith("}"))
        {
            int open = value.lastIndexOf('{');

            if (open >= 0)
            {
                categoryId = value.substring(open + 1, value.length() - 1).trim();
                value = value.substring(0, open).trim();
            }
        }

        String enchantments = null;

        if (value.endsWith("]"))
        {
            int open = value.lastIndexOf('[');

            if (open >= 0)
            {
                enchantments = value.substring(open + 1, value.length() - 1);
                value = value.substring(0, open).trim();
            }
        }

        int arrow = value.indexOf(" -> ");

        if (arrow < 0)
        {
            return null;
        }

        String left = value.substring(0, arrow).trim();
        String right = value.substring(arrow + 4).trim();
        String[] costs = left.split(" \\+ ");
        ItemRef cost1 = parseItem(costs[0]);
        ItemRef cost2 = costs.length > 1 ? parseItem(costs[1]) : null;
        ItemRef result = parseItem(right);

        TradeEntry trade = new TradeEntry();
        trade.categoryId = categoryId;
        trade.cost1Id = cost1.id;
        trade.cost1Count = cost1.count;
        trade.cost2Id = cost2 != null ? cost2.id : "";
        trade.cost2Count = cost2 != null ? cost2.count : 0;
        trade.sellId = result.id;
        trade.sellCount = result.count;
        trade.enchantedBook = "minecraft:enchanted_book".equals(result.id);
        trade.enchantments = parseEnchantments(enchantments);
        return trade;
    }

    private static ItemRef parseItem(String token)
    {
        String value = token.trim();
        int count = 1;
        Matcher countMatcher = ITEM_COUNT.matcher(value);

        if (countMatcher.matches())
        {
            value = countMatcher.group(1).trim();
            count = parseInt(countMatcher.group(2), 1);
        }

        Matcher idMatcher = ITEM_ID.matcher(value);
        String id = idMatcher.matches() ? idMatcher.group(1).trim() : value;
        return new ItemRef(id, count);
    }

    private static List<EnchantEntry> parseEnchantments(String text)
    {
        List<EnchantEntry> list = new ArrayList<>();

        if (text == null || text.trim().isEmpty())
        {
            return list;
        }

        for (String token : text.split(", "))
        {
            String value = token.trim();

            if (value.isEmpty())
            {
                continue;
            }

            Matcher withId = ENCHANT_WITH_ID.matcher(value);

            if (withId.matches())
            {
                list.add(enchant(withId.group(2).trim(), withId.group(3).trim()));
                continue;
            }

            Matcher noId = ENCHANT_NO_ID.matcher(value);

            if (noId.matches())
            {
                int level = romanOrInt(noId.group(2));
                int max = noId.group(3) != null ? parseInt(noId.group(3), -1) : -1;
                list.add(new EnchantEntry("", level, max));
            }
        }

        return list;
    }

    private static EnchantEntry enchant(String id, String levelAndMax)
    {
        int level = 1;
        int max = -1;
        int slash = levelAndMax.indexOf('/');

        if (slash >= 0)
        {
            level = romanOrInt(levelAndMax.substring(0, slash).trim());
            max = parseInt(levelAndMax.substring(slash + 1).trim(), -1);
        }
        else if (!levelAndMax.isEmpty())
        {
            level = romanOrInt(levelAndMax);
        }

        return new EnchantEntry(id, level, max);
    }

    private static Map<String, DataStatus> statusTexts()
    {
        Map<String, DataStatus> map = new HashMap<>();

        for (DataStatus status : DataStatus.values())
        {
            map.put(StringUtils.translate("villagercensus.report.status." + status.getId()), status);
        }

        return map;
    }

    private static int indexOfMeta(List<String> lines)
    {
        for (int i = 0; i < lines.size(); i++)
        {
            if (lines.get(i).contains(": "))
            {
                return i;
            }
        }

        return -1;
    }

    private static int lastSectionHeader(List<String> lines)
    {
        int index = -1;

        for (int i = 0; i < lines.size(); i++)
        {
            String trimmed = lines.get(i).trim();

            if (trimmed.startsWith("[") && trimmed.endsWith("]"))
            {
                index = i;
            }
        }

        return index;
    }

    private static String valueAfterColon(String line)
    {
        int colon = line.indexOf(": ");
        return colon >= 0 ? line.substring(colon + 2).trim() : line.trim();
    }

    private static int parseInt(String value, int fallback)
    {
        try
        {
            return (int) Double.parseDouble(value.trim());
        }
        catch (NumberFormatException e)
        {
            return fallback;
        }
    }

    private static float parseFloat(String value, float fallback)
    {
        try
        {
            return Float.parseFloat(value.trim());
        }
        catch (NumberFormatException e)
        {
            return fallback;
        }
    }

    private static int romanOrInt(String value)
    {
        String text = value.trim();

        if (text.matches("\\d+"))
        {
            return parseInt(text, 0);
        }

        int result = 0;
        int previous = 0;

        for (int i = text.length() - 1; i >= 0; i--)
        {
            int current = romanValue(text.charAt(i));

            if (current < previous)
            {
                result -= current;
            }
            else
            {
                result += current;
                previous = current;
            }
        }

        return result;
    }

    private static int romanValue(char c)
    {
        switch (c)
        {
            case 'I': return 1;
            case 'V': return 5;
            case 'X': return 10;
            case 'L': return 50;
            case 'C': return 100;
            case 'D': return 500;
            case 'M': return 1000;
            default: return 0;
        }
    }

    private static final class ItemRef
    {
        final String id;
        final int count;

        ItemRef(String id, int count)
        {
            this.id = id;
            this.count = count;
        }
    }
}

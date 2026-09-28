package net.villagercensus.data;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import fi.dy.masa.malilib.util.FileUtils;
import fi.dy.masa.malilib.util.StringUtils;
import net.villagercensus.Reference;
import net.villagercensus.census.CensusSession;
import net.villagercensus.census.EnchantEntry;
import net.villagercensus.census.TradeEntry;
import net.villagercensus.census.VillagerRecord;
import net.villagercensus.util.Names;

public class ReportWriter
{
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final DateTimeFormatter READABLE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static Path rootDirectory()
    {
        return FileUtils.getMinecraftDirectory().resolve("villager_census");
    }

    public static Path reportDirectory(String worldId)
    {
        return rootDirectory().resolve(WorldId.safe(worldId));
    }

    public static String baseName(CensusSession session)
    {
        return session.safeName + "_" + WorldId.safeDimension(session.dimension);
    }

    public static Path writeReport(CensusSession session)
    {
        Path dir = reportDirectory(session.worldId);

        try
        {
            Files.createDirectories(dir);
            String stamp = FILE_TIME.format(Instant.now().atZone(ZoneId.systemDefault()));
            Path txt = dir.resolve(baseName(session) + "_" + stamp + ".census.txt");
            writeText(session, txt);

            // The JSON report is always written: /census fork rebuilds a session from it, so a
            // completed report must stay forkable regardless of user configuration.
            Path jsonPath = dir.resolve(baseName(session) + "_" + stamp + ".census.json");
            Files.writeString(jsonPath, toJson(session), StandardCharsets.UTF_8);

            return txt;
        }
        catch (IOException e)
        {
            Reference.logger().warn("Failed to write census report", e);
            return null;
        }
    }

    private static void writeText(CensusSession session, Path file) throws IOException
    {
        try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8))
        {
            String line = "================================================================";
            writer.write(line);
            writer.newLine();
            writer.write(StringUtils.translate("villagercensus.report.title"));
            writer.newLine();
            writer.write(line);
            writer.newLine();
            writer.write(kv("villagercensus.report.session_name", session.rawName));
            writer.write(kv("villagercensus.report.world", session.worldId));
            writer.write(kv("villagercensus.report.dimension", session.dimension));
            writer.write(kv("villagercensus.report.start", format(session.startTime)));
            writer.write(kv("villagercensus.report.end", format(session.endTime == 0L ? System.currentTimeMillis() : session.endTime)));
            writer.write(kv("villagercensus.report.total", String.valueOf(session.totalCount())));
            writer.newLine();

            writer.write("[" + StringUtils.translate("villagercensus.report.profession_counts") + "]");
            writer.newLine();

            for (java.util.Map.Entry<String, Integer> entry : session.professionCounts().entrySet())
            {
                writer.write("  " + Names.profession(entry.getKey()) + " : " + entry.getValue());
                writer.newLine();
            }

            writer.write("  " + StringUtils.translate("villagercensus.report.baby") + " : " + session.babyCount());
            writer.newLine();
            writer.write("  " + StringUtils.translate("villagercensus.report.baby_note"));
            writer.newLine();
            writer.newLine();

            writer.write("[" + StringUtils.translate("villagercensus.report.trades") + "]");
            writer.newLine();

            LinkedHashMap<String, List<VillagerRecord>> byProfession = groupByProfession(session);
            List<String> professionOrder = new ArrayList<>(byProfession.keySet());
            professionOrder.sort((a, b) -> Long.compare(firstOrder(byProfession.get(a)), firstOrder(byProfession.get(b))));

            for (String profession : professionOrder)
            {
                writer.write(Names.profession(profession));
                writer.newLine();

                List<VillagerRecord> members = byProfession.get(profession);
                members.sort((a, b) -> Long.compare(a.order, b.order));

                for (VillagerRecord record : members)
                {
                    writeVillager(writer, record);
                }

                writer.newLine();
            }
        }
    }

    private static void writeVillager(BufferedWriter writer, VillagerRecord record) throws IOException
    {
        StringBuilder header = new StringBuilder("  #").append(record.order);
        header.append(" | ").append(record.baby ? StringUtils.translate("villagercensus.report.baby_short") : ("Lv" + record.level));
        header.append(" | ").append(StringUtils.translate(record.hasTraded
                ? "villagercensus.report.traded_yes"
                : "villagercensus.report.traded_no"));
        header.append(" | ").append(StringUtils.translate("villagercensus.report.health", (int) record.health, (int) record.maxHealth));

        if (record.customName != null && !record.customName.isEmpty())
        {
            header.append(" | \"").append(record.customName).append('"');
        }

        header.append(" | ").append(StringUtils.translate("villagercensus.report.coords",
                record.blockX, record.blockY, record.blockZ));

        if (record.status != null)
        {
            header.append(" | ").append(StringUtils.translate("villagercensus.report.status." + record.status.getId()));
        }

        writer.write(header.toString());
        writer.newLine();

        for (TradeEntry trade : record.trades)
        {
            writer.write("      " + formatTrade(trade));
            writer.newLine();
        }

        if (record.remark != null && !record.remark.isEmpty())
        {
            writer.write("      " + StringUtils.translate("villagercensus.report.remark") + ": " + record.remark);
            writer.newLine();
        }
    }

    public static String formatTrade(TradeEntry trade)
    {
        StringBuilder sb = new StringBuilder();
        sb.append(Names.item(trade.cost1Id)).append(" x").append(trade.cost1Count);

        if (trade.cost2Id != null && !trade.cost2Id.isEmpty())
        {
            sb.append(" + ").append(Names.item(trade.cost2Id)).append(" x").append(trade.cost2Count);
        }

        sb.append(" -> ").append(Names.item(trade.sellId)).append(" x").append(trade.sellCount);

        if (trade.enchantments != null && !trade.enchantments.isEmpty())
        {
            sb.append(" [");

            for (int i = 0; i < trade.enchantments.size(); i++)
            {
                EnchantEntry enchant = trade.enchantments.get(i);

                if (i > 0)
                {
                    sb.append(", ");
                }

                sb.append(Names.enchantment(enchant.id, enchant.level, enchant.maxLevel));
            }

            sb.append(']');
        }

        if (trade.categoryId != null && !trade.categoryId.isEmpty())
        {
            sb.append(" {").append(trade.categoryId).append('}');
        }

        return sb.toString();
    }

    private static LinkedHashMap<String, List<VillagerRecord>> groupByProfession(CensusSession session)
    {
        LinkedHashMap<String, List<VillagerRecord>> map = new LinkedHashMap<>();

        for (VillagerRecord record : session.records)
        {
            if (record.baby)
            {
                continue;
            }

            map.computeIfAbsent(record.professionId, k -> new ArrayList<>()).add(record);
        }

        return map;
    }

    private static long firstOrder(List<VillagerRecord> records)
    {
        long min = Long.MAX_VALUE;

        for (VillagerRecord record : records)
        {
            min = Math.min(min, record.order);
        }

        return min;
    }

    private static String kv(String key, String value)
    {
        return StringUtils.translate(key) + ": " + value + System.lineSeparator();
    }

    private static String format(long time)
    {
        return READABLE.format(Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()));
    }

    private static String toJson(CensusSession session)
    {
        com.google.gson.Gson gson = new com.google.gson.GsonBuilder().setPrettyPrinting().create();
        return gson.toJson(session);
    }
}

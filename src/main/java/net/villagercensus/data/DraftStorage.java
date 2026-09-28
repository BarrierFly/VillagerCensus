package net.villagercensus.data;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.villagercensus.Reference;
import net.villagercensus.census.CensusSession;

public class DraftStorage
{
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final DateTimeFormatter ARCHIVE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final String DRAFT_SUFFIX = ".census.draft.json";

    public static Path draftPath(CensusSession session)
    {
        return ReportWriter.reportDirectory(session.worldId)
                .resolve(ReportWriter.baseName(session) + DRAFT_SUFFIX);
    }

    public static boolean save(CensusSession session)
    {
        Path file = draftPath(session);

        try
        {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(session), StandardCharsets.UTF_8);
            return true;
        }
        catch (IOException e)
        {
            Reference.logger().warn("Failed to save session draft", e);
            return false;
        }
    }

    public static CensusSession load(Path file)
    {
        try
        {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            CensusSession session = GSON.fromJson(json, CensusSession.class);

            if (session != null)
            {
                session.rebuildIndex();
            }

            return session;
        }
        catch (Exception e)
        {
            Reference.logger().warn("Failed to load session draft from {}", file, e);
            return null;
        }
    }

    public static List<Path> findDrafts(String worldId)
    {
        List<Path> result = new ArrayList<>();
        Path dir = ReportWriter.reportDirectory(worldId);

        if (!Files.isDirectory(dir))
        {
            return result;
        }

        try (Stream<Path> stream = Files.list(dir))
        {
            stream.filter(p -> p.getFileName().toString().endsWith(DRAFT_SUFFIX)).forEach(result::add);
        }
        catch (IOException e)
        {
            Reference.logger().warn("Failed to list session drafts", e);
        }

        return result;
    }

    public static Path findDraft(String worldId, String dimension, String safeName)
    {
        Path dir = ReportWriter.reportDirectory(worldId);
        Path file = dir.resolve(safeName + "_" + WorldId.safeDimension(dimension) + DRAFT_SUFFIX);
        return Files.exists(file) ? file : null;
    }

    public static Path findAnyDraft(String worldId, String dimension)
    {
        for (Path draft : findDrafts(worldId))
        {
            CensusSession session = load(draft);

            if (session != null && WorldId.safeDimension(dimension).equals(WorldId.safeDimension(session.dimension)))
            {
                return draft;
            }
        }

        return null;
    }

    public static final class ForkSource
    {
        public final Path path;
        public final CensusSession session;
        public final boolean crossDimension;

        ForkSource(Path path, CensusSession session, boolean crossDimension)
        {
            this.path = path;
            this.session = session;
            this.crossDimension = crossDimension;
        }
    }

    /**
     * Finds a fork source by the session name it recorded (draft or completed report). A source
     * in the current dimension is preferred; if only a source recorded in another dimension
     * exists, it is returned with {@link ForkSource#crossDimension} set.
     */
    public static ForkSource findForkSource(String worldId, String dimension, String rawName)
    {
        Path dir = ReportWriter.reportDirectory(worldId);

        if (!Files.isDirectory(dir))
        {
            return null;
        }

        String safeName = WorldId.safe(rawName);
        String safeDimension = WorldId.safeDimension(dimension);
        String prefix = safeName + "_";
        List<Path> candidates = new ArrayList<>(findDrafts(worldId));

        try (Stream<Path> stream = Files.list(dir))
        {
            stream.filter(p -> p.getFileName().toString().endsWith(".census.json")).forEach(candidates::add);
        }
        catch (IOException e)
        {
            Reference.logger().warn("Failed to list census reports", e);
        }

        List<ForkSource> matches = new ArrayList<>();

        for (Path candidate : candidates)
        {
            if (!candidate.getFileName().toString().startsWith(prefix))
            {
                continue;
            }

            CensusSession session = load(candidate);

            if (session == null || !matchesName(session, rawName, safeName))
            {
                continue;
            }

            boolean sameDimension = safeDimension.equals(WorldId.safeDimension(session.dimension));
            matches.add(new ForkSource(candidate, session, !sameDimension));
        }

        if (matches.isEmpty())
        {
            return null;
        }

        // Same dimension first, then drafts, then the newest file name.
        matches.sort((a, b) ->
        {
            int cmp = Boolean.compare(a.crossDimension, b.crossDimension);

            if (cmp != 0)
            {
                return cmp;
            }

            cmp = Boolean.compare(!isDraft(a.path), !isDraft(b.path));

            if (cmp != 0)
            {
                return cmp;
            }

            return b.path.getFileName().toString().compareTo(a.path.getFileName().toString());
        });

        return matches.get(0);
    }

    private static boolean matchesName(CensusSession session, String rawName, String safeName)
    {
        if (session.safeName != null && session.safeName.equals(safeName))
        {
            return true;
        }

        return session.rawName != null && session.rawName.equals(rawName.trim());
    }

    private static boolean isDraft(Path file)
    {
        return file.getFileName().toString().endsWith(DRAFT_SUFFIX);
    }

    public static boolean delete(Path file)
    {
        try
        {
            return Files.deleteIfExists(file);
        }
        catch (IOException e)
        {
            return false;
        }
    }

    public static Path archive(Path file)
    {
        String stamp = ARCHIVE_TIME.format(Instant.now().atZone(ZoneId.systemDefault()));
        String name = file.getFileName().toString();
        String base = name.endsWith(DRAFT_SUFFIX) ? name.substring(0, name.length() - DRAFT_SUFFIX.length()) : name;
        Path target = file.resolveSibling(base + "_orphan_" + stamp + DRAFT_SUFFIX);

        try
        {
            Files.move(file, target);
            return target;
        }
        catch (IOException e)
        {
            Reference.logger().warn("Failed to archive session draft", e);
            return null;
        }
    }
}

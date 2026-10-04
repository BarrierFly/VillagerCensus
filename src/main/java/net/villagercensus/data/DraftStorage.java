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
    private static final String ORPHAN_MARKER = "_orphan_";
    private static final String REPORT_JSON_SUFFIX = ".census.json";
    private static final String REPORT_TXT_SUFFIX = ".census.txt";

    public enum ConflictKind
    {
        NONE,
        DRAFT,
        DIMENSION_MISMATCH,
        CORRUPT,
        COMPLETED_REPORT
    }

    /**
     * What {@code /census start <name>} would collide with in the current world.
     */
    public static final class NameConflict
    {
        public static final NameConflict NONE = new NameConflict(ConflictKind.NONE, null, null, null, 0);

        public final ConflictKind kind;
        // The existing draft (DRAFT / DIMENSION_MISMATCH / CORRUPT) or the newest colliding report.
        public final Path path;
        // Session name recorded inside the colliding file, when it could be read.
        public final String recordedName;
        // Dimension recorded inside the colliding draft, when it could be read.
        public final String recordedDimension;
        // How many files with the same name prefix were found (reports only).
        public final int reportCount;

        NameConflict(ConflictKind kind, Path path, String recordedName, String recordedDimension, int reportCount)
        {
            this.kind = kind;
            this.path = path;
            this.recordedName = recordedName;
            this.recordedDimension = recordedDimension;
            this.reportCount = reportCount;
        }

        public boolean isNone()
        {
            return this.kind == ConflictKind.NONE;
        }
    }

    public static Path draftPath(CensusSession session)
    {
        return reportDirectory(session.worldId)
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

    public static Path reportDirectory(String worldId)
    {
        return ReportWriter.reportDirectory(worldId);
    }

    public static List<Path> findDrafts(String worldId)
    {
        return findDrafts(reportDirectory(worldId));
    }

    public static List<Path> findDrafts(Path dir)
    {
        List<Path> result = new ArrayList<>();

        if (!Files.isDirectory(dir))
        {
            return result;
        }

        try (Stream<Path> stream = Files.list(dir))
        {
            stream.filter(p -> isLiveDraft(p.getFileName().toString())).forEach(result::add);
        }
        catch (IOException e)
        {
            Reference.logger().warn("Failed to list session drafts", e);
        }

        return result;
    }

    /**
     * Archived drafts carry an {@code _orphan_<stamp>} segment and must never be offered again as a
     * resume candidate, a start conflict or a fork source.
     */
    private static boolean isLiveDraft(String name)
    {
        return name.endsWith(DRAFT_SUFFIX) && !name.contains(ORPHAN_MARKER);
    }

    public static Path findDraft(String worldId, String dimension, String safeName)
    {
        return findDraft(reportDirectory(worldId), dimension, safeName);
    }

    public static Path findDraft(Path dir, String dimension, String safeName)
    {
        Path file = dir.resolve(safeName + "_" + WorldId.safeDimension(dimension) + DRAFT_SUFFIX);
        return Files.exists(file) ? file : null;
    }

    public static Path findAnyDraft(String worldId, String dimension)
    {
        return findAnyDraft(reportDirectory(worldId), dimension);
    }

    public static Path findAnyDraft(Path dir, String dimension)
    {
        for (Path draft : findDrafts(dir))
        {
            CensusSession session = load(draft);

            if (session != null && WorldId.safeDimension(dimension).equals(WorldId.safeDimension(session.dimension)))
            {
                return draft;
            }
        }

        return null;
    }

    /**
     * Classifies what a {@code /census start <name>} would run into. Drafts win over completed
     * reports; among drafts the one for the current dimension wins, then one from another
     * dimension, then an unreadable one.
     */
    public static NameConflict findConflicts(String worldId, String dimension, String safeName)
    {
        return findConflicts(reportDirectory(worldId), dimension, safeName);
    }

    public static NameConflict findConflicts(Path dir, String dimension, String safeName)
    {
        if (!Files.isDirectory(dir))
        {
            return NameConflict.NONE;
        }

        String safeDimension = WorldId.safeDimension(dimension);
        String prefix = safeName + "_";
        NameConflict mismatch = null;
        NameConflict corrupt = null;

        for (Path draft : findDrafts(dir))
        {
            if (!draft.getFileName().toString().startsWith(prefix))
            {
                continue;
            }

            CensusSession session = load(draft);

            if (session == null)
            {
                corrupt = corrupt != null ? corrupt : new NameConflict(ConflictKind.CORRUPT, draft, null, null, 0);
                continue;
            }

            if (safeDimension.equals(WorldId.safeDimension(session.dimension)))
            {
                return new NameConflict(ConflictKind.DRAFT, draft, session.rawName, session.dimension, 0);
            }

            if (mismatch == null)
            {
                mismatch = new NameConflict(ConflictKind.DIMENSION_MISMATCH, draft,
                        session.rawName, session.dimension, 0);
            }
        }

        if (mismatch != null)
        {
            return mismatch;
        }

        if (corrupt != null)
        {
            return corrupt;
        }

        List<Path> reports = findReports(dir, prefix);

        if (reports.isEmpty())
        {
            return NameConflict.NONE;
        }

        Path newest = reports.get(0);
        CensusSession session = loadSource(newest);
        return new NameConflict(ConflictKind.COMPLETED_REPORT, newest,
                session != null ? session.rawName : null,
                session != null ? session.dimension : null,
                reports.size());
    }

    private static List<Path> findReports(Path dir, String prefix)
    {
        List<Path> result = new ArrayList<>();

        try (Stream<Path> stream = Files.list(dir))
        {
            stream.filter(p ->
            {
                String name = p.getFileName().toString();
                return name.startsWith(prefix)
                        && (name.endsWith(REPORT_JSON_SUFFIX) || name.endsWith(REPORT_TXT_SUFFIX));
            }).forEach(result::add);
        }
        catch (IOException e)
        {
            Reference.logger().warn("Failed to list census reports", e);
        }

        // Newest first, so the JSON sidecar of a report wins over its TXT counterpart.
        result.sort((a, b) -> b.getFileName().toString().compareTo(a.getFileName().toString()));
        return result;
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
     * Finds a fork source by the session name it recorded. Drafts, completed JSON reports and
     * completed TXT reports are all considered. A same-dimension source is always preferred over
     * a cross-dimension one (so a same-dimension TXT beats a cross-dimension JSON); within the
     * same dimension a JSON source is preferred over a TXT one, drafts first.
     */
    public static ForkSource findForkSource(String worldId, String dimension, String rawName)
    {
        return findForkSource(reportDirectory(worldId), dimension, rawName);
    }

    public static ForkSource findForkSource(Path dir, String dimension, String rawName)
    {
        if (!Files.isDirectory(dir))
        {
            return null;
        }

        String safeName = WorldId.safe(rawName);
        String safeDimension = WorldId.safeDimension(dimension);
        String prefix = safeName + "_";
        List<Path> candidates = new ArrayList<>(findDrafts(dir));

        for (Path report : findReports(dir, prefix))
        {
            candidates.add(report);
        }

        List<ForkSource> matches = new ArrayList<>();

        for (Path candidate : candidates)
        {
            if (!candidate.getFileName().toString().startsWith(prefix))
            {
                continue;
            }

            CensusSession session = loadSource(candidate);

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

        // Same dimension first, then drafts / JSON over TXT, then the newest file name.
        matches.sort((a, b) ->
        {
            int cmp = Boolean.compare(a.crossDimension, b.crossDimension);

            if (cmp != 0)
            {
                return cmp;
            }

            cmp = Integer.compare(kindRank(a.path), kindRank(b.path));

            if (cmp != 0)
            {
                return cmp;
            }

            return b.path.getFileName().toString().compareTo(a.path.getFileName().toString());
        });

        return matches.get(0);
    }

    /**
     * Loads a session from a fork source file: JSON (draft or report) directly, or a TXT report
     * through {@link ReportReader} when no JSON sidecar exists.
     */
    public static CensusSession loadSource(Path file)
    {
        return file.getFileName().toString().endsWith(REPORT_TXT_SUFFIX)
                ? ReportReader.readText(file)
                : load(file);
    }

    private static int kindRank(Path file)
    {
        String name = file.getFileName().toString();

        if (name.endsWith(DRAFT_SUFFIX))
        {
            return 0;
        }

        if (name.endsWith(REPORT_JSON_SUFFIX))
        {
            return 1;
        }

        if (name.endsWith(REPORT_TXT_SUFFIX))
        {
            return 2;
        }

        return 3;
    }

    private static boolean matchesName(CensusSession session, String rawName, String safeName)
    {
        if (session.safeName != null && session.safeName.equals(safeName))
        {
            return true;
        }

        return session.rawName != null && session.rawName.equals(rawName.trim());
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
        Path target = file.resolveSibling(base + ORPHAN_MARKER + stamp + DRAFT_SUFFIX);

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
package net.villagercensus.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.villagercensus.census.CensusSession;
import net.villagercensus.census.VillagerRecord;

class DraftStorageLookupTest
{
    private static final String OVERWORLD = "minecraft:overworld";
    private static final String NETHER = "minecraft:the_nether";

    @TempDir
    Path dir;

    @Test
    void missingDirectoryYieldsNoConflictAndNoForkSource()
    {
        Path missing = dir.resolve("absent");

        assertTrue(DraftStorage.findConflicts(missing, OVERWORLD, "hall").isNone());
        assertNull(DraftStorage.findForkSource(missing, OVERWORLD, "hall"));
        assertTrue(DraftStorage.findDrafts(missing).isEmpty());
        assertNull(DraftStorage.findDraft(missing, OVERWORLD, "hall"));
    }

    @Test
    void aFreshNameHasNoConflict() throws IOException
    {
        writeDraft("hall", OVERWORLD, "hall");

        assertTrue(DraftStorage.findConflicts(dir, OVERWORLD, "other").isNone());
    }

    @Test
    void anExistingDraftInTheSameDimensionIsADraftConflict() throws IOException
    {
        writeDraft("hall", OVERWORLD, "hall");
        DraftStorage.NameConflict conflict = DraftStorage.findConflicts(dir, OVERWORLD, "hall");

        assertEquals(DraftStorage.ConflictKind.DRAFT, conflict.kind);
        assertTrue(conflict.path.getFileName().toString().startsWith("hall_"));
        assertEquals("hall", conflict.recordedName);
    }

    @Test
    void aDraftFromAnotherDimensionIsADimensionMismatch() throws IOException
    {
        writeDraft("hall", NETHER, "hall");
        DraftStorage.NameConflict conflict = DraftStorage.findConflicts(dir, OVERWORLD, "hall");

        assertEquals(DraftStorage.ConflictKind.DIMENSION_MISMATCH, conflict.kind);
        assertEquals(NETHER, conflict.recordedDimension);
    }

    @Test
    void anUnreadableDraftIsReportedAsCorrupt() throws IOException
    {
        writeFile("hall_" + WorldId.safeDimension(OVERWORLD) + ".census.draft.json", "{ not json");

        DraftStorage.NameConflict conflict = DraftStorage.findConflicts(dir, OVERWORLD, "hall");
        assertEquals(DraftStorage.ConflictKind.CORRUPT, conflict.kind);
    }

    @Test
    void aSameDimensionDraftBeatsAReportAndADimensionMismatch() throws IOException
    {
        writeDraft("hall", OVERWORLD, "hall");
        writeDraft("hall", NETHER, "hall");
        writeReport("hall", OVERWORLD, ".census.json");

        assertEquals(DraftStorage.ConflictKind.DRAFT, DraftStorage.findConflicts(dir, OVERWORLD, "hall").kind);
    }

    @Test
    void onlyReportsMeansACompletedReportConflict() throws IOException
    {
        writeReport("hall", OVERWORLD, ".census.json");
        writeReport("hall", OVERWORLD, ".census.txt");

        DraftStorage.NameConflict conflict = DraftStorage.findConflicts(dir, OVERWORLD, "hall");
        assertEquals(DraftStorage.ConflictKind.COMPLETED_REPORT, conflict.kind);
        assertEquals(2, conflict.reportCount);
    }

    @Test
    void reportsOfOtherNamesAreIgnored() throws IOException
    {
        writeReport("hall", OVERWORLD, ".census.json");
        writeReport("hallway", OVERWORLD, ".census.json");

        assertTrue(DraftStorage.findConflicts(dir, OVERWORLD, "library").isNone());
    }

    @Test
    void findDraftMatchesTheExactWorldDimensionKey() throws IOException
    {
        writeDraft("hall", OVERWORLD, "hall");

        assertNotNull(DraftStorage.findDraft(dir, OVERWORLD, "hall"));
        assertNull(DraftStorage.findDraft(dir, NETHER, "hall"));
    }

    @Test
    void findAnyDraftOnlyReturnsTheCurrentDimension() throws IOException
    {
        writeDraft("hall", NETHER, "hall");
        assertNull(DraftStorage.findAnyDraft(dir, OVERWORLD));

        writeDraft("hall", OVERWORLD, "hall");
        assertNotNull(DraftStorage.findAnyDraft(dir, OVERWORLD));
    }

    @Test
    void forkPrefersTheSameDimensionOverACrossDimensionSource() throws IOException
    {
        writeDraft("hall", NETHER, "hall");
        writeReport("hall", OVERWORLD, ".census.json");

        DraftStorage.ForkSource source = DraftStorage.findForkSource(dir, OVERWORLD, "hall");
        assertNotNull(source);
        assertFalse(source.crossDimension);
    }

    @Test
    void forkPrefersADraftOverACompletedReportInTheSameDimension() throws IOException
    {
        writeReport("hall", OVERWORLD, ".census.json");
        writeDraft("hall", OVERWORLD, "hall");

        DraftStorage.ForkSource source = DraftStorage.findForkSource(dir, OVERWORLD, "hall");
        assertNotNull(source);
        assertTrue(source.path.getFileName().toString().endsWith(".census.draft.json"));
    }

    @Test
    void forkPrefersTheJsonSidecarOverTheTxtReport() throws IOException
    {
        writeReport("hall", OVERWORLD, ".census.json");
        writeReport("hall", OVERWORLD, ".census.txt");

        DraftStorage.ForkSource source = DraftStorage.findForkSource(dir, OVERWORLD, "hall");
        assertNotNull(source);
        assertTrue(source.path.getFileName().toString().endsWith(".census.json"));
    }

    @Test
    void forkFallsBackToACrossDimensionSource() throws IOException
    {
        writeDraft("hall", NETHER, "hall");

        DraftStorage.ForkSource source = DraftStorage.findForkSource(dir, OVERWORLD, "hall");
        assertNotNull(source);
        assertTrue(source.crossDimension);
        assertEquals(NETHER, source.session.dimension);
    }

    @Test
    void forkMatchesTheRecordedSessionNameNotTheFileNamePrefix() throws IOException
    {
        // Same file name prefix, but the session recorded inside is a different name: the recorded
        // name decides, so this must not be offered as a fork source for "hall".
        writeFile("hall_" + WorldId.safeDimension(OVERWORLD) + ".census.json",
                json(session("hallway", WorldId.safe("hallway"), OVERWORLD)));

        assertNull(DraftStorage.findForkSource(dir, OVERWORLD, "hall"));

        writeFile("hall_" + WorldId.safeDimension(OVERWORLD) + ".census.json",
                json(session("hall", "hall", OVERWORLD)));

        DraftStorage.ForkSource source = DraftStorage.findForkSource(dir, OVERWORLD, "hall");
        assertNotNull(source);
        assertEquals("hall", source.session.rawName);
    }

    @Test
    void archiveRenamesTheDraftAndLeavesTheOriginalGone() throws IOException
    {
        Path draft = writeDraft("hall", OVERWORLD, "hall");
        Path archived = DraftStorage.archive(draft);

        assertNotNull(archived);
        assertFalse(Files.exists(draft));
        assertTrue(archived.getFileName().toString().contains("_orphan_"));
        assertTrue(archived.getFileName().toString().endsWith(".census.draft.json"));

        // An archived draft must no longer be offered as a resume candidate or fork source.
        assertNull(DraftStorage.findAnyDraft(dir, OVERWORLD));
        assertNull(DraftStorage.findForkSource(dir, OVERWORLD, "hall"));
        assertTrue(DraftStorage.findConflicts(dir, OVERWORLD, "hall").isNone());
    }

    @Test
    void deleteReportsWhetherSomethingWasRemoved() throws IOException
    {
        Path draft = writeDraft("hall", OVERWORLD, "hall");

        assertTrue(DraftStorage.delete(draft));
        assertFalse(DraftStorage.delete(draft));
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private Path writeDraft(String filePrefix, String dimension, String rawName) throws IOException
    {
        return writeFile(filePrefix + "_" + WorldId.safeDimension(dimension) + ".census.draft.json",
                json(session(rawName, filePrefix, dimension)));
    }

    private Path writeReport(String filePrefix, String dimension, String suffix) throws IOException
    {
        return writeFile(filePrefix + "_" + WorldId.safeDimension(dimension) + "_20260101_120000" + suffix,
                json(session(filePrefix, filePrefix, dimension)));
    }

    private Path writeFile(String name, String content) throws IOException
    {
        Path file = dir.resolve(name);
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }

    private static CensusSession session(String rawName, String safeName, String dimension)
    {
        CensusSession session = new CensusSession(rawName, safeName, "world", dimension, 1L);
        VillagerRecord record = new VillagerRecord();
        record.uuid = UUID.randomUUID();
        record.professionId = "minecraft:librarian";
        record.dimension = dimension;
        session.addRecord(record);
        return session;
    }

    private static String json(CensusSession session)
    {
        return new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(session);
    }
}
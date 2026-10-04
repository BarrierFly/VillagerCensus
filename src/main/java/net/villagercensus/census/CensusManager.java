package net.villagercensus.census;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.util.StringUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.network.protocol.game.ClientboundMerchantOffersPacket;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.villagercensus.Reference;
import net.villagercensus.config.Configs;
import net.villagercensus.data.DraftStorage;
import net.villagercensus.data.ReportWriter;
import net.villagercensus.data.WorldId;
import net.villagercensus.gui.GuiDraftResume;
import net.villagercensus.gui.GuiStartConflict;
import net.villagercensus.mixin.IMixinEntity;
import net.villagercensus.trade.TradeCatalog;
import net.villagercensus.trade.TradeCategory;
import net.villagercensus.util.Compat;
import net.villagercensus.util.Messages;
import net.villagercensus.util.Names;

public class CensusManager
{
    private static final CensusManager INSTANCE = new CensusManager();

    // The resume screen and the "loaded X/N" counter are refreshed twice a second (Q31: 10 gt).
    private static final long VERIFY_REFRESH_TICKS = 10L;
    // Ticks to wait after joining a world before the resume screen may open.
    private static final int DRAFT_PROMPT_DELAY_TICKS = 20;

    private volatile CensusSession session;
    private volatile PendingTarget pending;
    private PendingUpdate pendingUpdate;
    private volatile long tickCounter;
    private long lastAutoSaveTick;

    // Glowing markers. baseGlow is the glow state coming from any source other than this mod
    // (the server's shared flag). managed is the set of villagers whose glow this mod controls.
    // The visible glow is always baseGlow || censusGlow, so other glow sources are preserved.
    private final Map<UUID, Boolean> baseGlow = new LinkedHashMap<>();
    private final Set<UUID> managed = new HashSet<>();
    private volatile boolean reverseMarkers;
    private volatile boolean trackingMarkers;
    private final java.util.concurrent.ConcurrentLinkedQueue<BaseGlowUpdate> baseUpdates =
            new java.util.concurrent.ConcurrentLinkedQueue<>();

    private int verifyTotal;
    private int verifyLoaded;

    private CensusSession resumeDraft;
    private String draftName;
    private int draftLoaded;
    private int draftTotal;

    // Deferred confirmation flows (planning Q31 / Q37). The command channel and the screens share
    // the same state machine, so /census resume|discard keep working next to the screens.
    private String pendingStartName;
    private DraftStorage.NameConflict pendingConflict;
    private int draftPromptDelay;
    private boolean draftPromptShown;

    private final java.util.concurrent.ConcurrentLinkedQueue<ClientboundMerchantOffersPacket> offersQueue =
            new java.util.concurrent.ConcurrentLinkedQueue<>();

    private java.nio.file.Path resumeCandidate;

    public static CensusManager getInstance()
    {
        return INSTANCE;
    }

    private CensusManager()
    {
    }

    public CensusSession getSession()
    {
        return this.session;
    }

    public boolean hasSession()
    {
        return this.session != null && this.session.isActive();
    }

    public PendingUpdate getPendingUpdate()
    {
        return this.pendingUpdate;
    }

    public java.nio.file.Path getResumeCandidate()
    {
        return this.resumeCandidate;
    }

    public static String currentDimension()
    {
        Minecraft mc = Minecraft.getInstance();

        if (mc.level == null)
        {
            return "unknown";
        }

        try
        {
            return mc.level.dimension().identifier().toString();
        }
        catch (Exception e)
        {
            return "unknown";
        }
    }

    // ------------------------------------------------------------------
    // Session lifecycle
    // ------------------------------------------------------------------

    /**
     * Entry point for {@code /census start}. Starts right away when nothing collides with the
     * name; otherwise the collision is classified and offered on the confirmation screen (see
     * {@link GuiStartConflict}). With {@code startConflictPrompt} off the old behaviour applies:
     * start anyway and only warn about an existing draft.
     */
    public void requestStart(String rawName)
    {
        if (!this.validateStart(rawName))
        {
            return;
        }

        String name = rawName.trim();
        DraftStorage.NameConflict conflict = DraftStorage.findConflicts(
                WorldId.current(), currentDimension(), WorldId.safe(name));

        if (conflict.isNone() || !Configs.Generic.START_CONFLICT_PROMPT.getBooleanValue())
        {
            this.beginStart(name, conflict);
            return;
        }

        this.pendingStartName = name;
        this.pendingConflict = conflict;
        GuiBase.openGui(new GuiStartConflict(this));
    }

    private boolean validateStart(String rawName)
    {
        if (rawName == null || rawName.trim().isEmpty())
        {
            Messages.error( "villagercensus.message.name_required");
            return false;
        }

        if (this.hasSession())
        {
            Messages.error( "villagercensus.message.already_running");
            return false;
        }

        return true;
    }

    /**
     * Creates the session itself. A same-name draft is never silently overwritten: the caller has
     * already resolved the conflict (or the confirmation prompt is disabled).
     */
    private void beginStart(String rawName, DraftStorage.NameConflict conflict)
    {
        String safeName = WorldId.safe(rawName);
        String worldId = WorldId.current();
        String dimension = currentDimension();

        this.session = new CensusSession(rawName, safeName, worldId, dimension, System.currentTimeMillis());
        this.pending = null;
        this.pendingUpdate = null;
        this.clearPendingStart();

        if (conflict != null && conflict.kind == DraftStorage.ConflictKind.DRAFT)
        {
            Messages.warn( "villagercensus.message.draft_exists", safeName);
        }
        else
        {
            Messages.info( "villagercensus.message.started", rawName, dimension);
        }
    }

    public DraftStorage.NameConflict getPendingConflict()
    {
        return this.pendingConflict;
    }

    public String getPendingStartName()
    {
        return this.pendingStartName;
    }

    /**
     * Applies the choice made on the start-conflict screen.
     */
    public void resolveStartConflict(StartChoice choice, String newName)
    {
        DraftStorage.NameConflict conflict = this.pendingConflict;
        String name = this.pendingStartName;

        this.clearPendingStart();

        if (choice == StartChoice.CANCEL || conflict == null || name == null)
        {
            Messages.info( "villagercensus.message.conflict_cancelled");
            return;
        }

        switch (choice)
        {
            case CONTINUE_DRAFT -> this.adoptDraft(conflict.path, false);
            case FORCE_CONTINUE -> this.adoptDraft(conflict.path, true);
            case RESTART -> {
                if (conflict.path != null && !DraftStorage.delete(conflict.path))
                {
                    Messages.error( "villagercensus.message.conflict_action_failed");
                    return;
                }

                this.beginStart(name, DraftStorage.NameConflict.NONE);
            }
            case ARCHIVE_AND_START -> {
                if (conflict.path != null)
                {
                    DraftStorage.archive(conflict.path);
                }

                this.beginStart(name, DraftStorage.NameConflict.NONE);
            }
            case KEEP_NAME -> this.beginStart(name, DraftStorage.NameConflict.NONE);
            case RENAME -> {
                if (newName == null || newName.trim().isEmpty())
                {
                    Messages.error( "villagercensus.message.name_required");
                    return;
                }

                this.beginStart(newName.trim(), DraftStorage.NameConflict.NONE);
            }
        }
    }

    private void clearPendingStart()
    {
        this.pendingStartName = null;
        this.pendingConflict = null;
    }

    public enum StartChoice
    {
        CONTINUE_DRAFT("villagercensus.gui.button.conflict_continue"),
        FORCE_CONTINUE("villagercensus.gui.button.conflict_force_continue"),
        RESTART("villagercensus.gui.button.conflict_restart"),
        ARCHIVE_AND_START("villagercensus.gui.button.conflict_archive"),
        KEEP_NAME("villagercensus.gui.button.conflict_keep_name"),
        RENAME("villagercensus.gui.button.conflict_rename"),
        CANCEL("malilib.gui.button.cancel");

        private final String translationKey;

        StartChoice(String translationKey)
        {
            this.translationKey = translationKey;
        }

        public String getKey()
        {
            return this.translationKey;
        }
    }

    public java.nio.file.Path stopSession()
    {
        if (!this.hasSession())
        {
            Messages.error( "villagercensus.message.not_running");
            return null;
        }

        this.session.endTime = System.currentTimeMillis();
        java.nio.file.Path report = ReportWriter.writeReport(this.session);
        java.nio.file.Path draft = DraftStorage.findDraft(this.session.worldId, this.session.dimension, this.session.safeName);

        if (draft != null)
        {
            DraftStorage.delete(draft);
        }

        this.restoreAllMarkers();
        this.reverseMarkers = false;
        Messages.success( "villagercensus.message.stopped",
                this.session.rawName, this.session.totalCount(),
                report != null ? report.toAbsolutePath().toString() : "-");

        this.session = null;
        this.pending = null;
        this.pendingUpdate = null;
        return report;
    }

    public void abortSession()
    {
        if (!this.hasSession())
        {
            Messages.error( "villagercensus.message.not_running");
            return;
        }

        this.restoreAllMarkers();
        this.reverseMarkers = false;
        Messages.info( "villagercensus.message.aborted", this.session.rawName);
        this.session = null;
        this.pending = null;
        this.pendingUpdate = null;
    }

    public void togglePaused()
    {
        if (!this.hasSession())
        {
            Messages.error( "villagercensus.message.not_running");
            return;
        }

        this.cancelPendingUpdate();
        this.session.paused = !this.session.paused;
        Messages.info(
                this.session.paused ? "villagercensus.message.paused" : "villagercensus.message.resumed_stat");
    }

    public UndoEntry undo()
    {
        if (!this.hasSession())
        {
            Messages.error( "villagercensus.message.not_running");
            return null;
        }

        this.cancelPendingUpdate();
        UndoEntry entry = this.session.undo();

        if (entry == null)
        {
            Messages.error( "villagercensus.message.nothing_to_undo");
            return null;
        }

        Messages.info( "villagercensus.message.undone");
        return entry;
    }

    public boolean remark(String text)
    {
        if (!this.hasSession())
        {
            Messages.error( "villagercensus.message.not_running");
            return false;
        }

        this.cancelPendingUpdate();
        VillagerRecord record = this.session.getLastTarget();

        if (record == null)
        {
            Messages.error( "villagercensus.message.no_target");
            return false;
        }

        record.remark = text == null ? "" : text;
        Messages.info( "villagercensus.message.remark_set");
        return true;
    }

    public String status()
    {
        if (!this.hasSession())
        {
            return StringUtils.translate("villagercensus.message.not_running");
        }

        return StringUtils.translate("villagercensus.status.line",
                this.session.rawName, this.session.dimension,
                ReportWriter.formatTime(this.session.startTime),
                this.session.totalCount(), this.session.professionCounts().size(), this.session.babyCount(),
                yesNo(this.session.paused),
                yesNo(this.pending != null),
                yesNo(this.pendingUpdate != null));
    }

    private static String yesNo(boolean value)
    {
        return StringUtils.translate(value ? "villagercensus.status.yes" : "villagercensus.status.no");
    }


    // ------------------------------------------------------------------
    // Draft resume
    // ------------------------------------------------------------------

    public void refreshResumeCandidate()
    {
        this.resumeCandidate = DraftStorage.findAnyDraft(WorldId.current(), currentDimension());

        if (this.resumeCandidate != null)
        {
            this.resumeDraft = DraftStorage.load(this.resumeCandidate);
            this.draftName = this.resumeCandidate.getFileName().toString();
            this.draftTotal = this.resumeDraft != null ? this.resumeDraft.records.size() : 0;
            this.draftLoaded = this.resumeDraft != null ? this.updateVerification(this.resumeDraft) : 0;
        }
        else
        {
            this.resumeDraft = null;
            this.draftName = null;
            this.draftTotal = 0;
            this.draftLoaded = 0;
        }
    }

    public String getDraftName()
    {
        return this.draftName;
    }

    public int getDraftLoaded()
    {
        return this.draftLoaded;
    }

    public int getDraftTotal()
    {
        return this.draftTotal;
    }

    public boolean resumeSession()
    {
        if (this.hasSession())
        {
            Messages.error( "villagercensus.message.already_running");
            return false;
        }

        this.refreshResumeCandidate();

        if (this.resumeCandidate == null)
        {
            Messages.error( "villagercensus.message.no_draft");
            return false;
        }

        return this.adoptDraft(this.resumeCandidate, false);
    }

    /**
     * Loads a draft into a fresh in-memory session and starts re-verifying it. Records whose
     * villagers are not loaded keep their previous data and are marked "not verified this time";
     * they are never dropped. With {@code forceDimension} the draft is accepted even when it was
     * recorded in another dimension.
     */
    private boolean adoptDraft(java.nio.file.Path file, boolean forceDimension)
    {
        CensusSession loaded = DraftStorage.load(file);

        if (loaded == null)
        {
            DraftStorage.archive(file);
            this.clearResumeCandidate();
            Messages.error( "villagercensus.message.draft_corrupt");
            return false;
        }

        if (!forceDimension
                && !WorldId.safeDimension(currentDimension()).equals(WorldId.safeDimension(loaded.dimension)))
        {
            Messages.error( "villagercensus.message.dimension_mismatch");
            return false;
        }

        if (forceDimension)
        {
            Messages.warn( "villagercensus.message.dimension_override", loaded.dimension);
        }

        loaded.endTime = 0L;
        loaded.paused = false;
        loaded.worldId = WorldId.current();
        loaded.dimension = currentDimension();
        loaded.safeName = WorldId.safe(loaded.rawName != null && !loaded.rawName.isEmpty()
                ? loaded.rawName
                : file.getFileName().toString());
        this.session = loaded;
        this.pending = null;
        this.pendingUpdate = null;

        this.clearResumeCandidate();
        this.clearPendingStart();

        this.startVerification(loaded);
        Messages.success( "villagercensus.message.resumed",
                loaded.rawName, this.verifyLoaded, this.verifyTotal);
        return true;
    }

    public void discardDraft()
    {
        this.refreshResumeCandidate();

        if (this.resumeCandidate == null)
        {
            Messages.error( "villagercensus.message.no_draft");
            return;
        }

        DraftStorage.archive(this.resumeCandidate);
        this.clearResumeCandidate();
        Messages.info("villagercensus.message.draft_discarded");
    }

    /**
     * Closes the resume screen keeping the draft on disk, so it can be continued later.
     */
    public void deferDraft()
    {
        String name = this.draftName != null ? this.draftName : "";
        this.clearResumeCandidate();
        this.draftPromptShown = true;
        Messages.info( "villagercensus.message.draft_deferred", name);
    }

    private void clearResumeCandidate()
    {
        this.resumeCandidate = null;
        this.resumeDraft = null;
        this.draftName = null;
        this.draftTotal = 0;
        this.draftLoaded = 0;
    }

    // ------------------------------------------------------------------
    // Fork
    // ------------------------------------------------------------------

    /**
     * Creates a new active session from an existing draft or completed report whose recorded
     * session name matches, then starts counting immediately. A same-dimension source is
     * preferred; a cross-dimension match starts the fork anyway with a warning. The source is
     * left untouched.
     */
    public boolean forkSession(String rawName)
    {
        if (rawName == null || rawName.trim().isEmpty())
        {
            Messages.error( "villagercensus.message.fork_name_required");
            return false;
        }

        if (this.hasSession())
        {
            Messages.error( "villagercensus.message.already_running");
            return false;
        }

        if (Minecraft.getInstance().level == null)
        {
            Messages.error( "villagercensus.message.no_world");
            return false;
        }

        String sourceName = rawName.trim();
        String worldId = WorldId.current();
        String dimension = currentDimension();
        DraftStorage.ForkSource source = DraftStorage.findForkSource(worldId, dimension, sourceName);

        if (source == null)
        {
            Messages.error( "villagercensus.message.fork_not_found", sourceName);
            return false;
        }

        CensusSession base = source.session;
        String baseName = base.rawName != null && !base.rawName.isEmpty() ? base.rawName : sourceName;
        String display = baseName + " (fork)";
        CensusSession fork = new CensusSession(display, WorldId.safe(display), worldId, dimension, System.currentTimeMillis());

        for (VillagerRecord record : base.records)
        {
            VillagerRecord copy = record.copy();
            copy.order = ++fork.orderCounter;
            fork.records.add(copy);

            if (copy.uuid != null)
            {
                fork.byUuid.put(copy.uuid, copy);
            }
        }

        this.resolveMissingUuids(fork);

        this.session = fork;
        this.pending = null;
        this.pendingUpdate = null;
        this.offersQueue.clear();
        this.baseGlow.clear();
        this.managed.clear();
        this.baseUpdates.clear();
        this.trackingMarkers = false;
        this.reverseMarkers = false;
        this.clearResumeCandidate();
        this.clearPendingStart();
        this.verifyTotal = 0;
        this.verifyLoaded = 0;

        Messages.success( "villagercensus.message.fork_started", display, fork.totalCount(), dimension);

        if (source.crossDimension)
        {
            Messages.warn( "villagercensus.message.fork_cross_dimension", base.dimension);
        }

        return true;
    }

    private void startVerification(CensusSession loaded)
    {
        this.verifyTotal = loaded.records.size();
        this.verifyLoaded = 0;

        Set<UUID> loadedIds = this.loadedVillagerIds();

        for (VillagerRecord record : loaded.records)
        {
            if (record.uuid != null && loadedIds.contains(record.uuid))
            {
                record.status = record.hasTradeData
                        ? (record.weakAssociation ? DataStatus.WEAK : DataStatus.FULL)
                        : DataStatus.NO_TRADE_DATA;
                this.verifyLoaded++;
            }
            else
            {
                record.status = DataStatus.NOT_VERIFIED;
            }
        }
    }

    /**
     * TXT fork sources carry no UUIDs. Records that exactly match a single currently loaded
     * villager (position, profession and baby flag) recover its UUID so re-statting still
     * replaces the carried-over record instead of adding a duplicate.
     */
    private void resolveMissingUuids(CensusSession session)
    {
        Minecraft mc = Minecraft.getInstance();

        if (mc.level == null)
        {
            return;
        }

        List<Villager> loaded = new ArrayList<>();

        for (Entity entity : mc.level.entitiesForRendering())
        {
            if (entity instanceof Villager villager)
            {
                loaded.add(villager);
            }
        }

        for (VillagerRecord record : session.records)
        {
            if (record.uuid != null || !record.hasCoordinates)
            {
                continue;
            }

            Villager match = null;
            int matches = 0;

            for (Villager villager : loaded)
            {
                if (!villagerMatches(record, villager))
                {
                    continue;
                }

                match = villager;
                matches++;
            }

            if (matches == 1 && match != null)
            {
                record.uuid = match.getUUID();
                session.byUuid.put(record.uuid, record);
            }
        }
    }

    private static boolean villagerMatches(VillagerRecord record, Villager villager)
    {
        BlockPos pos = villager.blockPosition();

        if (pos.getX() != record.blockX || pos.getY() != record.blockY || pos.getZ() != record.blockZ)
        {
            return false;
        }

        if (villager.isBaby() != record.baby)
        {
            return false;
        }

        String profession = villager.getVillagerData().profession().unwrapKey()
                .map(key -> key.identifier().toString()).orElse("minecraft:none");
        return profession.equals(record.professionId);
    }

    private Set<UUID> loadedVillagerIds()
    {
        Set<UUID> ids = new HashSet<>();
        Minecraft mc = Minecraft.getInstance();

        if (mc.level != null)
        {
            for (Entity entity : mc.level.entitiesForRendering())
            {
                if (entity instanceof Villager)
                {
                    ids.add(entity.getUUID());
                }
            }
        }

        return ids;
    }

    private int updateVerification(CensusSession session)
    {
        Set<UUID> loadedIds = this.loadedVillagerIds();
        int loaded = 0;

        for (VillagerRecord record : session.records)
        {
            if (record.uuid != null && loadedIds.contains(record.uuid))
            {
                loaded++;

                if (record.status == DataStatus.NOT_VERIFIED)
                {
                    record.status = record.hasTradeData
                            ? (record.weakAssociation ? DataStatus.WEAK : DataStatus.FULL)
                            : DataStatus.NO_TRADE_DATA;
                }
            }
        }

        return loaded;
    }

    public int getVerifyLoaded()
    {
        return this.verifyLoaded;
    }

    public int getVerifyTotal()
    {
        return this.verifyTotal;
    }

    // ------------------------------------------------------------------
    // Interaction entry points (called from mixins)
    // ------------------------------------------------------------------

    public void onInteract(Entity entity, InteractionHand hand)
    {
        if (!this.hasSession() || this.session.paused)
        {
            return;
        }

        if (hand != InteractionHand.MAIN_HAND)
        {
            return;
        }

        Minecraft mc = Minecraft.getInstance();

        if (mc.player == null || !isTriggerItem(mc.player.getMainHandItem()))
        {
            return;
        }

        if (!(entity instanceof Villager villager))
        {
            return;
        }

        UUID uuid = villager.getUUID();

        if (this.pendingUpdate != null && this.pendingUpdate.uuid.equals(uuid))
        {
            this.confirmPendingUpdate(mc);
            return;
        }

        this.cancelPendingUpdate();

        if (this.pending != null)
        {
            Messages.warn( "villagercensus.message.wait_previous");
            return;
        }

        if (villager.isBaby())
        {
            VillagerRecord record = this.buildRecord(villager, null, 0, false, false);
            this.commitRecord(villager, record, this.session.getByUuid(uuid) != null);
            return;
        }

        boolean restat = this.session.getByUuid(uuid) != null;
        this.pending = new PendingTarget(villager.getId(), uuid, villager.getVillagerData().level(), this.tickCounter, restat);
    }

    /**
     * Called from the network thread by the mixin. Only queues the packet; all record building,
     * entity access and messaging happen later on the client thread (see {@link #onClientTick}).
     */
    public void onMerchantOffers(ClientboundMerchantOffersPacket packet)
    {
        if (this.hasSession())
        {
            this.offersQueue.add(packet);
        }
    }

    private void processMerchantOffers(ClientboundMerchantOffersPacket packet)
    {
        if (!this.hasSession() || this.pending == null)
        {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        Entity entity = mc.level != null ? mc.level.getEntity(this.pending.entityId) : null;

        if (!(entity instanceof Villager villager))
        {
            Messages.error( "villagercensus.message.failed");
            this.pending = null;
            return;
        }

        if (packet.getVillagerLevel() != villager.getVillagerData().level())
        {
            Messages.error( "villagercensus.message.level_mismatch");
            this.pending = null;
            return;
        }

        boolean weak = this.pending.containerId < 0;
        VillagerRecord record = this.buildRecord(villager, packet.getOffers(), packet.getVillagerXp(), true, weak);
        boolean restat = this.pending.restat;
        this.pending = null;
        this.commitRecord(villager, record, restat);
    }

    /**
     * @return true if the open-screen packet should be cancelled.
     */
    public boolean onOpenScreen(ClientboundOpenScreenPacket packet)
    {
        if (!this.hasSession() || this.pending == null)
        {
            return false;
        }

        if (packet.getType() != MenuType.MERCHANT)
        {
            return false;
        }

        boolean fresh = (this.tickCounter - this.pending.tick) <= (Configs.Generic.OFFERS_TIMEOUT.getIntegerValue() + 40L);

        if (fresh)
        {
            this.pending.containerId = packet.getContainerId();
        }
        else
        {
            this.pending = null;
        }

        return true;
    }

    private void warnMissingTradeData(VillagerRecord record)
    {
        if (record == null || record.baby || record.hasTradeData || record.professionId == null)
        {
            return;
        }

        if (record.professionId.equals("minecraft:none") || record.professionId.equals("minecraft:nitwit"))
        {
            return;
        }

        Messages.warn( "villagercensus.message.trade_data_failed", Names.profession(record.professionId));
    }

    private void commitRecord(Villager villager, VillagerRecord record, boolean restat)
    {
        if (restat)
        {
            VillagerRecord old = this.session.getByUuid(villager.getUUID());

            if (old != null && record.sameAs(old))
            {
                Messages.info( "villagercensus.message.unchanged");
                return;
            }

            this.pendingUpdate = new PendingUpdate(villager.getUUID(), villager.getId(), record);
            Messages.warn( "villagercensus.message.pending_update");

            if (old != null)
            {
                for (String line : record.diffLines(old))
                {
                    Messages.infoText(line);
                }
            }
        }
        else
        {
            this.session.addRecord(record);
            Messages.success( "villagercensus.message.recorded",
                    villager.getVillagerData().profession().unwrapKey()
                            .map(key -> key.identifier().toString()).orElse("minecraft:none"),
                    record.trades.size());
        }
    }

    private void confirmPendingUpdate(Minecraft mc)
    {
        PendingUpdate update = this.pendingUpdate;
        this.pendingUpdate = null;

        if (update == null)
        {
            return;
        }

        VillagerRecord old = this.session.getByUuid(update.uuid);

        if (old == null)
        {
            this.session.addRecord(update.record);
        }
        else
        {
            this.session.updateRecord(old, update.record.copy());
        }

        Messages.success( "villagercensus.message.updated");
    }

    private void cancelPendingUpdate()
    {
        if (this.pendingUpdate != null)
        {
            this.pendingUpdate = null;
            Messages.info( "villagercensus.message.pending_update_cancelled");
        }
    }

    // ------------------------------------------------------------------
    // Client tick
    // ------------------------------------------------------------------

    public void onClientTick(Minecraft mc)
    {
        this.tickCounter++;

        if (mc.level == null)
        {
            return;
        }

        // Network-thread packets are processed here, on the client thread.
        ClientboundMerchantOffersPacket queued;

        while ((queued = this.offersQueue.poll()) != null)
        {
            this.processMerchantOffers(queued);
        }

        this.drainBaseUpdates(mc);

        if (this.hasSession() && this.pending != null)
        {
            long timeout = Configs.Generic.OFFERS_TIMEOUT.getIntegerValue();

            if (this.tickCounter - this.pending.tick > timeout)
            {
                Entity entity = mc.level.getEntity(this.pending.entityId);
                boolean restat = this.pending.restat;
                this.pending = null;

                if (entity instanceof Villager villager)
                {
                    VillagerRecord record = this.buildRecord(villager, null, 0, false, false);

                    if (restat)
                    {
                        VillagerRecord old = this.session.getByUuid(villager.getUUID());

                        if (old != null)
                        {
                            // No offers arrived: keep the previously known trade data for the comparison.
                            record.hasTraded = old.hasTraded;
                            record.hasTradeData = old.hasTradeData;
                            record.trades = new ArrayList<>(old.trades);
                            record.status = old.status;
                        }
                    }

                    this.warnMissingTradeData(record);
                    this.commitRecord(villager, record, restat);
                }
            }
        }

        if (this.hasSession())
        {
            this.maintainMarkers(mc);

            if (this.verifyTotal > 0 && this.tickCounter % VERIFY_REFRESH_TICKS == 0L)
            {
                this.verifyLoaded = this.updateVerification(this.session);

                if (this.verifyLoaded >= this.verifyTotal)
                {
                    this.verifyTotal = 0;
                    Messages.info("villagercensus.message.verified_all", this.verifyLoaded);
                }
            }
        }

        if (!this.hasSession() && this.resumeDraft != null && this.tickCounter % VERIFY_REFRESH_TICKS == 0L)
        {
            this.draftLoaded = this.updateVerification(this.resumeDraft);
        }

        this.openDraftPromptWhenReady(mc);
        this.autoSaveDraft();
    }

    /**
     * Opens the resume screen shortly after joining a world that still holds a draft (Q31). The
     * delay keeps the screen from fighting the loading screen, and nothing is opened while another
     * screen is up.
     */
    private void openDraftPromptWhenReady(Minecraft mc)
    {
        if (this.draftPromptDelay <= 0 || this.hasSession() || this.resumeCandidate == null)
        {
            return;
        }

        if (--this.draftPromptDelay > 0)
        {
            return;
        }

        if (Compat.currentScreen(mc) != null)
        {
            this.draftPromptDelay = 20;
            return;
        }

        this.draftPromptShown = true;
        GuiBase.openGui(new GuiDraftResume(this));
    }

    private void autoSaveDraft()
    {
        int interval = Configs.Generic.AUTO_SAVE_INTERVAL.getIntegerValue();

        if (!this.hasSession() || this.session.paused || interval <= 0)
        {
            this.lastAutoSaveTick = this.tickCounter;
            return;
        }

        if (this.tickCounter - this.lastAutoSaveTick < interval * 20L)
        {
            return;
        }

        this.lastAutoSaveTick = this.tickCounter;

        try
        {
            DraftStorage.save(this.session);
        }
        catch (Exception e)
        {
            Reference.logger().warn("Automatic draft save failed", e);
        }
    }

    // ------------------------------------------------------------------
    // Record building
    // ------------------------------------------------------------------

    private VillagerRecord buildRecord(Villager villager, MerchantOffers offers, int villagerXp,
                                       boolean hasTradeData, boolean weak)
    {
        VillagerRecord record = new VillagerRecord();
        record.uuid = villager.getUUID();
        Holder<VillagerProfession> profession = villager.getVillagerData().profession();
        record.professionId = profession.unwrapKey().map(key -> key.identifier().toString()).orElse("minecraft:none");
        record.baby = villager.isBaby();
        record.level = villager.getVillagerData().level();
        record.hasTraded = villagerXp > 0;
        record.health = villager.getHealth();
        record.maxHealth = villager.getMaxHealth();

        if (Configs.Generic.RECORD_COORDINATES.getBooleanValue())
        {
            BlockPos pos = villager.blockPosition();
            record.blockX = pos.getX();
            record.blockY = pos.getY();
            record.blockZ = pos.getZ();
            record.hasCoordinates = true;
        }
        else
        {
            record.hasCoordinates = false;
        }

        record.dimension = currentDimension();
        record.customName = villager.getCustomName() != null ? villager.getCustomName().getString() : null;
        record.hasTradeData = hasTradeData && offers != null;
        record.weakAssociation = weak;
        record.status = hasTradeData ? (weak ? DataStatus.WEAK : DataStatus.FULL) : DataStatus.NO_TRADE_DATA;

        if (offers != null)
        {
            record.trades = this.buildTrades(record.professionId, offers);
        }

        return record;
    }

    private List<TradeEntry> buildTrades(String professionId, MerchantOffers offers)
    {
        List<TradeEntry> list = new ArrayList<>();
        boolean recordAll = Configs.Generic.RECORD_ALL_TRADES.getBooleanValue();
        List<String> selected = Configs.Generic.SELECTED_CATEGORIES.getStrings();

        for (MerchantOffer offer : offers)
        {
            ItemStack cost1 = offer.getBaseCostA();
            ItemStack cost2 = offer.getCostB();
            ItemStack result = offer.getResult();

            String c1 = itemId(cost1);
            String c2 = cost2.isEmpty() ? "" : itemId(cost2);
            String res = itemId(result);
            boolean resultEnchanted = result.is(Items.ENCHANTED_BOOK) || EnchantmentHelper.hasAnyEnchantments(result);

            TradeCategory category = TradeCatalog.get().match(professionId, c1, c2, res, resultEnchanted);
            String categoryId = category != null ? category.id : ("triple:" + c1 + ">" + res);

            if (!recordAll)
            {
                // Categories are keyed as "profession/id" by the selection GUI; bare ids and
                // replaced variant ids (aliases) from older configs keep working as well.
                boolean included = category != null
                        ? category.selectedBy(selected)
                        : selected.contains(categoryId);

                if (!included)
                {
                    continue;
                }
            }

            list.add(new TradeEntry(categoryId, c1, cost1.getCount(), c2, cost2.getCount(),
                    res, result.getCount(), result.is(Items.ENCHANTED_BOOK), collectEnchantments(result)));
        }

        return list;
    }

    private static List<EnchantEntry> collectEnchantments(ItemStack stack)
    {
        List<EnchantEntry> list = new ArrayList<>();

        try
        {
            if (!EnchantmentHelper.hasAnyEnchantments(stack))
            {
                return list;
            }

            for (Map.Entry<Holder<Enchantment>, Integer> entry : EnchantmentHelper.getEnchantmentsForCrafting(stack).entrySet())
            {
                Holder<Enchantment> holder = entry.getKey();
                String id = holder.unwrapKey().map(key -> key.identifier().toString()).orElse("?");

                // Direct call: Enchantment.getMaxLevel() exists in both target versions. A
                // reflective lookup by string name breaks in production because remapping does
                // not rewrite reflection names.
                list.add(new EnchantEntry(id, entry.getValue(), holder.value().getMaxLevel()));
            }
        }
        catch (Exception e)
        {
            Reference.logger().debug("Failed to read enchantments", e);
        }

        return list;
    }

    private static String itemId(ItemStack stack)
    {
        if (stack.isEmpty())
        {
            return "";
        }

        try
        {
            return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        }
        catch (Exception e)
        {
            return "?";
        }
    }

    public static boolean isTriggerItem(ItemStack stack)
    {
        if (stack.isEmpty())
        {
            return false;
        }

        if (matchesItem(stack, Configs.Generic.TRIGGER_ITEM.getStringValue()))
        {
            return true;
        }

        for (String entry : Configs.Generic.TRIGGER_ITEMS.getStrings())
        {
            if (matchesItem(stack, entry))
            {
                return true;
            }
        }

        return matchesTag(stack, Configs.Generic.TRIGGER_ITEM_TAG.getStringValue());
    }

    private static boolean matchesItem(ItemStack stack, String rawId)
    {
        if (rawId == null || rawId.trim().isEmpty())
        {
            return false;
        }

        Identifier id = Identifier.tryParse(rawId.trim());

        if (id == null)
        {
            return false;
        }

        Item item = BuiltInRegistries.ITEM.get(id).map(ref -> ref.value()).orElse(Items.AIR);
        return item != Items.AIR && stack.is(item);
    }

    private static boolean matchesTag(ItemStack stack, String rawTag)
    {
        if (rawTag == null || rawTag.trim().isEmpty())
        {
            return false;
        }

        String value = rawTag.trim();

        if (value.startsWith("#"))
        {
            value = value.substring(1);
        }

        try
        {
            Identifier id = Identifier.tryParse(value);

            if (id == null)
            {
                return false;
            }

            return stack.is(TagKey.create(Registries.ITEM, id));
        }
        catch (Exception e)
        {
            Reference.logger().warn("Invalid trigger item tag '{}'", rawTag);
            return false;
        }
    }

    private static Entity findEntity(ClientLevel level, UUID uuid)
    {
        if (level == null || uuid == null)
        {
            return null;
        }

        for (Entity entity : level.entitiesForRendering())
        {
            if (uuid.equals(entity.getUUID()))
            {
                return entity;
            }
        }

        return null;
    }

    // ------------------------------------------------------------------
    // Glowing markers
    // ------------------------------------------------------------------

    // Entity.DATA_SHARED_FLAGS_ID is the first synced value defined on Entity, so its data id is
    // always 0. The glowing bit is flag 6 within that byte.
    private static final int SHARED_FLAGS_DATA_ID = 0;
    private static final int GLOWING_FLAG_BIT = 6;

    /**
     * Called from the network thread by the mixin for entity-data updates. The server sends the
     * full shared-flags byte whenever any of its bits change, so the glowing bit in it is the
     * authoritative glow state from all non-census sources. It is queued and applied on the client
     * thread in {@link #drainBaseUpdates(Minecraft)}.
     */
    public void onSetEntityData(ClientboundSetEntityDataPacket packet)
    {
        if (!this.trackingMarkers)
        {
            return;
        }

        for (SynchedEntityData.DataValue<?> item : packet.packedItems())
        {
            if (item.id() == SHARED_FLAGS_DATA_ID && item.value() instanceof Byte value)
            {
                this.baseUpdates.add(new BaseGlowUpdate(packet.id(), (value & (1 << GLOWING_FLAG_BIT)) != 0));
                return;
            }
        }
    }

    private void drainBaseUpdates(Minecraft mc)
    {
        BaseGlowUpdate update;

        while ((update = this.baseUpdates.poll()) != null)
        {
            if (mc.level == null)
            {
                continue;
            }

            Entity entity = mc.level.getEntity(update.entityId);

            if (entity != null && this.baseGlow.containsKey(entity.getUUID()))
            {
                this.baseGlow.put(entity.getUUID(), update.glowing);
            }
        }
    }

    public boolean isReverseMarkers()
    {
        return this.reverseMarkers;
    }

    /**
     * Whether glowing markers should currently be applied. With {@code markWhenNotHolding} off the
     * markers are suppressed while the trigger item is not in the main hand.
     */
    public boolean markersVisible(Minecraft mc)
    {
        if (Configs.Generic.MARK_WHEN_NOT_HOLDING.getBooleanValue())
        {
            return true;
        }

        return mc != null && mc.player != null && isTriggerItem(mc.player.getMainHandItem());
    }

    public boolean isWaitingForOffers()
    {
        return this.hasSession() && !this.session.paused && this.pending != null;
    }

    public int getWaitingTicks()
    {
        return this.pending == null ? 0 : (int) (this.tickCounter - this.pending.tick);
    }

    /**
     * True when the pick ray should skip this entity because it is a villager this session already
     * counted. Requires the pass-through option, a running session and the trigger item in hand.
     */
    public boolean isPassThroughTarget(Entity entity)
    {
        if (!Configs.Generic.PASS_THROUGH.getBooleanValue()
                || !this.hasSession()
                || this.session.paused
                || !(entity instanceof Villager))
        {
            return false;
        }

        Minecraft mc = Minecraft.getInstance();

        if (mc.player == null || !isTriggerItem(mc.player.getMainHandItem()))
        {
            return false;
        }

        return this.session.getByUuid(entity.getUUID()) != null;
    }

    public boolean isDraftPromptPending()
    {
        return this.draftPromptDelay > 0 || (this.draftPromptShown && this.resumeCandidate != null);
    }

    public void toggleReverseMarkers()
    {
        if (!this.hasSession())
        {
            Messages.error( "villagercensus.message.not_running");
            return;
        }

        if (!Configs.Generic.GLOWING_MARKER.getBooleanValue())
        {
            Messages.error( "villagercensus.message.glowing_disabled");
            return;
        }

        this.reverseMarkers = !this.reverseMarkers;
        Messages.info(this.reverseMarkers
                ? "villagercensus.message.reverse_on"
                : "villagercensus.message.reverse_off");
    }

    private void maintainMarkers(Minecraft mc)
    {
        if (mc.level == null || !this.hasSession())
        {
            return;
        }

        if (!Configs.Generic.GLOWING_MARKER.getBooleanValue() || !this.markersVisible(mc))
        {
            if (!this.managed.isEmpty())
            {
                this.restoreAllMarkers();
            }

            return;
        }

        Map<UUID, Villager> loaded = new java.util.HashMap<>();

        for (Entity entity : mc.level.entitiesForRendering())
        {
            if (entity instanceof Villager villager)
            {
                loaded.put(entity.getUUID(), villager);
            }
        }

        boolean reverse = this.reverseMarkers;
        Set<UUID> targets = new HashSet<>();

        if (reverse)
        {
            // In reverse mode every loaded villager is managed so that the uncounted ones glow.
            targets.addAll(loaded.keySet());
        }
        else
        {
            for (VillagerRecord record : this.session.records)
            {
                if (record.uuid != null && loaded.containsKey(record.uuid))
                {
                    targets.add(record.uuid);
                }
            }
        }

        this.trackingMarkers = true;

        for (UUID uuid : targets)
        {
            Villager villager = loaded.get(uuid);
            boolean recorded = this.session.getByUuid(uuid) != null;
            boolean censusGlow = reverse ? !recorded : recorded;
            boolean base = this.baseGlow.computeIfAbsent(uuid, key -> villager.isCurrentlyGlowing());
            setGlow(villager, base || censusGlow);
            this.managed.add(uuid);
        }

        // Release villagers we no longer control, restoring their original (non-census) glow.
        for (UUID uuid : new ArrayList<>(this.managed))
        {
            if (!targets.contains(uuid))
            {
                Villager villager = loaded.get(uuid);

                if (villager != null)
                {
                    setGlow(villager, this.baseGlow.getOrDefault(uuid, false));
                }

                this.managed.remove(uuid);
                this.baseGlow.remove(uuid);
            }
        }

        this.trackingMarkers = !this.managed.isEmpty();
    }

    private void restoreAllMarkers()
    {
        ClientLevel level = Minecraft.getInstance().level;

        for (UUID uuid : new ArrayList<>(this.managed))
        {
            if (level != null)
            {
                Entity entity = findEntity(level, uuid);

                if (entity instanceof Villager villager)
                {
                    setGlow(villager, this.baseGlow.getOrDefault(uuid, false));
                }
            }
        }

        this.managed.clear();
        this.baseGlow.clear();
        this.baseUpdates.clear();
        this.trackingMarkers = false;
    }

    private static void setGlow(Villager villager, boolean value)
    {
        // Client-side Entity#setGlowingTag only round-trips the shared flag, so set it directly.
        ((IMixinEntity) villager).villagercensus$setSharedFlag(GLOWING_FLAG_BIT, value);
    }

    private static class BaseGlowUpdate
    {
        final int entityId;
        final boolean glowing;

        BaseGlowUpdate(int entityId, boolean glowing)
        {
            this.entityId = entityId;
            this.glowing = glowing;
        }
    }

    // ------------------------------------------------------------------
    // World load / unload
    // ------------------------------------------------------------------

    public void onWorldLoadPre(ClientLevel worldBefore, ClientLevel worldAfter)
    {
        if (worldBefore != null && worldAfter == null)
        {
            if (this.hasSession() && Configs.Generic.AUTO_SAVE_DRAFT.getBooleanValue())
            {
                DraftStorage.save(this.session);
            }

            // The session must not silently continue into the next world; the saved draft is
            // resumed explicitly with /census resume.
            this.session = null;
            this.pending = null;
            this.pendingUpdate = null;
            this.offersQueue.clear();
            this.baseGlow.clear();
            this.managed.clear();
            this.baseUpdates.clear();
            this.trackingMarkers = false;
            this.reverseMarkers = false;
            this.verifyTotal = 0;
            this.verifyLoaded = 0;
            this.clearResumeCandidate();
            this.clearPendingStart();
            this.draftPromptDelay = 0;
            this.draftPromptShown = false;
        }
    }

    public void onWorldLoadPost(ClientLevel worldBefore, ClientLevel worldAfter)
    {
        if (worldBefore == null && worldAfter != null)
        {
            this.refreshResumeCandidate();

            if (this.resumeCandidate != null)
            {
                if (Configs.Generic.RESUME_PROMPT.getBooleanValue())
                {
                    // Opened from the client tick so the loading screen is already gone.
                    this.draftPromptDelay = DRAFT_PROMPT_DELAY_TICKS;
                }
                else
                {
                    Messages.info( "villagercensus.message.draft_found",
                            this.resumeCandidate.getFileName().toString(),
                            this.draftLoaded, this.draftTotal);
                }
            }
        }
    }

    public int getTickCounter()
    {
        return (int) this.tickCounter;
    }

    private static class PendingTarget
    {
        final int entityId;
        final UUID uuid;
        final int level;
        final long tick;
        final boolean restat;
        volatile int containerId = -1;

        PendingTarget(int entityId, UUID uuid, int level, long tick, boolean restat)
        {
            this.entityId = entityId;
            this.uuid = uuid;
            this.level = level;
            this.tick = tick;
            this.restat = restat;
        }
    }

    public static class PendingUpdate
    {
        public final UUID uuid;
        public final int entityId;
        public final VillagerRecord record;

        PendingUpdate(UUID uuid, int entityId, VillagerRecord record)
        {
            this.uuid = uuid;
            this.entityId = entityId;
            this.record = record;
        }
    }
}

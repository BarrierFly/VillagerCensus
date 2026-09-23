package net.villagercensus.census;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import fi.dy.masa.malilib.gui.Message.MessageType;
import fi.dy.masa.malilib.util.InfoUtils;
import fi.dy.masa.malilib.util.StringUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundMerchantOffersPacket;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
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
import net.villagercensus.trade.TradeCatalog;
import net.villagercensus.trade.TradeCategory;

public class CensusManager
{
    private static final CensusManager INSTANCE = new CensusManager();

    private volatile CensusSession session;
    private volatile PendingTarget pending;
    private PendingUpdate pendingUpdate;
    private volatile long tickCounter;

    private final Map<UUID, Boolean> originalGlow = new LinkedHashMap<>();
    private final Set<UUID> modGlowing = new HashSet<>();

    private int verifyTotal;
    private int verifyLoaded;

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

    // ------------------------------------------------------------------
    // Session lifecycle
    // ------------------------------------------------------------------

    public boolean startSession(String rawName)
    {
        if (rawName == null || rawName.trim().isEmpty())
        {
            InfoUtils.showGuiOrInGameMessage(MessageType.ERROR, "villagercensus.message.name_required");
            return false;
        }

        if (this.hasSession())
        {
            InfoUtils.showGuiOrInGameMessage(MessageType.ERROR, "villagercensus.message.already_running");
            return false;
        }

        String safeName = WorldId.safe(rawName.trim());
        String worldId = WorldId.current();
        String dimension = currentDimension();
        java.nio.file.Path draft = DraftStorage.findDraft(worldId, dimension, safeName);

        this.session = new CensusSession(rawName.trim(), safeName, worldId, dimension, System.currentTimeMillis());
        this.pending = null;
        this.pendingUpdate = null;

        if (draft != null)
        {
            InfoUtils.showGuiOrInGameMessage(MessageType.WARNING, "villagercensus.message.draft_exists", safeName);
        }
        else
        {
            InfoUtils.showGuiOrInGameMessage(MessageType.INFO, "villagercensus.message.started", rawName.trim(), dimension);
        }

        return true;
    }

    public java.nio.file.Path stopSession()
    {
        if (!this.hasSession())
        {
            InfoUtils.showGuiOrInGameMessage(MessageType.ERROR, "villagercensus.message.not_running");
            return null;
        }

        this.session.endTime = System.currentTimeMillis();
        java.nio.file.Path report = ReportWriter.writeReport(this.session, Configs.Generic.OUTPUT_JSON.getBooleanValue());
        java.nio.file.Path draft = DraftStorage.findDraft(this.session.worldId, this.session.dimension, this.session.safeName);

        if (draft != null)
        {
            DraftStorage.delete(draft);
        }

        this.removeAllMarkers();
        InfoUtils.showGuiOrInGameMessage(MessageType.SUCCESS, "villagercensus.message.stopped",
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
            InfoUtils.showGuiOrInGameMessage(MessageType.ERROR, "villagercensus.message.not_running");
            return;
        }

        this.removeAllMarkers();
        InfoUtils.showGuiOrInGameMessage(MessageType.INFO, "villagercensus.message.aborted", this.session.rawName);
        this.session = null;
        this.pending = null;
        this.pendingUpdate = null;
    }

    public void togglePaused()
    {
        if (!this.hasSession())
        {
            InfoUtils.showGuiOrInGameMessage(MessageType.ERROR, "villagercensus.message.not_running");
            return;
        }

        this.cancelPendingUpdate();
        this.session.paused = !this.session.paused;
        InfoUtils.showGuiOrInGameMessage(MessageType.INFO,
                this.session.paused ? "villagercensus.message.paused" : "villagercensus.message.resumed_stat");
    }

    public UndoEntry undo()
    {
        if (!this.hasSession())
        {
            InfoUtils.showGuiOrInGameMessage(MessageType.ERROR, "villagercensus.message.not_running");
            return null;
        }

        this.cancelPendingUpdate();
        UndoEntry entry = this.session.undo();

        if (entry == null)
        {
            InfoUtils.showGuiOrInGameMessage(MessageType.ERROR, "villagercensus.message.nothing_to_undo");
            return null;
        }

        if (entry.wasNew)
        {
            this.removeMarker(entry.uuid);
        }

        InfoUtils.showGuiOrInGameMessage(MessageType.INFO, "villagercensus.message.undone");
        return entry;
    }

    public boolean remark(String text)
    {
        if (!this.hasSession())
        {
            InfoUtils.showGuiOrInGameMessage(MessageType.ERROR, "villagercensus.message.not_running");
            return false;
        }

        this.cancelPendingUpdate();
        VillagerRecord record = this.session.getLastTarget();

        if (record == null)
        {
            InfoUtils.showGuiOrInGameMessage(MessageType.ERROR, "villagercensus.message.no_target");
            return false;
        }

        record.remark = text == null ? "" : text;
        InfoUtils.showGuiOrInGameMessage(MessageType.INFO, "villagercensus.message.remark_set");
        return true;
    }

    public String status()
    {
        if (!this.hasSession())
        {
            return StringUtils.translate("villagercensus.message.not_running");
        }

        return this.session.rawName + " @ " + this.session.dimension
                + " | count=" + this.session.totalCount()
                + " | professions=" + this.session.professionCounts().size()
                + " | babies=" + this.session.babyCount()
                + " | paused=" + this.session.paused
                + " | pending=" + (this.pending != null)
                + " | pendingUpdate=" + (this.pendingUpdate != null);
    }

    // ------------------------------------------------------------------
    // Draft resume
    // ------------------------------------------------------------------

    public void refreshResumeCandidate()
    {
        this.resumeCandidate = DraftStorage.findAnyDraft(WorldId.current(), currentDimension());
    }

    public boolean resumeSession()
    {
        if (this.hasSession())
        {
            InfoUtils.showGuiOrInGameMessage(MessageType.ERROR, "villagercensus.message.already_running");
            return false;
        }

        this.refreshResumeCandidate();

        if (this.resumeCandidate == null)
        {
            InfoUtils.showGuiOrInGameMessage(MessageType.ERROR, "villagercensus.message.no_draft");
            return false;
        }

        CensusSession loaded = DraftStorage.load(this.resumeCandidate);

        if (loaded == null)
        {
            DraftStorage.archive(this.resumeCandidate);
            this.resumeCandidate = null;
            InfoUtils.showGuiOrInGameMessage(MessageType.ERROR, "villagercensus.message.draft_corrupt");
            return false;
        }

        if (!WorldId.safeDimension(currentDimension()).equals(WorldId.safeDimension(loaded.dimension)))
        {
            InfoUtils.showGuiOrInGameMessage(MessageType.ERROR, "villagercensus.message.dimension_mismatch");
            return false;
        }

        loaded.endTime = 0L;
        loaded.paused = false;
        loaded.worldId = WorldId.current();
        this.session = loaded;
        this.pending = null;
        this.pendingUpdate = null;

        this.startVerification(loaded);
        InfoUtils.showGuiOrInGameMessage(MessageType.SUCCESS, "villagercensus.message.resumed",
                loaded.rawName, this.verifyLoaded, this.verifyTotal);
        return true;
    }

    public void discardDraft()
    {
        this.refreshResumeCandidate();

        if (this.resumeCandidate == null)
        {
            InfoUtils.showGuiOrInGameMessage(MessageType.ERROR, "villagercensus.message.no_draft");
            return;
        }

        DraftStorage.archive(this.resumeCandidate);
        this.resumeCandidate = null;
        InfoUtils.showGuiOrInGameMessage(MessageType.INFO, "villagercensus.message.draft_discarded");
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
            InfoUtils.showGuiOrInGameMessage(MessageType.WARNING, "villagercensus.message.wait_previous");
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
            InfoUtils.showGuiOrInGameMessage(MessageType.ERROR, "villagercensus.message.failed");
            this.pending = null;
            return;
        }

        if (packet.getVillagerLevel() != villager.getVillagerData().level())
        {
            InfoUtils.showGuiOrInGameMessage(MessageType.ERROR, "villagercensus.message.level_mismatch");
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

    private void commitRecord(Villager villager, VillagerRecord record, boolean restat)
    {
        if (restat)
        {
            VillagerRecord old = this.session.getByUuid(villager.getUUID());

            if (old != null && record.sameAs(old))
            {
                InfoUtils.showGuiOrInGameMessage(MessageType.INFO, "villagercensus.message.unchanged");
                return;
            }

            this.pendingUpdate = new PendingUpdate(villager.getUUID(), villager.getId(), record);
            InfoUtils.showGuiOrInGameMessage(MessageType.WARNING, "villagercensus.message.pending_update");

            if (old != null)
            {
                for (String line : record.diffLines(old))
                {
                    InfoUtils.showGuiOrInGameMessage(MessageType.INFO, line);
                }
            }
        }
        else
        {
            this.session.addRecord(record);
            this.applyMarker(villager);
            InfoUtils.showGuiOrInGameMessage(MessageType.SUCCESS, "villagercensus.message.recorded",
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

        this.applyMarker(mc.level != null ? mc.level.getEntity(update.entityId) : null);
        InfoUtils.showGuiOrInGameMessage(MessageType.SUCCESS, "villagercensus.message.updated");
    }

    private void cancelPendingUpdate()
    {
        if (this.pendingUpdate != null)
        {
            this.pendingUpdate = null;
            InfoUtils.showGuiOrInGameMessage(MessageType.INFO, "villagercensus.message.pending_update_cancelled");
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

                    this.commitRecord(villager, record, restat);
                }
            }
        }

        if (this.hasSession())
        {
            this.maintainMarkers(mc);

            if (this.verifyTotal > 0 && this.tickCounter % 20L == 0L)
            {
                this.verifyLoaded = this.updateVerification(this.session);

                if (this.verifyLoaded >= this.verifyTotal)
                {
                    this.verifyTotal = 0;
                    InfoUtils.showGuiOrInGameMessage(MessageType.INFO, "villagercensus.message.verified_all",
                            this.verifyLoaded);
                }
            }
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
        BlockPos pos = villager.blockPosition();
        record.blockX = pos.getX();
        record.blockY = pos.getY();
        record.blockZ = pos.getZ();
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

            TradeCategory category = TradeCatalog.get().match(professionId, c1, c2, res);
            String categoryId = category != null ? category.id : ("triple:" + c1 + ">" + res);

            if (!recordAll)
            {
                boolean included = category != null
                        ? selected.contains(category.id)
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
                list.add(new EnchantEntry(id, entry.getValue(), resolveMaxLevel(holder.value())));
            }
        }
        catch (Exception e)
        {
            Reference.logger().debug("Failed to read enchantments", e);
        }

        return list;
    }

    /**
     * Reflective max-level lookup so the code compiles across mappings where the accessor
     * moved between {@code Enchantment} and {@code Enchantment.EnchantmentDefinition}.
     */
    private static int resolveMaxLevel(Enchantment enchantment)
    {
        for (String method : new String[] { "getMaxLevel", "maxLevel" })
        {
            try
            {
                Object value = enchantment.getClass().getMethod(method).invoke(enchantment);

                if (value instanceof Integer level)
                {
                    return level;
                }
            }
            catch (Throwable ignored)
            {
            }
        }

        try
        {
            Object definition = enchantment.getClass().getMethod("definition").invoke(enchantment);
            Object value = definition.getClass().getMethod("maxLevel").invoke(definition);

            if (value instanceof Integer level)
            {
                return level;
            }
        }
        catch (Throwable ignored)
        {
        }

        return -1;
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

        Identifier id = Identifier.tryParse(Configs.Generic.TRIGGER_ITEM.getStringValue().trim());

        if (id == null)
        {
            return false;
        }

        Item item = BuiltInRegistries.ITEM.get(id).map(ref -> ref.value()).orElse(Items.AIR);
        return item != Items.AIR && stack.is(item);
    }

    private static String currentDimension()
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

    private void applyMarker(Entity entity)
    {
        if (!Configs.Generic.GLOWING_MARKER.getBooleanValue() || !(entity instanceof Villager villager))
        {
            return;
        }

        UUID uuid = villager.getUUID();
        this.originalGlow.putIfAbsent(uuid, villager.hasGlowingTag());
        villager.setGlowingTag(true);
        this.modGlowing.add(uuid);
    }

    private void removeMarker(UUID uuid)
    {
        if (uuid == null)
        {
            return;
        }

        Entity entity = findEntity(Minecraft.getInstance().level, uuid);

        if (entity instanceof Villager villager && this.modGlowing.contains(uuid))
        {
            boolean original = this.originalGlow.getOrDefault(uuid, false);

            if (!original)
            {
                villager.setGlowingTag(false);
            }
        }

        this.modGlowing.remove(uuid);
        this.originalGlow.remove(uuid);
    }

    private void removeAllMarkers()
    {
        for (UUID uuid : new ArrayList<>(this.modGlowing))
        {
            this.removeMarker(uuid);
        }

        this.modGlowing.clear();
        this.originalGlow.clear();
    }

    private void maintainMarkers(Minecraft mc)
    {
        if (!Configs.Generic.GLOWING_MARKER.getBooleanValue() || mc.level == null || !this.hasSession())
        {
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

        for (VillagerRecord record : this.session.records)
        {
            if (record.uuid == null)
            {
                continue;
            }

            Villager villager = loaded.get(record.uuid);

            if (villager != null)
            {
                this.originalGlow.putIfAbsent(record.uuid, villager.hasGlowingTag());

                if (!villager.hasGlowingTag())
                {
                    villager.setGlowingTag(true);
                }

                this.modGlowing.add(record.uuid);
            }
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
            this.originalGlow.clear();
            this.modGlowing.clear();
            this.verifyTotal = 0;
            this.verifyLoaded = 0;
        }
    }

    public void onWorldLoadPost(ClientLevel worldBefore, ClientLevel worldAfter)
    {
        if (worldBefore == null && worldAfter != null)
        {
            this.refreshResumeCandidate();

            if (this.resumeCandidate != null)
            {
                CensusSession draft = DraftStorage.load(this.resumeCandidate);
                int total = draft != null ? draft.records.size() : 0;
                int loaded = draft != null ? this.updateVerification(draft) : 0;
                InfoUtils.showGuiOrInGameMessage(MessageType.INFO, "villagercensus.message.draft_found",
                        this.resumeCandidate.getFileName().toString(), loaded, total);
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

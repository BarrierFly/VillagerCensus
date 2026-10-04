package net.villagercensus.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundMerchantOffersPacket;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.villagercensus.census.CensusManager;
import net.villagercensus.compat.offershud.OffersHudCompat;

// Priority 900 puts these hooks ahead of other mods' hooks (offers-hud uses the default 1000), so
// the container id is recorded and the offers are captured before anyone may cancel a packet.
// Mixin 0.8.5 has no per-injector priority attribute, so the whole mixin class carries it.
@Mixin(value = ClientPacketListener.class, priority = 900)
public abstract class MixinClientPacketListener
{
    // Reading the offers packet never cancels it, so offers-hud's preview keeps working.
    @Inject(method = "handleMerchantOffers", at = @At("HEAD"))
    private void villagercensus$onMerchantOffers(ClientboundMerchantOffersPacket packet, CallbackInfo ci)
    {
        CensusManager.getInstance().onMerchantOffers(packet);
    }

    @Inject(method = "handleSetEntityData", at = @At("HEAD"))
    private void villagercensus$onSetEntityData(ClientboundSetEntityDataPacket packet, CallbackInfo ci)
    {
        CensusManager.getInstance().onSetEntityData(packet);
    }

    // The container is closed by exactly one mod; see OffersHudCompat.
    @Inject(method = "handleOpenScreen", at = @At("HEAD"), cancellable = true)
    private void villagercensus$onOpenScreen(ClientboundOpenScreenPacket packet, CallbackInfo ci)
    {
        if (CensusManager.getInstance().onOpenScreen(packet))
        {
            ci.cancel();

            if (OffersHudCompat.willSendContainerClose())
            {
                return;
            }

            Minecraft mc = Minecraft.getInstance();

            if (mc.getConnection() != null)
            {
                mc.getConnection().send(new ServerboundContainerClosePacket(packet.getContainerId()));
            }
        }
    }
}
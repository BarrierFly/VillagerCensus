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

@Mixin(ClientPacketListener.class)
public abstract class MixinClientPacketListener
{
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

    @Inject(method = "handleOpenScreen", at = @At("HEAD"), cancellable = true)
    private void villagercensus$onOpenScreen(ClientboundOpenScreenPacket packet, CallbackInfo ci)
    {
        if (CensusManager.getInstance().onOpenScreen(packet))
        {
            ci.cancel();

            Minecraft mc = Minecraft.getInstance();

            if (mc.getConnection() != null)
            {
                mc.getConnection().send(new ServerboundContainerClosePacket(packet.getContainerId()));
            }
        }
    }
}

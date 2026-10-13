package net.villagercensus.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import io.netty.channel.ChannelFutureListener;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.villagercensus.census.CensusManager;

// The 3-argument overload is the single sink every outgoing packet funnels into (send(Packet) and
// send(Packet, listener) delegate to it, and Fabric's sendPacket() calls the 2-argument one), so
// offers-hud's probes are observed as well. Read-only: the packet is never cancelled here, the
// container stays owned by OffersHudCompat.
@Mixin(Connection.class)
public abstract class MixinConnection
{
    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;Z)V",
            at = @At("HEAD"))
    private void villagercensus$onSendPacket(Packet<?> packet, ChannelFutureListener listener,
                                             boolean flush, CallbackInfo ci)
    {
        if (packet instanceof ServerboundInteractPacket interact)
        {
            //? if >= 26.1 {
            CensusManager.getInstance().onOutboundInteract(interact.entityId());
            //?} else {
            /*CensusManager.getInstance()
                    .onOutboundInteract(((IMixinServerboundInteractPacket) interact).villagercensus$entityId());
            *//*?}*/
        }
    }
}

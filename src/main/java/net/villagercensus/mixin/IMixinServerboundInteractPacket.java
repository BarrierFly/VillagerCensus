package net.villagercensus.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.network.protocol.game.ServerboundInteractPacket;

// 1.21.11 only: there ServerboundInteractPacket is a plain class with a private entityId, while
// 26.3 made it a record with a public accessor. The field exists in both versions, so this mixin
// is registered unconditionally.
@Mixin(ServerboundInteractPacket.class)
public interface IMixinServerboundInteractPacket
{
    @Accessor("entityId")
    int villagercensus$entityId();
}

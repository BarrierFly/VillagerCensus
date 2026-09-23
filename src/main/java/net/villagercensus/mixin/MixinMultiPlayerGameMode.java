package net.villagercensus.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.villagercensus.census.CensusManager;

//? if >= 26.1 {
import net.minecraft.world.phys.EntityHitResult;
//?}

@Mixin(MultiPlayerGameMode.class)
public abstract class MixinMultiPlayerGameMode
{
    //? if >= 26.1 {
    @Inject(method = "interact", at = @At("HEAD"))
    private void villagercensus$onInteract(Player player, Entity entity, EntityHitResult hitResult, InteractionHand hand,
                                           CallbackInfoReturnable<InteractionResult> cir)
    {
        CensusManager.getInstance().onInteract(entity, hand);
    }
    //?} else {
    /*@Inject(method = "interact", at = @At("HEAD"))
    private void villagercensus$onInteract(Player player, Entity entity, InteractionHand hand,
                                           CallbackInfoReturnable<InteractionResult> cir)
    {
        CensusManager.getInstance().onInteract(entity, hand);
    }
    *//*?}*/
}

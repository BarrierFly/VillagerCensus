package net.villagercensus.mixin;

import java.util.function.Predicate;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.villagercensus.census.CensusManager;

/**
 * Optional "pass through already counted villagers" pick (planning item 9.4). When a census is
 * running, the trigger item is in the main hand and the crosshair is on a villager that this
 * session already recorded, the pick ray is repeated with that villager excluded so the entity
 * behind it becomes the target. Disabled by default ({@code passThrough}); when nothing is behind
 * the counted villager the original hit is kept.
 */
@Mixin(ProjectileUtil.class)
public abstract class MixinProjectileUtil
{
    // Guards the recursive call below, which re-enters this mixin.
    private static boolean villagercensus$resolving;

    @Inject(
            method = "getEntityHitResult(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;"
                    + "Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;D)"
                    + "Lnet/minecraft/world/phys/EntityHitResult;",
            at = @At("RETURN"),
            cancellable = true)
    private static void villagercensus$passThroughCountedVillager(Entity source, Vec3 min, Vec3 max,
                                                                  AABB box, Predicate<Entity> predicate,
                                                                  double maxDistanceSq,
                                                                  CallbackInfoReturnable<EntityHitResult> cir)
    {
        if (villagercensus$resolving)
        {
            return;
        }

        EntityHitResult hit = cir.getReturnValue();

        if (hit == null || !CensusManager.getInstance().isPassThroughTarget(hit.getEntity()))
        {
            return;
        }

        Entity blocked = hit.getEntity();

        villagercensus$resolving = true;

        try
        {
            EntityHitResult behind = ProjectileUtil.getEntityHitResult(source, min, max, box,
                    candidate -> !candidate.equals(blocked) && predicate.test(candidate), maxDistanceSq);

            if (behind != null)
            {
                cir.setReturnValue(behind);
            }
        }
        finally
        {
            villagercensus$resolving = false;
        }
    }
}
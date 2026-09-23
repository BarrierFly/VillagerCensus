package net.villagercensus.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.world.entity.Entity;

@Mixin(Entity.class)
public interface IMixinEntity
{
    @Invoker("setSharedFlag")
    void villagercensus$setSharedFlag(int flag, boolean value);
}

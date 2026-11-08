package net.villagercensus.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.gui.screens.inventory.MerchantScreen;

// The scroll offset is private in 26.3 and package-private in 1.21.11; an accessor covers both.
@Mixin(MerchantScreen.class)
public interface IMixinMerchantScreen
{
    @Accessor("scrollOff")
    int villagercensus$getScrollOff();
}

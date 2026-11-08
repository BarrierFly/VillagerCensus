package net.villagercensus.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/*? if >= 26.1 {*/
import net.minecraft.client.gui.GuiGraphicsExtractor;
/*?} else {*/
/*import net.minecraft.client.gui.GuiGraphics;
*//*?}*/
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.villagercensus.render.MerchantOfferMarks;

// The merchant screen only appears when this mod does not suppress it (a vanilla trade screen, or
// offers-hud previewing it), so the competitive book rows are marked on top of the vanilla widgets.
@Mixin(MerchantScreen.class)
public abstract class MixinMerchantScreen
{
    //? if >= 26.1 {
    @Inject(method = "extractContents", at = @At("TAIL"))
    private void villagercensus$markOffers(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float tickDelta,
                                           CallbackInfo ci)
    {
        MerchantOfferMarks.draw(graphics, (MerchantScreen) (Object) this,
                ((IMixinMerchantScreen) this).villagercensus$getScrollOff());
    }
    //?} else {
    /*@Inject(method = "renderContents", at = @At("TAIL"))
    private void villagercensus$markOffers(GuiGraphics graphics, int mouseX, int mouseY, float tickDelta,
                                           CallbackInfo ci)
    {
        MerchantOfferMarks.draw(graphics, (MerchantScreen) (Object) this,
                ((IMixinMerchantScreen) this).villagercensus$getScrollOff());
    }
    *//*?}*/
}

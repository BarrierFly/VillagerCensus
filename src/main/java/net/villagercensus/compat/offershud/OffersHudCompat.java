package net.villagercensus.compat.offershud;

import java.lang.reflect.Method;

import net.fabricmc.loader.api.FabricLoader;
import net.villagercensus.Reference;
import net.villagercensus.config.Configs;

/**
 * Optional, read-only integration with naari3's offers-hud.
 *
 * <p>Both mods cancel {@code ClientPacketListener.handleOpenScreen} for the merchant screen and
 * both send a {@code ServerboundContainerClosePacket} afterwards. offers-hud only does so while
 * its own "open window" flag is clear (see its {@code ValidTrade} / {@code ReceiveTradeOfferPacket}
 * mixins), and that flag cannot be observed without reflection. To guarantee the container is
 * closed exactly once, this mod skips its own close packet whenever offers-hud is installed and
 * reports that it will send one. If the reflective probe fails, this mod falls back to closing the
 * container itself, so the two-mod setup never leaves a container open.
 *
 * <p>Nothing here is a hard dependency: the mod id is probed with {@code FabricLoader} and the
 * offers-hud class is only touched reflectively.
 */
public final class OffersHudCompat
{
    public static final String OFFERSHUD_ID = "offershud";
    private static final String OFFERSHUD_CLASS = "net.naari3.offershud.OffersHUD";

    private static Boolean loaded;
    private static Method getOpenWindow;
    private static boolean probeFailed;

    private OffersHudCompat()
    {
    }

    public static boolean isLoaded()
    {
        if (loaded == null)
        {
            boolean present;

            try
            {
                present = FabricLoader.getInstance().isModLoaded(OFFERSHUD_ID);
            }
            catch (Throwable t)
            {
                Reference.logger().warn("Could not probe for offers-hud", t);
                present = false;
            }

            loaded = present;

            if (present)
            {
                Reference.logger().info("offers-hud detected; suppressed merchant containers are "
                        + "closed by it while villagercensus.config.generic.offersHudCompat is on");
            }
        }

        return loaded;
    }

    /**
     * @return true if offers-hud will send the container close for a merchant screen it did not
     *         open itself, meaning this mod must not send a second one.
     */
    public static boolean willSendContainerClose()
    {
        if (!isLoaded() || !Configs.Generic.OFFERSHUD_COMPAT.getBooleanValue())
        {
            return false;
        }

        Boolean openWindow = readOpenWindow();

        if (openWindow == null)
        {
            // Unknown state: assume offers-hud stays out of the way and close the container here.
            return false;
        }

        return !openWindow;
    }

    private static Boolean readOpenWindow()
    {
        if (probeFailed)
        {
            return null;
        }

        try
        {
            if (getOpenWindow == null)
            {
                Class<?> clazz = Class.forName(OFFERSHUD_CLASS);
                getOpenWindow = clazz.getMethod("getOpenWindow");
            }

            Object value = getOpenWindow.invoke(null);
            return value instanceof Boolean flag ? flag : null;
        }
        catch (Throwable t)
        {
            probeFailed = true;
            Reference.logger().warn("Could not read offers-hud's open-window flag; "
                    + "villagercensus will keep closing the container itself", t);
            return null;
        }
    }
}
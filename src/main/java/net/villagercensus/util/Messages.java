package net.villagercensus.util;

import fi.dy.masa.malilib.util.InfoUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.villagercensus.Reference;

/**
 * Sends feedback to the vanilla action bar instead of malilib's on-screen message overlay,
 * which draws an opaque background and fades out after a few seconds.
 */
public final class Messages
{
    private Messages()
    {
    }

    public static void info(String key, Object... args)
    {
        send(key, 0xFFFFFF, args);
    }

    public static void success(String key, Object... args)
    {
        send(key, 0x55FF55, args);
    }

    public static void warn(String key, Object... args)
    {
        send(key, 0xFFAA00, args);
    }

    public static void error(String key, Object... args)
    {
        send(key, 0xFF5555, args);
    }

    private static void send(String key, int color, Object... args)
    {
        MutableComponent message = Component.translatable(key, args).withColor(color);

        if (Minecraft.getInstance().level != null)
        {
            InfoUtils.sendVanillaMessage(message);
        }
        else
        {
            Reference.logger().info(message.getString());
        }
    }
}

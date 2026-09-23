package net.villagercensus;

import fi.dy.masa.malilib.util.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Reference
{
    public static final String MOD_ID = "villagercensus";
    public static final String MOD_NAME = "Villager Census";
    public static final String MOD_VERSION = StringUtils.getModVersionString(MOD_ID);

    public static Logger logger()
    {
        return Holder.LOGGER;
    }

    private static final class Holder
    {
        private static final Logger LOGGER = LogManager.getLogger(MOD_ID);
    }
}

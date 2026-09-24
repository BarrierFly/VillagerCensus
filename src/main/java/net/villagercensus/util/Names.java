package net.villagercensus.util;

import fi.dy.masa.malilib.util.StringUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class Names
{
    public static String item(String itemId)
    {
        if (itemId == null || itemId.isEmpty())
        {
            return "-";
        }

        Identifier id = Identifier.tryParse(itemId);
        Item item = null;

        if (id != null)
        {
            item = BuiltInRegistries.ITEM.get(id).map(ref -> ref.value()).orElse(null);
        }

        if (item == null || item == Items.AIR)
        {
            return itemId;
        }

        try
        {
            String local = new ItemStack(item).getHoverName().getString();
            return local + " (" + itemId + ")";
        }
        catch (Exception e)
        {
            return itemId;
        }
    }

    public static String profession(String professionId)
    {
        if (professionId == null || professionId.isEmpty())
        {
            return "-";
        }

        return professionName(professionId) + " (" + professionId + ")";
    }

    public static String professionName(String professionId)
    {
        if (professionId == null || professionId.isEmpty())
        {
            return "-";
        }

        String path = professionId.contains(":") ? professionId.substring(professionId.indexOf(':') + 1) : professionId;
        String key = "entity.minecraft.villager." + path;
        String translated = StringUtils.translate(key);

        return translated == null || translated.equals(key) ? path : translated;
    }

    /**
     * Localized item name without the registry id, for compact GUIs.
     */
    public static String itemName(String itemId)
    {
        if (itemId == null || itemId.isEmpty())
        {
            return "-";
        }

        Identifier id = Identifier.tryParse(itemId);
        Item item = null;

        if (id != null)
        {
            item = BuiltInRegistries.ITEM.get(id).map(ref -> ref.value()).orElse(null);
        }

        if (item == null || item == Items.AIR)
        {
            return shortPath(itemId);
        }

        try
        {
            return new ItemStack(item).getHoverName().getString();
        }
        catch (Exception e)
        {
            return shortPath(itemId);
        }
    }

    private static String shortPath(String id)
    {
        int colon = id.indexOf(':');
        return colon >= 0 ? id.substring(colon + 1) : id;
    }

    public static String enchantment(String enchantmentId, int level, int maxLevel)
    {
        String name;

        if (enchantmentId != null && enchantmentId.contains(":"))
        {
            String path = enchantmentId.substring(enchantmentId.indexOf(':') + 1);
            String key = "enchantment.minecraft." + path;
            String translated = StringUtils.translate(key);
            name = (translated == null || translated.equals(key)) ? path : translated;
        }
        else
        {
            name = String.valueOf(enchantmentId);
        }

        String roman = toRoman(level);
        StringBuilder sb = new StringBuilder(name).append(' ').append(roman);

        if (maxLevel > 0)
        {
            sb.append('/').append(maxLevel);
        }

        return sb.toString();
    }

    private static String toRoman(int value)
    {
        if (value <= 0)
        {
            return String.valueOf(value);
        }

        String[] romans = { "M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I" };
        int[] values = { 1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1 };
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < values.length; i++)
        {
            while (value >= values[i])
            {
                value -= values[i];
                sb.append(romans[i]);
            }
        }

        return sb.toString();
    }
}

package net.villagercensus.trade;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class TradeCategory
{
    public String profession = "";
    public String id = "";
    public String label = "";
    public List<String> cost1;
    public List<String> cost2;
    public List<String> result;
    // Ids this category replaced when the catalog collapsed variant trades. They keep older
    // selections working after the catalog is normalized.
    public List<String> aliases = new ArrayList<>();

    public boolean matches(String professionId, String c1, String c2, String res)
    {
        if (!this.profession.equals(professionId))
        {
            return false;
        }

        return matchesList(this.cost1, c1)
                && matchesList(this.cost2, c2)
                && matchesList(this.result, res);
    }

    private static boolean matchesList(List<String> list, String value)
    {
        // An empty/absent list means "don't care".
        return list == null || list.isEmpty() || list.contains(value);
    }

    public String displayLabel()
    {
        return this.label == null || this.label.isEmpty() ? this.id : this.label;
    }

    /**
     * Returns true if the stored selection list references this category, either by its current
     * id or by one of the variant ids it replaced.
     */
    public boolean selectedBy(Collection<String> selected)
    {
        if (selected.contains(this.profession + "/" + this.id) || selected.contains(this.id))
        {
            return true;
        }

        for (String alias : this.aliases)
        {
            if (selected.contains(this.profession + "/" + alias) || selected.contains(alias))
            {
                return true;
            }
        }

        return false;
    }
}

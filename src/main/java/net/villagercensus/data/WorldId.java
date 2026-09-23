package net.villagercensus.data;

import fi.dy.masa.malilib.util.StringUtils;

public class WorldId
{
    public static String current()
    {
        String name = StringUtils.getWorldOrServerName();
        return name == null || name.isEmpty() ? "unknown" : name;
    }

    public static String safe(String input)
    {
        if (input == null || input.isEmpty())
        {
            return "unnamed";
        }

        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < input.length(); i++)
        {
            char c = input.charAt(i);

            if (c == '\\' || c == '/' || c == ':' || c == '*' || c == '?' || c == '"'
                    || c == '<' || c == '>' || c == '|' || c < 32)
            {
                sb.append('_');
            }
            else
            {
                sb.append(c);
            }
        }

        String out = sb.toString().trim();

        if (out.isEmpty())
        {
            out = "unnamed";
        }

        if (out.length() > 48)
        {
            out = out.substring(0, 48);
        }

        return out;
    }

    public static String safeDimension(String dimension)
    {
        return safe(dimension == null ? "unknown" : dimension);
    }
}

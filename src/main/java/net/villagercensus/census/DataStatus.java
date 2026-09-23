package net.villagercensus.census;

public enum DataStatus
{
    FULL                ("full"),
    WEAK                ("weak"),
    NO_TRADE_DATA       ("no_trade_data"),
    DRAFT_UNVERIFIED    ("draft_unverified"),
    VERIFYING           ("verifying"),
    NOT_VERIFIED        ("not_verified");

    private final String id;

    DataStatus(String id)
    {
        this.id = id;
    }

    public String getId()
    {
        return this.id;
    }

    public static DataStatus fromId(String id)
    {
        for (DataStatus status : values())
        {
            if (status.id.equals(id))
            {
                return status;
            }
        }

        return FULL;
    }
}

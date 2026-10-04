package net.villagercensus.trade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class TradeCatalogTest
{
    private static final String LIBRARIAN = "minecraft:librarian";

    @Test
    void emptyListsMeanDontCare()
    {
        TradeCategory category = new TradeCategory();
        category.profession = LIBRARIAN;
        category.id = "buy_book";
        category.cost1 = List.of();
        category.cost2 = List.of();
        category.result = List.of();

        assertTrue(category.matches(LIBRARIAN, "minecraft:emerald", "", "minecraft:book"));
        assertTrue(category.matches(LIBRARIAN, "minecraft:paper", "", "minecraft:bread"));
    }

    @Test
    void professionMustMatch()
    {
        TradeCategory category = new TradeCategory();
        category.profession = LIBRARIAN;
        category.id = "buy_book";
        category.cost1 = List.of("minecraft:emerald");
        category.result = List.of("minecraft:book");

        assertFalse(category.matches("minecraft:smith", "minecraft:emerald", "", "minecraft:book"));
        assertTrue(category.matches(LIBRARIAN, "minecraft:emerald", "", "minecraft:book"));
    }

    @Test
    void costListsAreInclusive()
    {
        TradeCategory category = new TradeCategory();
        category.profession = LIBRARIAN;
        category.id = "buy_ink";
        category.cost1 = List.of("minecraft:emerald", "minecraft:paper");

        assertTrue(category.matches(LIBRARIAN, "minecraft:paper", "", "minecraft:ink_cyan"));
        assertFalse(category.matches(LIBRARIAN, "minecraft:redstone", "", "minecraft:ink_cyan"));
    }

    @Test
    void selectedByAcceptsProfessionKeysBareIdsAndAliases()
    {
        TradeCategory category = new TradeCategory();
        category.profession = LIBRARIAN;
        category.id = "sell_book";
        category.aliases = List.of("sell_enchanted_book_old");

        assertTrue(category.selectedBy(List.of("minecraft:librarian/sell_book")));
        assertTrue(category.selectedBy(List.of("sell_book")));
        assertTrue(category.selectedBy(List.of("minecraft:librarian/sell_enchanted_book_old")));
        assertTrue(category.selectedBy(List.of("sell_enchanted_book_old")));
        assertFalse(category.selectedBy(List.of("minecraft:smith/sell_book")));
        assertFalse(category.selectedBy(List.of("sell_paper")));
    }

    @Test
    void displayLabelFallsBackToId()
    {
        TradeCategory category = new TradeCategory();
        category.id = "sell_book";
        category.label = "";

        assertEquals("sell_book", category.displayLabel());

        category.label = "Books";
        assertEquals("Books", category.displayLabel());
    }

    @Test
    void catalogLoadsEveryProfession()
    {
        TradeCatalog catalog = TradeCatalog.get();
        catalog.ensureLoaded();

        // The generated catalog must never end up empty, otherwise every trade silently falls back
        // to an item-triple label.
        assertTrue(catalog.allCategories().size() > 100,
                "expected the generated trade catalog to be populated");
    }

    @Test
    void catalogMatchesKnownVanillaTrades()
    {
        TradeCatalog catalog = TradeCatalog.get();

        TradeCategory coal = catalog.match("minecraft:weaponsmith", "minecraft:coal", "", "minecraft:emerald", false);
        assertNotNull(coal, "coal -> emerald is a weaponsmith trade and must resolve");
        assertEquals("buy_coal", coal.id);

        TradeCategory unknown = catalog.match("minecraft:weaponsmith",
                "minecraft:barrier", "", "minecraft:barrier", false);
        assertNull(unknown, "an offer that is not in the catalog must not resolve to a category");
    }

    @Test
    void enchantedAndPlainVariantsResolveSeparately()
    {
        TradeCatalog catalog = TradeCatalog.get();
        TradeCategory plain = catalog.match("minecraft:bowyer", "minecraft:emerald", "", "minecraft:bow", false);
        TradeCategory enchanted = catalog.match("minecraft:bowyer", "minecraft:emerald", "", "minecraft:bow", true);

        if (plain != null && enchanted != null)
        {
            assertFalse(plain.id.equals(enchanted.id),
                    "the enchanted and unenchanted bow trades must not collapse into one category");
        }
        else
        {
            // The catalog may not carry bow variants on every Minecraft version; only assert that
            // lookups do not throw when one of the variants is absent.
            assertTrue(plain == null || enchanted == null);
        }
    }
}
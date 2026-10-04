package net.villagercensus.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import net.villagercensus.data.WorldId;

class WorldIdTest
{
    @Test
    void illegalFileNameCharactersBecomeUnderscores()
    {
        assertEquals("a_b_c_d", WorldId.safe("a/b:c\\d"));
        assertEquals("x_y", WorldId.safe("x*y"));
        assertEquals("a_b_c_d_e_f", WorldId.safe("a?b\"c<d>e|f"));
    }

    @Test
    void emptyInputGetsAPlaceholderName()
    {
        assertEquals("unnamed", WorldId.safe(null));
        assertEquals("unnamed", WorldId.safe(""));
    }

    @Test
    void controlCharactersAreStripped()
    {
        assertEquals("a_b", WorldId.safe("a\nb"));
    }

    @Test
    void namesAreTruncatedToTheFileNameLimit()
    {
        String long48 = "x".repeat(48);
        assertEquals(long48, WorldId.safe(long48));
        assertEquals(48, WorldId.safe("y".repeat(60)).length());
    }

    @Test
    void dimensionsUseTheSameSanitizing()
    {
        assertEquals("minecraft_overworld", WorldId.safeDimension("minecraft:overworld"));
        assertEquals("unknown", WorldId.safeDimension(null));
    }
}
package com.devmod.telemetry;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.devmod.telemetry.util.BitPackedFlags;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BitPackedFlagsDirectTest {

    @Test
    @DisplayName("Pack/unpack and flag operations preserve movement state")
    void packUnpackAndFlagOps() {
        int packed = BitPackedFlags.packMovementSimple(true, false, true, true);

        assertTrue(BitPackedFlags.isSet(packed, BitPackedFlags.FLAG_SPRINTING));
        assertFalse(BitPackedFlags.isSet(packed, BitPackedFlags.FLAG_SNEAKING));
        assertTrue(BitPackedFlags.isSet(packed, BitPackedFlags.FLAG_SWIMMING));
        assertTrue(BitPackedFlags.isSet(packed, BitPackedFlags.FLAG_FALLING));

        boolean[] flags = BitPackedFlags.unpack(packed, 4);
        assertTrue(flags[0]);
        assertFalse(flags[1]);
        assertTrue(flags[2]);
        assertTrue(flags[3]);

        int updated = BitPackedFlags.setFlag(0, BitPackedFlags.FLAG_SNEAKING);
        assertTrue(BitPackedFlags.isSet(updated, BitPackedFlags.FLAG_SNEAKING));
        updated = BitPackedFlags.clearFlag(updated, BitPackedFlags.FLAG_SNEAKING);
        assertFalse(BitPackedFlags.isSet(updated, BitPackedFlags.FLAG_SNEAKING));
    }

    @Test
    @DisplayName("Nibble packing extracts low and high values")
    void nibblePackingWorks() {
        int packed = BitPackedFlags.packTwoNibbles(3, 12);
        assertEquals(3, BitPackedFlags.getLowNibble(packed));
        assertEquals(12, BitPackedFlags.getHighNibble(packed));
    }

    @Test
    @DisplayName("Debug output includes enabled movement flags")
    void debugMovementIncludesFlags() {
        int packed = BitPackedFlags.packMovement(true, true, false, false, false, false, false, false);
        String debug = BitPackedFlags.debugMovement(packed);
        assertTrue(debug.contains("SPRINT"));
        assertTrue(debug.contains("SNEAK"));
    }
}

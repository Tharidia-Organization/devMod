package com.devmod.network;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PacketValidatorDirectTest {

    @Test
    @DisplayName("Numeric validation clamps to configured bounds")
    void numericValidationClampsBounds() {
        PacketValidator validator = PacketValidator.INSTANCE;

        assertEquals(-5.0, validator.validateDamage(-5.0), 0.0001);
        assertEquals(PacketValidator.MAX_GUARD_VALUE, validator.validateDamage(2_000_000.0), 0.0001);

        assertEquals(-1.0, validator.validateArmor(-1.0), 0.0001);
        assertEquals(PacketValidator.MAX_GUARD_VALUE, validator.validateArmor(2_000_000.0), 0.0001);

        assertEquals(PacketValidator.MAX_GUARD_INT, validator.validateDurability(2_000_000));
        assertEquals(-10, validator.validateDurability(-10));
    }
}

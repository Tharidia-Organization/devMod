package com.devmod.network;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PacketValidatorDirectTest {

    @Test
    @DisplayName("Numeric validation clamps to configured bounds")
    void numericValidationClampsBounds() {
        PacketValidator validator = PacketValidator.INSTANCE;

        assertEquals(PacketValidator.MIN_ATTRIBUTE_VALUE, validator.validateDamage(-5.0), 0.0001);
        assertEquals(PacketValidator.MAX_DAMAGE, validator.validateDamage(999999.0), 0.0001);

        assertEquals(PacketValidator.MIN_ATTRIBUTE_VALUE, validator.validateArmor(-1.0), 0.0001);
        assertEquals(PacketValidator.MAX_ARMOR, validator.validateArmor(99999.0), 0.0001);

        assertEquals(PacketValidator.MAX_DURABILITY, validator.validateDurability(99999));
        assertEquals(0, validator.validateDurability(-10));
    }
}

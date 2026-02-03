package com.devmod.client.ui.radial.config;

import java.lang.reflect.Field;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("Radial Menu Scaler")
class RadialMenuScalerTest {

    @Test
    @DisplayName("scaleConstant snaps negative values symmetrically")
    void scaleConstantHandlesNegativeValues() throws Exception {
        Field scaleField = RadialMenuScaler.class.getDeclaredField("scaleFactor");
        scaleField.setAccessible(true);
        float original = scaleField.getFloat(null);
        try {
            scaleField.setFloat(null, 1.0f);
            assertEquals(-8, RadialMenuScaler.scaleConstant(-8),
                "Negative values should snap symmetrically");
        } finally {
            scaleField.setFloat(null, original);
        }
    }
}

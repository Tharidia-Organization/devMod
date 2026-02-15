package com.devmod.hologram.data;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.devmod.TestBootstrap;

@DisplayName("HologramState")
class HologramStateTest {

    @BeforeAll
    static void setup() {
        TestBootstrap.init();
    }

    @Nested
    @DisplayName("color methods")
    class Colors {
        @Test
        @DisplayName("getPrimaryWithAlpha adds full alpha to primary")
        void primaryWithAlpha() {
            int withAlpha = HologramState.IDLE.getPrimaryWithAlpha();
            assertEquals(0xFF000000 | HologramState.IDLE.getPrimary(), withAlpha);
            // Top byte should be 0xFF
            assertEquals(0xFF, (withAlpha >>> 24) & 0xFF);
        }

        @Test
        @DisplayName("getSecondaryWithAlpha adds full alpha to secondary")
        void secondaryWithAlpha() {
            int withAlpha = HologramState.IDLE.getSecondaryWithAlpha();
            assertEquals(0xFF000000 | HologramState.IDLE.getSecondary(), withAlpha);
        }

        @Test
        @DisplayName("each state has distinct primary colors")
        void distinctPrimary() {
            var colors = new java.util.HashSet<Integer>();
            for (HologramState state : HologramState.values()) {
                assertTrue(colors.add(state.getPrimary()),
                    "Duplicate primary color for " + state.name());
            }
        }
    }

    @Nested
    @DisplayName("particle properties")
    class Particles {
        @Test
        @DisplayName("all states have a particle type")
        void allHaveParticleType() {
            for (HologramState state : HologramState.values()) {
                assertNotNull(state.getParticleType(), state.name() + " missing particle type");
            }
        }

        @Test
        @DisplayName("all states have positive base particle count")
        void positiveBaseCount() {
            for (HologramState state : HologramState.values()) {
                assertTrue(state.getBaseParticleCount() > 0,
                    state.name() + " should have positive particle count");
            }
        }

        @Test
        @DisplayName("all states have positive particle spread")
        void positiveSpread() {
            for (HologramState state : HologramState.values()) {
                assertTrue(state.getParticleSpread() > 0,
                    state.name() + " should have positive spread");
            }
        }
    }

    @Test
    @DisplayName("has exactly 6 states")
    void stateCount() {
        assertEquals(6, HologramState.values().length);
    }

    @Test
    @DisplayName("IDLE primary color is correct")
    void idlePrimary() {
        assertEquals(0x00d4aa, HologramState.IDLE.getPrimary());
    }

    @Test
    @DisplayName("ERROR primary color is correct")
    void errorPrimary() {
        assertEquals(0xe74c3c, HologramState.ERROR.getPrimary());
    }
}

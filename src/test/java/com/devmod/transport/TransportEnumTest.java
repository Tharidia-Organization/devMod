package com.devmod.transport;

import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import net.minecraft.world.BossEvent;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Transport Enum Types")
class TransportEnumTest {

    // =========================================================================
    // TransportState
    // =========================================================================

    @Nested
    @DisplayName("TransportState")
    class TransportStateTests {

        @Test
        @DisplayName("byName returns correct state")
        void byName() {
            assertEquals(TransportState.IDLE, TransportState.byName("idle"));
            assertEquals(TransportState.CHARGING, TransportState.byName("charging"));
            assertEquals(TransportState.ACTIVE, TransportState.byName("active"));
            assertEquals(TransportState.ERROR, TransportState.byName("error"));
            assertEquals(TransportState.COOLDOWN, TransportState.byName("cooldown"));
            assertEquals(TransportState.LOCKED, TransportState.byName("locked"));
        }

        @Test
        @DisplayName("byName returns IDLE for unknown names")
        void byNameUnknown() {
            assertEquals(TransportState.IDLE, TransportState.byName("nonexistent"));
        }

        @Test
        @DisplayName("isUsable returns true for IDLE, CHARGING, ACTIVE")
        void isUsable() {
            assertTrue(TransportState.IDLE.isUsable());
            assertTrue(TransportState.CHARGING.isUsable());
            assertTrue(TransportState.ACTIVE.isUsable());
            assertFalse(TransportState.ERROR.isUsable());
            assertFalse(TransportState.COOLDOWN.isUsable());
            assertFalse(TransportState.LOCKED.isUsable());
        }

        @Test
        @DisplayName("isProblematic returns true for ERROR, LOCKED")
        void isProblematic() {
            assertTrue(TransportState.ERROR.isProblematic());
            assertTrue(TransportState.LOCKED.isProblematic());
            assertFalse(TransportState.IDLE.isProblematic());
            assertFalse(TransportState.CHARGING.isProblematic());
        }

        @Test
        @DisplayName("getPrimaryColorWithAlpha sets full alpha")
        void primaryColorAlpha() {
            int argb = TransportState.IDLE.getPrimaryColorWithAlpha();
            assertEquals(0xFF, (argb >> 24) & 0xFF);
        }

        @Test
        @DisplayName("color component extraction is correct for IDLE")
        void colorComponents() {
            // IDLE primary = 0x1a3a5c
            assertEquals(0x1a, TransportState.IDLE.getPrimaryRed());
            assertEquals(0x3a, TransportState.IDLE.getPrimaryGreen());
            assertEquals(0x5c, TransportState.IDLE.getPrimaryBlue());
        }

        @Test
        @DisplayName("getSerializedName returns name")
        void serializedName() {
            assertEquals("idle", TransportState.IDLE.getSerializedName());
            assertEquals("error", TransportState.ERROR.getSerializedName());
        }
    }

    // =========================================================================
    // TransportMode
    // =========================================================================

    @Nested
    @DisplayName("TransportMode")
    class TransportModeTests {

        @Test
        @DisplayName("byName returns correct mode for all values")
        void byNameAll() {
            assertEquals(TransportMode.LINKED, TransportMode.byName("linked"));
            assertEquals(TransportMode.NETWORK, TransportMode.byName("network"));
            assertEquals(TransportMode.FIXED, TransportMode.byName("fixed"));
            assertEquals(TransportMode.WAYPOINT, TransportMode.byName("waypoint"));
            assertEquals(TransportMode.INSTANCE, TransportMode.byName("instance"));
            assertEquals(TransportMode.RETURN, TransportMode.byName("return"));
            assertEquals(TransportMode.PARTY, TransportMode.byName("party"));
            assertEquals(TransportMode.ZONE, TransportMode.byName("zone"));
        }

        @Test
        @DisplayName("byName returns DEFAULT (NETWORK) for unknown")
        void byNameDefault() {
            assertEquals(TransportMode.NETWORK, TransportMode.byName("unknown"));
        }

        @Test
        @DisplayName("byIndex returns DEFAULT for out of bounds")
        void byIndexOutOfBounds() {
            assertEquals(TransportMode.NETWORK, TransportMode.byIndex(-1));
            assertEquals(TransportMode.NETWORK, TransportMode.byIndex(999));
        }

        @Test
        @DisplayName("byIndex returns correct mode")
        void byIndexValid() {
            assertEquals(TransportMode.LINKED, TransportMode.byIndex(0));
        }

        @Test
        @DisplayName("requiresDestination is true for LINKED, FIXED, INSTANCE, RETURN, PARTY")
        void requiresDestination() {
            assertTrue(TransportMode.LINKED.requiresDestination());
            assertTrue(TransportMode.FIXED.requiresDestination());
            assertTrue(TransportMode.INSTANCE.requiresDestination());
            assertTrue(TransportMode.RETURN.requiresDestination());
            assertTrue(TransportMode.PARTY.requiresDestination());
            assertFalse(TransportMode.NETWORK.requiresDestination());
            assertFalse(TransportMode.WAYPOINT.requiresDestination());
        }

        @Test
        @DisplayName("supportsMultipleDestinations is true for NETWORK, WAYPOINT, ZONE")
        void supportsMultiple() {
            assertTrue(TransportMode.NETWORK.supportsMultipleDestinations());
            assertTrue(TransportMode.WAYPOINT.supportsMultipleDestinations());
            assertTrue(TransportMode.ZONE.supportsMultipleDestinations());
            assertFalse(TransportMode.LINKED.supportsMultipleDestinations());
        }

        @Test
        @DisplayName("requiresParty is true only for PARTY")
        void requiresParty() {
            assertTrue(TransportMode.PARTY.requiresParty());
            assertFalse(TransportMode.LINKED.requiresParty());
            assertFalse(TransportMode.NETWORK.requiresParty());
        }

        @Test
        @DisplayName("usesNetworkName for NETWORK and ZONE")
        void usesNetworkName() {
            assertTrue(TransportMode.NETWORK.usesNetworkName());
            assertTrue(TransportMode.ZONE.usesNetworkName());
            assertFalse(TransportMode.LINKED.usesNetworkName());
        }

        @Test
        @DisplayName("isBidirectional is true only for LINKED")
        void isBidirectional() {
            assertTrue(TransportMode.LINKED.isBidirectional());
            assertFalse(TransportMode.FIXED.isBidirectional());
        }

        @Test
        @DisplayName("supportsRandomSelection is true only for NETWORK")
        void supportsRandom() {
            assertTrue(TransportMode.NETWORK.supportsRandomSelection());
            assertFalse(TransportMode.WAYPOINT.supportsRandomSelection());
        }

        @Test
        @DisplayName("showsSelectionGui is true only for WAYPOINT")
        void showsGui() {
            assertTrue(TransportMode.WAYPOINT.showsSelectionGui());
            assertFalse(TransportMode.NETWORK.showsSelectionGui());
        }

        @Test
        @DisplayName("DEFAULT is NETWORK")
        void defaultMode() {
            assertEquals(TransportMode.NETWORK, TransportMode.DEFAULT);
        }
    }

    // =========================================================================
    // TransportColor
    // =========================================================================

    @Nested
    @DisplayName("TransportColor")
    class TransportColorTests {

        @Test
        @DisplayName("byIndex returns DEFAULT for out of bounds")
        void byIndexOutOfBounds() {
            assertEquals(TransportColor.CYAN, TransportColor.byIndex(-1));
            assertEquals(TransportColor.CYAN, TransportColor.byIndex(999));
        }

        @Test
        @DisplayName("byName returns DEFAULT for unknown")
        void byNameUnknown() {
            assertEquals(TransportColor.CYAN, TransportColor.byName("nope"));
        }

        @Test
        @DisplayName("byName returns correct color")
        void byName() {
            assertEquals(TransportColor.RED, TransportColor.byName("red"));
            assertEquals(TransportColor.WHITE, TransportColor.byName("white"));
        }

        @Test
        @DisplayName("fromIndex is alias for byIndex")
        void fromIndex() {
            assertEquals(TransportColor.byIndex(0), TransportColor.fromIndex(0));
            assertEquals(TransportColor.byIndex(5), TransportColor.fromIndex(5));
        }

        @Test
        @DisplayName("getDisplayName converts snake_case to Title Case")
        void displayName() {
            assertEquals("White", TransportColor.WHITE.getDisplayName());
            assertEquals("Light Blue", TransportColor.LIGHT_BLUE.getDisplayName());
            assertEquals("Light Gray", TransportColor.LIGHT_GRAY.getDisplayName());
        }

        @Test
        @DisplayName("next wraps around from last to first")
        void nextWraps() {
            TransportColor last = TransportColor.BLACK;
            assertEquals(TransportColor.WHITE, last.next());
        }

        @Test
        @DisplayName("previous wraps around from first to last")
        void previousWraps() {
            TransportColor first = TransportColor.WHITE;
            assertEquals(TransportColor.BLACK, first.previous());
        }

        @Test
        @DisplayName("next and previous are inverse")
        void nextPreviousInverse() {
            for (TransportColor color : TransportColor.values()) {
                assertEquals(color, color.next().previous());
            }
        }

        @Test
        @DisplayName("getColorWithAlpha sets full alpha")
        void colorWithAlpha() {
            int argb = TransportColor.RED.getColorWithAlpha();
            assertEquals(0xFF, (argb >> 24) & 0xFF);
        }

        @Test
        @DisplayName("RGB float components are in 0.0-1.0 range")
        void floatComponents() {
            for (TransportColor color : TransportColor.values()) {
                float r = color.getRedF();
                float g = color.getGreenF();
                float b = color.getBlueF();
                assertTrue(r >= 0.0f && r <= 1.0f, "Red out of range for " + color);
                assertTrue(g >= 0.0f && g <= 1.0f, "Green out of range for " + color);
                assertTrue(b >= 0.0f && b <= 1.0f, "Blue out of range for " + color);
            }
        }

        @Test
        @DisplayName("getIndex matches ordinal")
        void indexMatchesOrdinal() {
            for (TransportColor color : TransportColor.values()) {
                assertEquals(color.ordinal(), color.getIndex());
            }
        }

        @Test
        @DisplayName("toBossBarColor returns valid mapping")
        void toBossBarColor() {
            assertEquals(BossEvent.BossBarColor.WHITE, TransportColor.WHITE.toBossBarColor());
            assertEquals(BossEvent.BossBarColor.PINK, TransportColor.PINK.toBossBarColor());
            assertEquals(BossEvent.BossBarColor.RED, TransportColor.RED.toBossBarColor());
            assertEquals(BossEvent.BossBarColor.YELLOW, TransportColor.YELLOW.toBossBarColor());
            assertEquals(BossEvent.BossBarColor.GREEN, TransportColor.GREEN.toBossBarColor());
            assertEquals(BossEvent.BossBarColor.GREEN, TransportColor.LIME.toBossBarColor());
            assertEquals(BossEvent.BossBarColor.BLUE, TransportColor.BLUE.toBossBarColor());
            assertEquals(BossEvent.BossBarColor.BLUE, TransportColor.LIGHT_BLUE.toBossBarColor());
            assertEquals(BossEvent.BossBarColor.BLUE, TransportColor.CYAN.toBossBarColor());
            assertEquals(BossEvent.BossBarColor.PURPLE, TransportColor.PURPLE.toBossBarColor());
            assertEquals(BossEvent.BossBarColor.PURPLE, TransportColor.MAGENTA.toBossBarColor());
        }

        @Test
        @DisplayName("DEFAULT is CYAN")
        void defaultColor() {
            assertEquals(TransportColor.CYAN, TransportColor.DEFAULT);
        }
    }

    // =========================================================================
    // TransportNodeType
    // =========================================================================

    @Nested
    @DisplayName("TransportNodeType")
    class TransportNodeTypeTests {

        @Test
        @DisplayName("byName returns correct type")
        void byName() {
            assertEquals(TransportNodeType.PORTAL, TransportNodeType.byName("portal"));
            assertEquals(TransportNodeType.TELEPAD, TransportNodeType.byName("telepad"));
            assertEquals(TransportNodeType.RIFT_GATE, TransportNodeType.byName("rift_gate"));
            assertEquals(TransportNodeType.WAYPOINT, TransportNodeType.byName("waypoint"));
            assertEquals(TransportNodeType.ZONE, TransportNodeType.byName("zone"));
        }

        @Test
        @DisplayName("byName returns TELEPAD for unknown")
        void byNameDefault() {
            assertEquals(TransportNodeType.TELEPAD, TransportNodeType.byName("unknown"));
        }

        @Test
        @DisplayName("byIndex returns TELEPAD for out of bounds")
        void byIndexOutOfBounds() {
            assertEquals(TransportNodeType.TELEPAD, TransportNodeType.byIndex(-1));
            assertEquals(TransportNodeType.TELEPAD, TransportNodeType.byIndex(999));
        }

        @Test
        @DisplayName("hasPhysicalBlock")
        void hasPhysicalBlock() {
            assertTrue(TransportNodeType.PORTAL.hasPhysicalBlock());
            assertTrue(TransportNodeType.TELEPAD.hasPhysicalBlock());
            assertFalse(TransportNodeType.WAYPOINT.hasPhysicalBlock());
            assertFalse(TransportNodeType.VIRTUAL.hasPhysicalBlock());
        }

        @Test
        @DisplayName("supportsLinking")
        void supportsLinking() {
            assertTrue(TransportNodeType.PORTAL.supportsLinking());
            assertTrue(TransportNodeType.TELEPAD.supportsLinking());
            assertFalse(TransportNodeType.RIFT_GATE.supportsLinking());
            assertFalse(TransportNodeType.WAYPOINT.supportsLinking());
        }

        @Test
        @DisplayName("supportsModules")
        void supportsModules() {
            assertTrue(TransportNodeType.PORTAL.supportsModules());
            assertTrue(TransportNodeType.TELEPAD.supportsModules());
            assertFalse(TransportNodeType.RIFT_GATE.supportsModules());
        }

        @Test
        @DisplayName("isTemporary only for RIFT_GATE")
        void isTemporary() {
            assertTrue(TransportNodeType.RIFT_GATE.isTemporary());
            assertFalse(TransportNodeType.PORTAL.isTemporary());
        }

        @Test
        @DisplayName("isNexusRelated for ZONE and INSTANCE_GATE")
        void isNexusRelated() {
            assertTrue(TransportNodeType.ZONE.isNexusRelated());
            assertTrue(TransportNodeType.INSTANCE_GATE.isNexusRelated());
            assertFalse(TransportNodeType.PORTAL.isNexusRelated());
        }

        @Test
        @DisplayName("isPersistent excludes RIFT_GATE, VIRTUAL, RECOVERY_POINT")
        void isPersistent() {
            assertTrue(TransportNodeType.PORTAL.isPersistent());
            assertTrue(TransportNodeType.TELEPAD.isPersistent());
            assertFalse(TransportNodeType.RIFT_GATE.isPersistent());
            assertFalse(TransportNodeType.VIRTUAL.isPersistent());
            assertFalse(TransportNodeType.RECOVERY_POINT.isPersistent());
        }
    }

    // =========================================================================
    // TransportEnhancement
    // =========================================================================

    @Nested
    @DisplayName("TransportEnhancement")
    class TransportEnhancementTests {

        @Test
        @DisplayName("byName returns correct enhancement")
        void byName() {
            assertEquals(TransportEnhancement.HASTE, TransportEnhancement.byName("haste"));
            assertEquals(TransportEnhancement.GATE, TransportEnhancement.byName("gate"));
            assertEquals(TransportEnhancement.CHARGED, TransportEnhancement.byName("charged"));
        }

        @Test
        @DisplayName("byName returns null for unknown")
        void byNameNull() {
            assertNull(TransportEnhancement.byName("nonexistent"));
        }

        @Test
        @DisplayName("byIndex returns CHARGED for out of bounds")
        void byIndexOutOfBounds() {
            assertEquals(TransportEnhancement.CHARGED, TransportEnhancement.byIndex(-1));
            assertEquals(TransportEnhancement.CHARGED, TransportEnhancement.byIndex(999));
        }

        @Test
        @DisplayName("getRangeBonus values are correct")
        void rangeBonusValues() {
            assertEquals(100, TransportEnhancement.ENHANCER.getRangeBonus());
            assertEquals(100, TransportEnhancement.RANGE_AMPLIFIER.getRangeBonus());
            assertEquals(500, TransportEnhancement.STRONG_ENHANCER.getRangeBonus());
            assertEquals(Integer.MAX_VALUE, TransportEnhancement.INFINITY.getRangeBonus());
            assertEquals(0, TransportEnhancement.HASTE.getRangeBonus());
            assertEquals(0, TransportEnhancement.CHARGED.getRangeBonus());
        }

        @Test
        @DisplayName("affectsCharging identifies correct enhancements")
        void affectsCharging() {
            assertTrue(TransportEnhancement.HASTE.affectsCharging());
            assertTrue(TransportEnhancement.FLUX_CAPACITOR.affectsCharging());
            assertTrue(TransportEnhancement.CHARGED.affectsCharging());
            assertTrue(TransportEnhancement.COUNTDOWN.affectsCharging());
            assertFalse(TransportEnhancement.GATE.affectsCharging());
        }

        @Test
        @DisplayName("affectsRange identifies correct enhancements")
        void affectsRange() {
            assertTrue(TransportEnhancement.ENHANCER.affectsRange());
            assertTrue(TransportEnhancement.STRONG_ENHANCER.affectsRange());
            assertTrue(TransportEnhancement.INFINITY.affectsRange());
            assertTrue(TransportEnhancement.RANGE_AMPLIFIER.affectsRange());
            assertFalse(TransportEnhancement.HASTE.affectsRange());
        }

        @Test
        @DisplayName("isModule identifies MODULE category")
        void isModule() {
            assertTrue(TransportEnhancement.CHROMATIC.isModule());
            assertTrue(TransportEnhancement.FLUX_CAPACITOR.isModule());
            assertFalse(TransportEnhancement.HASTE.isModule());
        }

        @Test
        @DisplayName("isRune identifies RUNE category")
        void isRune() {
            assertTrue(TransportEnhancement.HASTE.isRune());
            assertTrue(TransportEnhancement.GATE.isRune());
            assertFalse(TransportEnhancement.CHROMATIC.isRune());
        }

        @Test
        @DisplayName("enablesCrossDimension only for GATE")
        void enablesCrossDimension() {
            assertTrue(TransportEnhancement.GATE.enablesCrossDimension());
            assertFalse(TransportEnhancement.HASTE.enablesCrossDimension());
        }

        @Test
        @DisplayName("MODULE_TYPES contains all physical modules")
        void moduleTypes() {
            Set<TransportEnhancement> modules = TransportEnhancement.MODULE_TYPES;
            assertEquals(7, modules.size());
            assertTrue(modules.contains(TransportEnhancement.CHROMATIC));
            assertTrue(modules.contains(TransportEnhancement.RANGE_AMPLIFIER));
            assertTrue(modules.contains(TransportEnhancement.FLUX_CAPACITOR));
            assertTrue(modules.contains(TransportEnhancement.NETWORK_RELAY));
            assertTrue(modules.contains(TransportEnhancement.MEMORY_CORE));
            assertTrue(modules.contains(TransportEnhancement.PARTY_BEACON));
            assertTrue(modules.contains(TransportEnhancement.FRAME_SEGMENT));
        }

        @Test
        @DisplayName("RUNE_TYPES contains all rune effects")
        void runeTypes() {
            Set<TransportEnhancement> runes = TransportEnhancement.RUNE_TYPES;
            assertEquals(5, runes.size());
            assertTrue(runes.contains(TransportEnhancement.HASTE));
            assertTrue(runes.contains(TransportEnhancement.GATE));
            assertTrue(runes.contains(TransportEnhancement.ENHANCER));
            assertTrue(runes.contains(TransportEnhancement.STRONG_ENHANCER));
            assertTrue(runes.contains(TransportEnhancement.INFINITY));
        }

        @Test
        @DisplayName("byCategory returns correct set")
        void byCategory() {
            Set<TransportEnhancement> runes = TransportEnhancement.byCategory(TransportEnhancement.Category.RUNE);
            assertEquals(TransportEnhancement.RUNE_TYPES, runes);
        }
    }
}

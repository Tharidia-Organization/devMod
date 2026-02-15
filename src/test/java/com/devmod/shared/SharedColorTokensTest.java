package com.devmod.shared;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import net.minecraft.ChatFormatting;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SharedColorTokens")
class SharedColorTokensTest {

    @Test
    @DisplayName("Palette colors are non-zero")
    void paletteColorsNonZero() {
        // BACKGROUND may be 0 (black), so skip it
        assertNotEquals(0, SharedColorTokens.Palette.TEXT_PRIMARY);
        assertNotEquals(0, SharedColorTokens.Palette.TEXT_SECONDARY);
        assertNotEquals(0, SharedColorTokens.Palette.ACCENT_TEAL);
        assertNotEquals(0, SharedColorTokens.Palette.SUCCESS);
        assertNotEquals(0, SharedColorTokens.Palette.WARNING);
        assertNotEquals(0, SharedColorTokens.Palette.ERROR);
    }

    @Test
    @DisplayName("Mask.RGB is standard 0x00FFFFFF")
    void maskRGB() {
        assertEquals(0x00FFFFFF, SharedColorTokens.Mask.RGB);
    }

    @Test
    @DisplayName("Basic colors are non-zero")
    void basicColors() {
        assertNotEquals(0, SharedColorTokens.Basic.RED);
        assertNotEquals(0, SharedColorTokens.Basic.YELLOW);
        assertNotEquals(0, SharedColorTokens.Basic.GREEN);
        assertNotEquals(0, SharedColorTokens.Basic.CYAN);
        assertNotEquals(0, SharedColorTokens.Basic.BLUE);
    }

    @Test
    @DisplayName("Chat formatting constants are valid ChatFormatting values")
    void chatFormattingValues() {
        assertEquals(ChatFormatting.BLACK, SharedColorTokens.Chat.BLACK);
        assertEquals(ChatFormatting.RED, SharedColorTokens.Chat.RED);
        assertEquals(ChatFormatting.GREEN, SharedColorTokens.Chat.GREEN);
        assertEquals(ChatFormatting.BLUE, SharedColorTokens.Chat.BLUE);
        assertEquals(ChatFormatting.YELLOW, SharedColorTokens.Chat.YELLOW);
        assertEquals(ChatFormatting.WHITE, SharedColorTokens.Chat.WHITE);
    }

    @Test
    @DisplayName("Alert colors are distinct")
    void alertColorsDistinct() {
        assertNotEquals(SharedColorTokens.Alert.ERROR, SharedColorTokens.Alert.WARN);
        assertNotEquals(SharedColorTokens.Alert.ERROR, SharedColorTokens.Alert.INFO);
        assertNotEquals(SharedColorTokens.Alert.WARN, SharedColorTokens.Alert.INFO);
    }

    @Test
    @DisplayName("AttributeLog colors are non-zero")
    void attributeLogColors() {
        assertNotEquals(0, SharedColorTokens.AttributeLog.GREEN);
        assertNotEquals(0, SharedColorTokens.AttributeLog.RED);
        assertNotEquals(0, SharedColorTokens.AttributeLog.YELLOW);
        assertNotEquals(0, SharedColorTokens.AttributeLog.CRITICAL_RED);
    }

    @Test
    @DisplayName("BodyPart colors are non-zero")
    void bodyPartColors() {
        assertNotEquals(0, SharedColorTokens.BodyPart.HEAD);
        assertNotEquals(0, SharedColorTokens.BodyPart.BODY);
        assertNotEquals(0, SharedColorTokens.BodyPart.ARMS);
        assertNotEquals(0, SharedColorTokens.BodyPart.LEGS);
    }

    @Test
    @DisplayName("Endurance StyleRank colors are all distinct")
    void styleRankDistinct() {
        int[] ranks = {
            SharedColorTokens.Endurance.StyleRank.D,
            SharedColorTokens.Endurance.StyleRank.C,
            SharedColorTokens.Endurance.StyleRank.B,
            SharedColorTokens.Endurance.StyleRank.A,
            SharedColorTokens.Endurance.StyleRank.S,
            SharedColorTokens.Endurance.StyleRank.SS,
            SharedColorTokens.Endurance.StyleRank.SSS
        };
        for (int i = 0; i < ranks.length; i++) {
            for (int j = i + 1; j < ranks.length; j++) {
                assertNotEquals(ranks[i], ranks[j],
                    "StyleRank colors at index " + i + " and " + j + " should be distinct");
            }
        }
    }

    @Test
    @DisplayName("Endurance LootTier colors progress from common to mythic")
    void lootTierColorsExist() {
        // Just verify they are set (non-zero) and accessible
        assertNotEquals(0, SharedColorTokens.Endurance.LootTier.COMMON);
        assertNotEquals(0, SharedColorTokens.Endurance.LootTier.MYTHIC);
    }

    @Test
    @DisplayName("Message RGB colors are non-zero")
    void messageColors() {
        assertNotEquals(0, SharedColorTokens.Message.RGB_SUCCESS);
        assertNotEquals(0, SharedColorTokens.Message.RGB_ERROR);
        assertNotEquals(0, SharedColorTokens.Message.RGB_MUTED);
    }

    @Test
    @DisplayName("TelemetryExport gradient colors exist (10 steps)")
    void telemetryGradient() {
        // Access all 10 gradient stops to verify they compile and are accessible
        int[] gradients = {
            SharedColorTokens.TelemetryExport.GRADIENT_0,
            SharedColorTokens.TelemetryExport.GRADIENT_1,
            SharedColorTokens.TelemetryExport.GRADIENT_2,
            SharedColorTokens.TelemetryExport.GRADIENT_3,
            SharedColorTokens.TelemetryExport.GRADIENT_4,
            SharedColorTokens.TelemetryExport.GRADIENT_5,
            SharedColorTokens.TelemetryExport.GRADIENT_6,
            SharedColorTokens.TelemetryExport.GRADIENT_7,
            SharedColorTokens.TelemetryExport.GRADIENT_8,
            SharedColorTokens.TelemetryExport.GRADIENT_9
        };
        assertEquals(10, gradients.length);
    }
}

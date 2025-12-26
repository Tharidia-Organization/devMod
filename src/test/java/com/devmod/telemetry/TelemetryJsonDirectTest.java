package com.devmod.telemetry;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TelemetryJsonDirectTest {

    @Test
    @DisplayName("Escape handles null and control characters")
    void escapeHandlesNullAndControlCharacters() {
        assertEquals("", TelemetryJson.escape(null));

        String input = "a\"b\\c\n\r\t";
        String expected = "a\\\"b\\\\c\\n\\r\\t";
        assertEquals(expected, TelemetryJson.escape(input));
    }
}

package com.frenkvs.devmod.telemetry;

public final class TelemetryJson {
    private TelemetryJson() {}

    public static String escape(String input) {
        if (input == null) return "";
        StringBuilder sb = new StringBuilder(input.length() + 8);
        for (char c : input.toCharArray()) {
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '"' -> sb.append("\\\"");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }
}

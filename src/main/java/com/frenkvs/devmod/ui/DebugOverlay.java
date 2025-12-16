package com.frenkvs.devmod.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import java.util.Objects;

public class DebugOverlay {
    public enum Mode {
        PERFORMANCE, MEMORY, NETWORK, OFF
    }
    
    private static Mode currentMode = Mode.OFF;
    private static long lastUpdate = 0;
    private static String[] performanceData = new String[5];
    private static String[] memoryData = new String[3];
    private static String[] networkData = new String[3];
    
    public static boolean handleKeyPress(int keyCode) {
        switch (keyCode) {
            case GLFW.GLFW_KEY_F9:
                currentMode = currentMode == Mode.PERFORMANCE ? Mode.OFF : Mode.PERFORMANCE;
                return true;
            case GLFW.GLFW_KEY_F10:
                currentMode = currentMode == Mode.MEMORY ? Mode.OFF : Mode.MEMORY;
                return true;
            case GLFW.GLFW_KEY_F11:
                currentMode = currentMode == Mode.NETWORK ? Mode.OFF : Mode.NETWORK;
                return true;
        }
        return false;
    }
    
    public static void render(GuiGraphics graphics, int screenWidth, int screenHeight) {
        if (currentMode == Mode.OFF) return;
        
        updateData();
        
        int x = 10;
        int y = 10;
        int color = 0xFFFFFF;
        var mc = Minecraft.getInstance();
        if (mc == null || mc.font == null) return;

        var safeFont = Objects.requireNonNull(mc.font);
        graphics.drawString(safeFont, "DEBUG: " + currentMode.name(), x, y, color);
        y += 12;
        
        String[] data = switch (currentMode) {
            case PERFORMANCE -> performanceData;
            case MEMORY -> memoryData;
            case NETWORK -> networkData;
            default -> new String[0];
        };
        
        for (String line : data) {
            if (line != null) {
                graphics.drawString(safeFont, line, x, y, color);
                y += 10;
            }
        }
    }
    
    private static void updateData() {
        var mc = Minecraft.getInstance();
        if (mc == null) return;

        long now = System.currentTimeMillis();
        if (now - lastUpdate < 500) return; // Update every 500ms
        lastUpdate = now;
        
        Runtime runtime = Runtime.getRuntime();
        
        switch (currentMode) {
            case PERFORMANCE:
                var level = mc.level;
                performanceData[0] = "FPS: " + mc.getFps();
                performanceData[1] = "Entities: " + (level != null ? level.getEntityCount() : 0);
                performanceData[2] = "Chunks: n/a";
                break;
            case MEMORY:
                long total = runtime.totalMemory() / 1024 / 1024;
                long free = runtime.freeMemory() / 1024 / 1024;
                long used = total - free;
                memoryData[0] = "Used: " + used + "MB";
                memoryData[1] = "Free: " + free + "MB";
                memoryData[2] = "Total: " + total + "MB";
                break;
            case NETWORK: {
                var conn = mc.getConnection();
                var player = mc.player;
                networkData[0] = "Connection: " + (conn != null ? "OK" : "OFFLINE");
                if (conn != null && player != null) {
                    var info = conn.getPlayerInfo(Objects.requireNonNull(player.getUUID()));
                    networkData[1] = "Ping: " + (info != null ? info.getLatency() + "ms" : "N/A");
                } else {
                    networkData[1] = "Ping: N/A";
                }
                break;
            }
            case OFF:
            default:
                break;
        }
    }
}

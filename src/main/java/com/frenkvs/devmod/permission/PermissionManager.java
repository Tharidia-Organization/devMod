package com.frenkvs.devmod.permission;

import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

public class PermissionManager {
    private static boolean hasPermission = false;
    private static boolean checked = false;
    
    public static boolean hasModPermission(Player player) {
        if (player == null) return false;
        
        // Check server-side
        if (player instanceof ServerPlayer serverPlayer) {
            return serverPlayer.hasPermissions(2); // Changed from 4 to 2
        }
        
        // Check client-side
        if (player.level().isClientSide) {
            // Cache the permission to avoid checking every frame
            if (!checked || Minecraft.getInstance().player != player) {
                hasPermission = player.hasPermissions(2); // Changed from 4 to 2
                checked = true;
            }
            return hasPermission;
        }
        
        return false;
    }
    
    public static void resetCache() {
        checked = false;
        hasPermission = false;
    }
    
    public static boolean isClientOp() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        
        // Temporarily remove caching to debug
        boolean currentPermission = mc.player.hasPermissions(2);
        boolean hasLevel4 = mc.player.hasPermissions(4);
        
        // Temporary bypass for singleplayer - if you're the world owner, allow mod features
        if (mc.hasSingleplayerServer()) {
            return true;
        }
        
        return currentPermission;
    }
}

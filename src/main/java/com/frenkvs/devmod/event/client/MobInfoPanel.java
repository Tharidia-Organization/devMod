package com.frenkvs.devmod.event.client;

import com.frenkvs.devmod.config.ModConfig;
import com.frenkvs.devmod.permission.PermissionManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.gui.Font;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.text.DecimalFormat;

public class MobInfoPanel {
    
    private static final DecimalFormat df = new DecimalFormat("#.##");
    private static final ResourceLocation PANEL_TEXTURE = 
        ResourceLocation.fromNamespaceAndPath("devmod", "textures/gui/mob_panel.png");
    private static final float PANEL_WIDTH = 1.2f; // Increased width
    private static final float PANEL_HEIGHT = 1.2f; // Increased height
    private static final float OFFSET_Y = 1.0f; // Height above mob
    private static final float MAX_DISTANCE = 15.0f; // Max render distance
    
    public static void renderMobInfoPanel(PoseStack poseStack, LivingEntity entity, Vec3 cameraPos, float partialTick) {
        // Check if player has OP level 4 or higher
        if (!PermissionManager.isClientOp()) return;
        
        if (!ModConfig.showAnchors) {
            System.out.println("Mob info panel disabled - showAnchors is false");
            return;
        }
        
        Minecraft mc = Minecraft.getInstance();
        double distance = mc.player.distanceToSqr(entity);
        
        // Don't render if too far away
        if (distance > MAX_DISTANCE * MAX_DISTANCE) return;
        
        // Check if player has line of sight to entity
        if (!mc.player.hasLineOfSight(entity)) return;
        
        System.out.println("Rendering mob info panel for: " + entity.getName().getString());
        
        // Calculate opacity based on distance
        float alpha = 1.0f - (float)(Math.sqrt(distance) / MAX_DISTANCE);
        alpha = Mth.clamp(alpha, 0.3f, 1.0f);
        
        // Get mob position
        Vec3 mobPos = entity.getEyePosition(partialTick).add(0, OFFSET_Y, 0);
        
        // Setup pose stack for world rendering
        poseStack.pushPose();
        
        // Translate to mob position
        poseStack.translate(mobPos.x - cameraPos.x, mobPos.y - cameraPos.y, mobPos.z - cameraPos.z);
        
        // Make panel always face the camera (billboard effect)
        Quaternionf rotation = mc.gameRenderer.getMainCamera().rotation();
        poseStack.mulPose(rotation);
        
        // Scale based on distance - make it bigger to ensure visibility
        float scale = 1.0f; // Increased from 0.5f
        poseStack.scale(scale, scale, scale);
        
        // Calculate animations
        float time = (System.currentTimeMillis() % 2000) / 1000f;
        float pulse = (float) Math.sin(time * Math.PI) * 0.1f + 0.9f;
        
        // Render the panel
        renderSciFiPanel(poseStack, entity, alpha, pulse);
        
        poseStack.popPose();
    }
    
    private static void renderSciFiPanel(PoseStack poseStack, LivingEntity entity, float alpha, float scale) {
        // Setup rendering for panel geometry
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder bufferBuilder = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        
        Matrix4f matrix = poseStack.last().pose();
        
        float halfWidth = PANEL_WIDTH / 2f;
        float halfHeight = PANEL_HEIGHT / 2f;
        
        // Panel colors
        float bgAlpha = alpha * 0.9f;
        float borderAlpha = alpha;
        float glowAlpha = alpha * 0.3f;
        
        // Render background panel
        renderPanelBackground(bufferBuilder, matrix, -halfWidth, -halfHeight, halfWidth, halfHeight, bgAlpha);
        
        // Render animated borders
        renderAnimatedBorders(bufferBuilder, matrix, -halfWidth, -halfHeight, halfWidth, halfHeight, 
                            borderAlpha, glowAlpha, scale);
        
        // Draw the panel geometry
        BufferUploader.drawWithShader(bufferBuilder.buildOrThrow());
        
        // Reset render state for text
        RenderSystem.depthMask(true);
        
        // Render text info
        renderMobInfo(poseStack, entity, alpha);
    }
    
    private static void renderPanelBackground(BufferBuilder builder, Matrix4f matrix,
                                           float x1, float y1, float x2, float y2, float alpha) {
        // Main panel with dark blue color
        builder.addVertex(matrix, x1, y1, 0).setColor(0.1f, 0.1f, 0.18f, alpha);
        builder.addVertex(matrix, x1, y2, 0).setColor(0.08f, 0.08f, 0.14f, alpha);
        builder.addVertex(matrix, x2, y2, 0).setColor(0.08f, 0.08f, 0.14f, alpha);
        builder.addVertex(matrix, x2, y1, 0).setColor(0.1f, 0.1f, 0.18f, alpha);
        
        // Inner frame with subtle blue
        float inset = 0.02f;
        builder.addVertex(matrix, x1 + inset, y1 + inset, 0.01f).setColor(0, 0.5f, 0.8f, alpha * 0.5f);
        builder.addVertex(matrix, x1 + inset, y2 - inset, 0.01f).setColor(0, 0.5f, 0.8f, alpha * 0.5f);
        builder.addVertex(matrix, x2 - inset, y2 - inset, 0.01f).setColor(0, 0.5f, 0.8f, alpha * 0.5f);
        builder.addVertex(matrix, x2 - inset, y1 + inset, 0.01f).setColor(0, 0.5f, 0.8f, alpha * 0.5f);
    }
    
    private static void renderAnimatedBorders(BufferBuilder builder, Matrix4f matrix,
                                            float x1, float y1, float x2, float y2, 
                                            float borderAlpha, float glowAlpha, float pulse) {
        float borderThickness = 0.03f * pulse;
        
        // Cyan border color
        float r = 0, g = 0.667f, b = 1.0f;
        
        // Top border
        renderBorderSegment(builder, matrix, x1, y1, x2, y1 + borderThickness, r, g, b, borderAlpha);
        
        // Bottom border
        renderBorderSegment(builder, matrix, x1, y2 - borderThickness, x2, y2, r, g, b, borderAlpha);
        
        // Left border
        renderBorderSegment(builder, matrix, x1, y1, x1 + borderThickness, y2, r, g, b, borderAlpha);
        
        // Right border
        renderBorderSegment(builder, matrix, x2 - borderThickness, y1, x2, y2, r, g, b, borderAlpha);
        
        // Corner accents with glow
        float accentSize = 0.05f;
        float gr = 0, gg = 1.0f, gb = 1.0f; // Cyan glow
        
        // Top-left corner
        renderCornerAccent(builder, matrix, x1, y1, accentSize, gr, gg, gb, glowAlpha * pulse);
        // Top-right corner
        renderCornerAccent(builder, matrix, x2, y1, accentSize, gr, gg, gb, glowAlpha * pulse);
        // Bottom-left corner
        renderCornerAccent(builder, matrix, x1, y2, accentSize, gr, gg, gb, glowAlpha * pulse);
        // Bottom-right corner
        renderCornerAccent(builder, matrix, x2, y2, accentSize, gr, gg, gb, glowAlpha * pulse);
    }
    
    private static void renderBorderSegment(BufferBuilder builder, Matrix4f matrix,
                                          float x1, float y1, float x2, float y2,
                                          float r, float g, float b, float a) {
        builder.addVertex(matrix, x1, y1, 0.02f).setColor(r, g, b, a);
        builder.addVertex(matrix, x1, y2, 0.02f).setColor(r, g, b, a);
        builder.addVertex(matrix, x2, y2, 0.02f).setColor(r, g, b, a);
        builder.addVertex(matrix, x2, y1, 0.02f).setColor(r, g, b, a);
    }
    
    private static void renderCornerAccent(BufferBuilder builder, Matrix4f matrix,
                                         float x, float y, float size,
                                         float r, float g, float b, float a) {
        // Render a small glowing diamond at corners (4 vertices for QUADS)
        builder.addVertex(matrix, x - size, y, 0.03f).setColor(r, g, b, a);
        builder.addVertex(matrix, x, y - size, 0.03f).setColor(r, g, b, a);
        builder.addVertex(matrix, x + size, y, 0.03f).setColor(r, g, b, a);
        builder.addVertex(matrix, x, y + size, 0.03f).setColor(r, g, b, a);
    }
    
    private static void renderMobInfo(PoseStack poseStack, LivingEntity entity, float alpha) {
        Minecraft mc = Minecraft.getInstance();
        
        // Calculate text position - centered in panel with proper margins
        float textStartX = -PANEL_WIDTH / 2f + 0.15f; // More margin from left
        float textStartY = -PANEL_HEIGHT / 2f + 0.12f; // More margin from top
        float lineHeight = 0.12f; // Reduced line height
        
        // Enable proper text rendering
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        
        // Setup text rendering pose
        poseStack.pushPose();
        poseStack.translate(0, 0, 0.05f); // Slight Z offset for text
        poseStack.scale(0.01f, -0.01f, 0.01f); // Reduced scale back to 0.01f
        
        int currentLine = 0;
        
        // Render mob name at top
        String name = entity.getName().getString();
        int nameColor = ((int)(alpha * 255) << 24) | 0xFFFF00;
        mc.font.drawInBatch(name, (int)(textStartX * 100), (int)((textStartY + lineHeight * currentLine) * 100), nameColor, false, poseStack.last().pose(), 
                          mc.renderBuffers().bufferSource(), Font.DisplayMode.NORMAL, 0, 15728880);
        currentLine++;
        
        // Render HP
        float hp = entity.getHealth();
        float maxHp = entity.getMaxHealth();
        String hpText = "HP: " + df.format(hp) + " / " + df.format(maxHp);
        int hpColor = ((int)(alpha * 255) << 24) | 0xFF5555;
        mc.font.drawInBatch(hpText, (int)(textStartX * 100), (int)((textStartY + lineHeight * currentLine) * 100), hpColor, false, poseStack.last().pose(),
                          mc.renderBuffers().bufferSource(), Font.DisplayMode.NORMAL, 0, 15728880);
        currentLine++;
        
        // Render Armor
        int armor = entity.getArmorValue();
        if (armor > 0) {
            String armorText = "Armor: " + armor + " (-" + (int)(armor * 4.0f) + "%)";
            int armorColor = ((int)(alpha * 255) << 24) | 0x5555FF;
            mc.font.drawInBatch(armorText, (int)(textStartX * 100), (int)((textStartY + lineHeight * currentLine) * 100), armorColor, false, poseStack.last().pose(),
                              mc.renderBuffers().bufferSource(), Font.DisplayMode.NORMAL, 0, 15728880);
            currentLine++;
        }
        
        // Render Damage
        double dmg = 0;
        if (entity.getAttribute(Attributes.ATTACK_DAMAGE) != null) {
            dmg = entity.getAttributeValue(Attributes.ATTACK_DAMAGE);
        }
        if (dmg > 0) {
            String dmgText = "DMG: " + df.format(dmg) + " (" + df.format(dmg/2.0) + " Hearts)";
            int dmgColor = ((int)(alpha * 255) << 24) | 0xFFAAAA;
            mc.font.drawInBatch(dmgText, (int)(textStartX * 100), (int)((textStartY + lineHeight * currentLine) * 100), dmgColor, false, poseStack.last().pose(),
                              mc.renderBuffers().bufferSource(), Font.DisplayMode.NORMAL, 0, 15728880);
            currentLine++;
        }
        
        // Render Vista (Follow Range)
        double follow = 0;
        if (entity.getAttribute(Attributes.FOLLOW_RANGE) != null) {
            follow = entity.getAttributeValue(Attributes.FOLLOW_RANGE);
        }
        String vistaText = "Vista: " + df.format(follow);
        int vistaColor = ((int)(alpha * 255) << 24) | 0x00FF00;
        mc.font.drawInBatch(vistaText, (int)(textStartX * 100), (int)((textStartY + lineHeight * currentLine) * 100), vistaColor, false, poseStack.last().pose(),
                          mc.renderBuffers().bufferSource(), Font.DisplayMode.NORMAL, 0, 15728880);
        currentLine++;
        
        // Render Reach
        double rawReach = 0;
        if (entity.getAttribute(Attributes.ENTITY_INTERACTION_RANGE) != null) {
            rawReach = entity.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE);
        }
        
        String reachText;
        int reachColor;
        if (rawReach > 0) {
            reachText = "Reach (MOD): " + df.format(rawReach);
            reachColor = ((int)(alpha * 255) << 24) | 0xFFFF00;
        } else {
            double estimated = entity.getBbWidth() * 2.0 + 1.0;
            reachText = "Reach (Vanilla): " + df.format(estimated);
            reachColor = ((int)(alpha * 255) << 24) | 0xAAAAAA;
        }
        mc.font.drawInBatch(reachText, (int)(textStartX * 100), (int)((textStartY + lineHeight * currentLine) * 100), reachColor, false, poseStack.last().pose(),
                          mc.renderBuffers().bufferSource(), Font.DisplayMode.NORMAL, 0, 15728880);
        currentLine++;
        
        // Render Target
        String target = "Nessuno";
        if (entity instanceof Mob mob && mob.getTarget() != null) {
            target = mob.getTarget().getName().getString();
        }
        String targetText = "Target: " + target;
        int targetColor = ((int)(alpha * 255) << 24) | 0xFFA500;
        mc.font.drawInBatch(targetText, (int)(textStartX * 100), (int)((textStartY + lineHeight * currentLine) * 100), targetColor, false, poseStack.last().pose(),
                          mc.renderBuffers().bufferSource(), Font.DisplayMode.NORMAL, 0, 15728880);
        
        poseStack.popPose();
        
        // Flush text buffer
        mc.renderBuffers().bufferSource().endBatch();
    }
}

package com.frenkvs.devmod.client.screen;

import com.frenkvs.devmod.config.ModConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import com.mojang.blaze3d.systems.RenderSystem;

public class SettingsScreen extends Screen {

    // Dimensioni del pannello
    private static final int PANEL_WIDTH = 380;
    private static final int PANEL_HEIGHT = 400; // Increased from 300 to prevent text overlap
    
    // Animation time
    private long openTime = System.currentTimeMillis();
    
    // Sci-fi color scheme
    private static final int CYAN_GLOW = 0xFF00FFFF;
    private static final int PURPLE_GLOW = 0xFFAA00FF;
    private static final int PANEL_BG = 0xDD0A0A1A;
    private static final int BORDER_TOP = 0xFF00AAFF;
    private static final int BORDER_BOTTOM = 0xFFAA00FF;

    public SettingsScreen() {
        super(Component.literal("DevMod Control Panel"));
    }

    @Override
    public boolean isPauseScreen() {
        return false; // Permette al gioco di continuare a girare dietro il menu
    }

    @Override
    protected void init() {
        // Calcolo coordinate per centrare il pannello
        int panelLeft = (this.width - PANEL_WIDTH) / 2;
        int panelTop = (this.height - PANEL_HEIGHT) / 2;

        // Margini interni
        int xL = panelLeft + 10;          // Colonna Sinistra
        int xR = panelLeft + PANEL_WIDTH / 2 + 5; // Colonna Destra
        int y = panelTop + 45;            // Inizio verticale (più spazio in alto)
        int btnW = 150;                   // Larghezza bottoni
        int btnH = 18;                    // Altezza bottoni
        int step = 20;                    // Spazio verticale tra bottoni

        // --- COLONNA SINISTRA: VISUALS ---

        // 1. Base Visuals
        this.addRenderableWidget(new SciFiButton(xL, y, btnW, btnH, 
            Component.literal("Overlay HUD: " + (ModConfig.showOverlay ? "§aON" : "§cOFF")), 
            b -> { ModConfig.showOverlay = !ModConfig.showOverlay; b.setMessage(Component.literal("Overlay HUD: " + (ModConfig.showOverlay ? "§aON" : "§cOFF"))); }));
        y += step;
        this.addRenderableWidget(new SciFiButton(xL, y, btnW, btnH, 
            Component.literal("Render Sfere: " + (ModConfig.showRender ? "§aON" : "§cOFF")), 
            b -> { ModConfig.showRender = !ModConfig.showRender; b.setMessage(Component.literal("Render Sfere: " + (ModConfig.showRender ? "§aON" : "§cOFF"))); }));
        y += step;
        this.addRenderableWidget(new SciFiButton(xL, y, btnW, btnH, 
            Component.literal("Sphere Mode: " + (ModConfig.sphereRenderMode == ModConfig.SphereRenderMode.FILLED ? "§bFILLED" : "§eWIREFRAME")), 
            b -> { 
                ModConfig.sphereRenderMode = ModConfig.sphereRenderMode == ModConfig.SphereRenderMode.FILLED ? ModConfig.SphereRenderMode.WIREFRAME : ModConfig.SphereRenderMode.FILLED;
                b.setMessage(Component.literal("Sphere Mode: " + (ModConfig.sphereRenderMode == ModConfig.SphereRenderMode.FILLED ? "§bFILLED" : "§eWIREFRAME"))); 
            }));
        y += step;
        this.addRenderableWidget(new SciFiButton(xL, y, btnW, btnH, 
            Component.translatable("devmod.settings.color", Component.translatable(ModConfig.getColorTranslationKey())), 
            b -> { ModConfig.cycleColor(); b.setMessage(Component.translatable("devmod.settings.color", Component.translatable(ModConfig.getColorTranslationKey()))); }));
        y += step + 8; // Spazietto aumentato

        // NUOVO BOTTONE FRECCE
        this.addRenderableWidget(new SciFiButton(xL, y, btnW, btnH, 
            Component.literal("Show Arrows: " + (ModConfig.showArrowHits ? "§aON" : "§cOFF")), 
            b -> { ModConfig.showArrowHits = !ModConfig.showArrowHits; b.setMessage(Component.literal("Show Arrows: " + (ModConfig.showArrowHits ? "§aON" : "§cOFF"))); }));
        y += step;
        this.addRenderableWidget(new SciFiButton(xL, y, btnW, btnH, 
            Component.literal("Show AI Path: " + (ModConfig.showMobPath ? "§aON" : "§cOFF")), 
            b -> { ModConfig.showMobPath = !ModConfig.showMobPath; b.setMessage(Component.literal("Show AI Path: " + (ModConfig.showMobPath ? "§aON" : "§cOFF"))); }));
        y += step + 5;

        // 2. Anchors & Markers
        this.addRenderableWidget(new SciFiButton(xL, y, btnW, btnH, 
            Component.literal("Name Tags: " + (ModConfig.showAnchors ? "§aON" : "§cOFF")), 
            b -> { ModConfig.showAnchors = !ModConfig.showAnchors; b.setMessage(Component.literal("Name Tags: " + (ModConfig.showAnchors ? "§aON" : "§cOFF"))); }));
        y += step;
        this.addRenderableWidget(new SciFiButton(xL, y, btnW, btnH, 
            Component.literal("Markers: " + (ModConfig.showMarkers ? "§aON" : "§cOFF")), 
            b -> { ModConfig.showMarkers = !ModConfig.showMarkers; b.setMessage(Component.literal("Markers: " + (ModConfig.showMarkers ? "§aON" : "§cOFF"))); }));
        y += step + 8; // Spazio aumentato

        // 3. Line of Sight
        this.addRenderableWidget(new SciFiButton(xL, y, btnW, btnH, 
            Component.literal("Line of Sight: " + (ModConfig.showLoS ? "§aON" : "§cOFF")), 
            b -> { ModConfig.showLoS = !ModConfig.showLoS; b.setMessage(Component.literal("Line of Sight: " + (ModConfig.showLoS ? "§aON" : "§cOFF"))); }));
        y += step;
        this.addRenderableWidget(new SciFiButton(xL, y, btnW, btnH, 
            Component.literal("All Mobs LoS: " + (ModConfig.showAllMobsLoS ? "§aON" : "§cOFF")), 
            b -> { ModConfig.showAllMobsLoS = !ModConfig.showAllMobsLoS; b.setMessage(Component.literal("All Mobs LoS: " + (ModConfig.showAllMobsLoS ? "§aON" : "§cOFF"))); }));
        y += step + 20;
        this.addRenderableWidget(new AllMobsRadiusSlider(xL, y, btnW, btnH, ModConfig.allMobsLoSRadius));
        y += step + 10;
        this.addRenderableWidget(new LoSRangeSlider(xL, y, btnW, btnH, ModConfig.losDistance));


        // --- COLONNA DESTRA: DEBUG & LOGIC ---
        // Resettiamo Y per la colonna destra
        y = panelTop + 35;

        // 1. Logic Debug
        this.addRenderableWidget(new SciFiButton(xR, y, btnW, btnH, 
            Component.literal("Stuck Detector: " + (ModConfig.enableStuckDebug ? "§aON" : "§cOFF")), 
            b -> { ModConfig.enableStuckDebug = !ModConfig.enableStuckDebug; b.setMessage(Component.literal("Stuck Detector: " + (ModConfig.enableStuckDebug ? "§aON" : "§cOFF"))); }));
        y += step;
        this.addRenderableWidget(new SciFiButton(xR, y, btnW, btnH, 
            Component.literal("Debug Chat: " + (ModConfig.showStuckChat ? "§aON" : "§cOFF")), 
            b -> { ModConfig.showStuckChat = !ModConfig.showStuckChat; b.setMessage(Component.literal("Debug Chat: " + (ModConfig.showStuckChat ? "§aON" : "§cOFF"))); }));
        y += step + 20;
        this.addRenderableWidget(new TimeSlider(xR, y, btnW, btnH, ModConfig.stuckThresholdSeconds));
        y += step + 15; // Spazio extra

        // 2. Render Distance
        this.addRenderableWidget(new RenderDistanceSlider(xR, y, btnW, btnH, ModConfig.renderDistanceChunks));
        y += step + 15; // Più spazio prima dei toggle

        // 3. Render Toggles (Aggro/Attack) - Un po' compressi
        int miniBtnW = (btnW - 5) / 2; // Bottoni metà larghezza

        // Riga 1: Friendly
        this.addRenderableWidget(new SciFiButton(xR, y, miniBtnW, btnH, 
            Component.literal("§bF-Aggro"), 
            b -> { ModConfig.renderFriendlyAggro = !ModConfig.renderFriendlyAggro; })
            .setTooltip(Component.literal("Friendly Aggro Range")));
        this.addRenderableWidget(new SciFiButton(xR + miniBtnW + 5, y, miniBtnW, btnH, 
            Component.literal("§bF-Atk"), 
            b -> { ModConfig.renderFriendlyAttack = !ModConfig.renderFriendlyAttack; })
            .setTooltip(Component.literal("Friendly Attack Range")));
        y += step;

        // Riga 2: Hostile
        this.addRenderableWidget(new SciFiButton(xR, y, miniBtnW, btnH, 
            Component.literal("§cH-Aggro"), 
            b -> { ModConfig.renderHostileAggro = !ModConfig.renderHostileAggro; })
            .setTooltip(Component.literal("Hostile Aggro Range")));
        this.addRenderableWidget(new SciFiButton(xR + miniBtnW + 5, y, miniBtnW, btnH, 
            Component.literal("§cH-Atk"), 
            b -> { ModConfig.renderHostileAttack = !ModConfig.renderHostileAttack; })
            .setTooltip(Component.literal("Hostile Attack Range")));


        // --- BOTTONE CHIUDI (IN BASSO AL CENTRO) ---
        int closeW = 100;
        this.addRenderableWidget(new SciFiButton(
            panelLeft + (PANEL_WIDTH - closeW) / 2, 
            panelTop + PANEL_HEIGHT - 45, // Più spazio in basso
            closeW, 22, 
            Component.literal("§l§f[ CLOSE ]"), 
            b -> this.onClose()));
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        
        // Render our panel first
        int panelLeft = (this.width - PANEL_WIDTH) / 2;
        int panelTop = (this.height - PANEL_HEIGHT) / 2;
        
        // Calculate animations
        float time = (System.currentTimeMillis() - openTime) * 0.001f;
        float pulse = (float) Math.sin(time * Math.PI * 2) * 0.5f + 0.5f;
        float glowPulse = (float) Math.sin(time * Math.PI * 3) * 0.3f + 0.7f;
        
        // Render panel with sci-fi styling
        renderHolographicPanel(guiGraphics, panelLeft, panelTop, pulse, glowPulse);
        renderAnimatedBorders(guiGraphics, panelLeft, panelTop, time, pulse);
        
        // Render text
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        renderSciFiTitle(guiGraphics, panelLeft, panelTop, time, pulse);
        renderSectionHeaders(guiGraphics, panelLeft, panelTop, pulse);
        
        // Now render buttons on top of panel
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }
    
    private void renderHolographicPanel(GuiGraphics guiGraphics, int x, int y, float pulse, float glow) {
        // Main panel with dark sci-fi background
        guiGraphics.fill(x, y, x + PANEL_WIDTH, y + PANEL_HEIGHT, 0xFF1A1A2E);
        
        // Inner border with subtle cyan glow
        guiGraphics.fill(x + 1, y + 1, x + PANEL_WIDTH - 1, y + 2, 0xFF16213E);
        guiGraphics.fill(x + 1, y + PANEL_HEIGHT - 2, x + PANEL_WIDTH - 1, y + PANEL_HEIGHT - 1, 0xFF16213E);
        guiGraphics.fill(x + 1, y + 1, x + 2, y + PANEL_HEIGHT - 1, 0xFF16213E);
        guiGraphics.fill(x + PANEL_WIDTH - 2, y + 1, x + PANEL_WIDTH - 1, y + PANEL_HEIGHT - 1, 0xFF16213E);
    }
    
    private void renderAnimatedBorders(GuiGraphics guiGraphics, int x, int y, float time, float pulse) {
        // Render solid borders without any transparency to eliminate blur
        int borderColor = 0xFF00AAFF;
        
        // Simple solid borders - no animations or transparency
        guiGraphics.fill(x, y, x + PANEL_WIDTH, y + 2, borderColor);
        guiGraphics.fill(x, y + PANEL_HEIGHT - 2, x + PANEL_WIDTH, y + PANEL_HEIGHT, borderColor);
        guiGraphics.fill(x, y, x + 2, y + PANEL_HEIGHT, borderColor);
        guiGraphics.fill(x + PANEL_WIDTH - 2, y, x + PANEL_WIDTH, y + PANEL_HEIGHT, borderColor);
    }
    
    private void renderSciFiTitle(GuiGraphics guiGraphics, int x, int y, float time, float pulse) {
        // Reset render color
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        
        // Animated title with glow effect
        String title = "§l§f[ QUANTUM CONTROL PANEL ]";
        int titleWidth = font.width(title);
        int titleX = x + (PANEL_WIDTH - titleWidth) / 2;
        int titleY = y + 8;
        
        // Draw glow behind text
        for (int i = 0; i < 3; i++) {
            float glowAlpha = (0.3f - i * 0.1f) * pulse;
            int glowColor = ((int)(glowAlpha * 255) << 24) | 0x00AAFF;
            guiGraphics.drawString(font, title, titleX - i, titleY, glowColor, false);
            guiGraphics.drawString(font, title, titleX + i, titleY, glowColor, false);
            guiGraphics.drawString(font, title, titleX, titleY - i, glowColor, false);
            guiGraphics.drawString(font, title, titleX, titleY + i, glowColor, false);
        }
        
        // Main title
        guiGraphics.drawString(font, title, titleX, titleY, 0xFFFFFFFF, false);
        
        // Subtitle with animation
        float subtitlePhase = (float) Math.sin(time * 4);
        String subtitle = String.format("§7§oSystem Status: %s", subtitlePhase > 0 ? "ONLINE" : "SCANNING");
        int subtitleWidth = font.width(subtitle);
        guiGraphics.drawString(font, subtitle, x + (PANEL_WIDTH - subtitleWidth) / 2, titleY + 15, 0xFF00AAFF, false);
    }
    
    private void renderSectionHeaders(GuiGraphics guiGraphics, int x, int y, float pulse) {
        // Left section header
        String leftHeader = "§b§l[ VISUAL SYSTEMS ]";
        guiGraphics.drawString(font, leftHeader, x + 15, y + 25 - 10, 0xFF00FFFF, false);
        
        // Draw hexagonal icon
        renderHexIcon(guiGraphics, x + PANEL_WIDTH / 4 - 30, y + 25 - 10, 0xFF00FFFF, pulse);
        
        // Right section header
        String rightHeader = "§d§l[ DEBUG PROTOCOLS ]";
        guiGraphics.drawString(font, rightHeader, x + PANEL_WIDTH / 2 + 10, y + 25 - 10, 0xFFAA00FF, false);
        
        // Draw circuit icon
        renderCircuitIcon(guiGraphics, x + PANEL_WIDTH * 3 / 4 - 30, y + 25 - 10, 0xFFAA00FF, pulse);
    }
    
    private void renderHexIcon(GuiGraphics guiGraphics, int x, int y, int color, float pulse) {
        float size = 4 + 2 * pulse;
        for (int i = 0; i < 6; i++) {
            float angle1 = (float) Math.toRadians(i * 60);
            float angle2 = (float) Math.toRadians((i + 1) * 60);
            
            int x1 = (int) (x + Math.cos(angle1) * size);
            int y1 = (int) (y + Math.sin(angle1) * size);
            int x2 = (int) (x + Math.cos(angle2) * size);
            int y2 = (int) (y + Math.sin(angle2) * size);
            
            guiGraphics.fill(x, y, x1, y1, color);
            guiGraphics.fill(x, y, x2, y2, color);
        }
    }
    
    private void renderCircuitIcon(GuiGraphics guiGraphics, int x, int y, int color, float pulse) {
        // Simple circuit pattern
        int alpha = (int) (255 * pulse);
        int circuitColor = (alpha << 24) | (color & 0x00FFFFFF);
        
        guiGraphics.fill(x - 4, y, x + 4, y + 1, circuitColor);
        guiGraphics.fill(x - 4, y + 4, x + 4, y + 5, circuitColor);
        guiGraphics.fill(x - 4, y, x - 3, y + 5, circuitColor);
        guiGraphics.fill(x + 3, y, x + 4, y + 5, circuitColor);
        guiGraphics.fill(x - 1, y + 2, x + 1, y + 3, circuitColor);
    }
    
    private void renderScanningOverlay(GuiGraphics guiGraphics, int x, int y, float time) {
        // Ensure proper render state
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        
        // Vertical scanning line - more subtle
        float scanPos = (time * 0.5f) % 1.0f;
        int scanY = y + (int) (scanPos * PANEL_HEIGHT);
        int scanAlpha = (int) (50 * (1.0f - Math.abs(scanPos - 0.5f) * 2)); // Reduced alpha
        int scanColor = (scanAlpha << 24) | 0x00FFFF;
        
        guiGraphics.fill(x, scanY - 1, x + PANEL_WIDTH, scanY + 1, scanColor);
        
        // Corner accents - reduced intensity
        renderCornerAccent(guiGraphics, x, y, time, true);
        renderCornerAccent(guiGraphics, x + PANEL_WIDTH, y, time, false);
        renderCornerAccent(guiGraphics, x, y + PANEL_HEIGHT, time, false);
        renderCornerAccent(guiGraphics, x + PANEL_WIDTH, y + PANEL_HEIGHT, time, true);
    }
    
    private void renderCornerAccent(GuiGraphics guiGraphics, int x, int y, float time, boolean topRight) {
        float pulse = (float) Math.sin(time * 3 + (topRight ? 0 : Math.PI)) * 0.5f + 0.5f;
        int size = (int) (8 + 3 * pulse); // Smaller size
        int alpha = (int) (100 * pulse); // Reduced alpha
        int color = (alpha << 24) | 0x00AAFF;
        
        // Draw L-shaped corner
        if (topRight) {
            guiGraphics.fill(x - size, y, x, y + 1, color);
            guiGraphics.fill(x - 1, y, x, y + size, color);
        } else {
            guiGraphics.fill(x, y, x + size, y + 1, color);
            guiGraphics.fill(x, y, x + 1, y + size, color);
        }
    }

    // =================================================================================
    // CUSTOM SCI-FI COMPONENTS
    // =================================================================================
    
    // Custom Sci-Fi Button with hover effects and gradient styling
    private static class SciFiButton extends Button {
        private float hoverAnimation = 0;
        private long lastHoverTime = 0;
        
        public SciFiButton(int x, int y, int width, int height, Component message, OnPress onPress) {
            super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
        }
        
        public SciFiButton setTooltip(Component tooltip) {
            this.setTooltip(net.minecraft.client.gui.components.Tooltip.create(tooltip));
            return this;
        }
        
        @Override
        public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            // Update hover animation
            boolean isHovered = isHovered();
            long currentTime = System.currentTimeMillis();
            float deltaTime = (currentTime - lastHoverTime) * 0.001f;
            lastHoverTime = currentTime;
            
            if (isHovered) {
                hoverAnimation = Math.min(1.0f, hoverAnimation + deltaTime * 4);
            } else {
                hoverAnimation = Math.max(0.0f, hoverAnimation - deltaTime * 4);
            }
            
            // Calculate colors based on hover state
            float pulse = (float) Math.sin(currentTime * 0.003) * 0.5f + 0.5f;
            
            // Background gradient
            int topColor = lerpColor(0xDD0A0A2A, 0xDD1A1A3A, hoverAnimation);
            int bottomColor = lerpColor(0xDD050515, 0xDD151525, hoverAnimation);
            
            // Draw button background with gradient
            int gradientSteps = 5;
            for (int i = 0; i < gradientSteps; i++) {
                float progress = i / (float) gradientSteps;
                int color = lerpColor(topColor, bottomColor, progress);
                int y = this.getY() + i * (this.height / gradientSteps);
                int h = (this.height / gradientSteps) + 1;
                guiGraphics.fill(this.getX(), y, this.getX() + this.width, y + h, color);
            }
            
            // Draw glowing borders
            int borderColor = lerpColor(0xFF006666, 0xFF00AAAA, hoverAnimation);
            int glowColor = lerpColor(0x33006666, 0x3300AAAA, hoverAnimation);
            
            // Border glow effect
            for (int i = 0; i < 3; i++) {
                int alpha = (int) ((3 - i) * 0.1f * 255 * (hoverAnimation + 0.2f));
                int glow = (alpha << 24) | (borderColor & 0x00FFFFFF);
                guiGraphics.fill(this.getX() - i, this.getY() - i, this.getX() + this.width + i, this.getY() + this.height + i, glow);
            }
            
            // Main border
            guiGraphics.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + 1, borderColor);
            guiGraphics.fill(this.getX(), this.getY() + this.height - 1, this.getX() + this.width, this.getY() + this.height, borderColor);
            guiGraphics.fill(this.getX(), this.getY(), this.getX() + 1, this.getY() + this.height, borderColor);
            guiGraphics.fill(this.getX() + this.width - 1, this.getY(), this.getX() + this.width, this.getY() + this.height, borderColor);
            
            // Corner accents
            if (hoverAnimation > 0.5f) {
                int accentColor = 0xFF00FFFF;
                int accentSize = (int) (3 * (hoverAnimation - 0.5f) * 2);
                guiGraphics.fill(this.getX(), this.getY(), this.getX() + accentSize, this.getY() + 1, accentColor);
                guiGraphics.fill(this.getX() + this.width - accentSize, this.getY(), this.getX() + this.width, this.getY() + 1, accentColor);
                guiGraphics.fill(this.getX(), this.getY() + this.height - 1, this.getX() + accentSize, this.getY() + this.height, accentColor);
                guiGraphics.fill(this.getX() + this.width - accentSize, this.getY() + this.height - 1, this.getX() + this.width, this.getY() + this.height, accentColor);
            }
            
            // Draw text with shadow effect
            // Reset render states to ensure text is in focus
            RenderSystem.disableBlend();
            RenderSystem.enableDepthTest();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            
            // Text shadow
            int textColor = isHovered ? 0xFFFFFFFF : 0xFFCCCCCC;
            int shadowColor = 0x66000000;
            
            // Use Minecraft.getInstance().font for static inner classes
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            guiGraphics.drawString(mc.font, this.getMessage(), this.getX() + (this.width - mc.font.width(this.getMessage())) / 2 + 1, 
                                  this.getY() + (this.height - mc.font.lineHeight) / 2 + 1, shadowColor, false);
            
            // Main text
            guiGraphics.drawString(mc.font, this.getMessage(), this.getX() + (this.width - mc.font.width(this.getMessage())) / 2, 
                                  this.getY() + (this.height - mc.font.lineHeight) / 2, textColor, false);
            
            // Re-enable blend for other effects
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
        }
        
        private int lerpColor(int color1, int color2, float t) {
            int a1 = (color1 >> 24) & 0xFF;
            int r1 = (color1 >> 16) & 0xFF;
            int g1 = (color1 >> 8) & 0xFF;
            int b1 = color1 & 0xFF;
            
            int a2 = (color2 >> 24) & 0xFF;
            int r2 = (color2 >> 16) & 0xFF;
            int g2 = (color2 >> 8) & 0xFF;
            int b2 = color2 & 0xFF;
            
            int a = (int) (a1 + (a2 - a1) * t);
            int r = (int) (r1 + (r2 - r1) * t);
            int g = (int) (g1 + (g2 - g1) * t);
            int b = (int) (b1 + (b2 - b1) * t);
            
            return (a << 24) | (r << 16) | (g << 8) | b;
        }
    }
    
    // Custom Sci-Fi Slider with enhanced visual effects
    private static class SciFiSlider extends AbstractSliderButton {
        private final SliderValueApplier valueApplier;
        private float glowAnimation = 0;
        
        public SciFiSlider(int x, int y, int width, int height, Component message, double initialValue) {
            super(x, y, width, height, message, initialValue);
            this.valueApplier = null;
        }
        
        public SciFiSlider(int x, int y, int width, int height, Component message, double initialValue, SliderValueApplier applier) {
            super(x, y, width, height, message, initialValue);
            this.valueApplier = applier;
        }
        
        @Override
        protected void updateMessage() {
            // Message is updated externally
        }
        
        @Override
        protected void applyValue() {
            // Always update the message first
            this.updateMessage();
            
            // Then apply the value if we have an applier
            if (valueApplier != null) {
                valueApplier.applyValue(this.value);
            }
        }
        
        @Override
        public void onDrag(double mouseX, double mouseY, double dragX, double dragY) {
            // Call the parent drag behavior which should update the value
            super.onDrag(mouseX, mouseY, dragX, dragY);
            // Ensure applyValue is called during drag for immediate updates
            this.applyValue();
        }
        
        @Override
        public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            // Update animations
            boolean isHovered = isHovered();
            long currentTime = System.currentTimeMillis();
            float time = currentTime * 0.001f;
            float pulse = (float) Math.sin(time * Math.PI * 2) * 0.5f + 0.5f;
            
            if (isHovered) {
                glowAnimation = Math.min(1.0f, glowAnimation + 0.1f);
            } else {
                glowAnimation = Math.max(0.0f, glowAnimation - 0.05f);
            }
            
            // Draw slider track
            int trackY = this.getY() + this.height / 2 - 2;
            
            // Background track
            guiGraphics.fill(this.getX(), trackY, this.getX() + this.width, trackY + 4, 0xFF0A0A1A);
            
            // Animated track segments
            int segments = 20;
            for (int i = 0; i < segments; i++) {
                float segmentProgress = i / (float) segments;
                float segmentPulse = (float) Math.sin(time * 3 + i * 0.5f) * 0.5f + 0.5f;
                
                int segmentX = this.getX() + i * (this.width / segments);
                int segmentWidth = (this.width / segments) + 1;
                
                if (segmentProgress <= this.value) {
                    int color = lerpColor(0xFF006666, 0xFF00FFFF, segmentPulse);
                    guiGraphics.fill(segmentX, trackY, segmentX + segmentWidth, trackY + 4, color);
                } else {
                    int color = 0xFF333333;
                    guiGraphics.fill(segmentX, trackY, segmentX + segmentWidth, trackY + 4, color);
                }
            }
            
            // Draw slider handle
            int handleX = this.getX() + (int) (this.value * (this.width - 8));
            int handleY = this.getY() + 2;
            int handleWidth = 8;
            int handleHeight = this.height - 4;
            
            // Handle glow
            for (int i = 3; i > 0; i--) {
                int glowAlpha = (int) (50 * glowAnimation * (i / 3f));
                int glowColor = (glowAlpha << 24) | 0x00AAFF;
                guiGraphics.fill(handleX - i, handleY - i, handleX + handleWidth + i, handleY + handleHeight + i, glowColor);
            }
            
            // Main handle with gradient
            for (int i = 0; i < handleHeight; i++) {
                float progress = i / (float) handleHeight;
                int color = lerpColor(0xFF00AAFF, 0xFF00FFFF, progress);
                guiGraphics.fill(handleX, handleY + i, handleX + handleWidth, handleY + i + 1, color);
            }
            
            // Handle borders
            guiGraphics.fill(handleX, handleY, handleX + handleWidth, handleY + 1, 0xFFFFFFFF);
            guiGraphics.fill(handleX, handleY + handleHeight - 1, handleX + handleWidth, handleY + handleHeight, 0xFFFFFFFF);
            guiGraphics.fill(handleX, handleY, handleX + 1, handleY + handleHeight, 0xFFFFFFFF);
            guiGraphics.fill(handleX + handleWidth - 1, handleY, handleX + handleWidth, handleY + handleHeight, 0xFFFFFFFF);
            
            // Draw text
            // Reset render states to ensure text is in focus
            RenderSystem.disableBlend();
            RenderSystem.enableDepthTest();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            
            int textColor = isHovered ? 0xFFFFFFFF : 0xFFCCCCCC;
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            guiGraphics.drawString(mc.font, this.getMessage(), this.getX(), this.getY() - 10, textColor, false);
            
            // Re-enable blend for other effects
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
        }
        
        private int lerpColor(int color1, int color2, float t) {
            int r1 = (color1 >> 16) & 0xFF;
            int g1 = (color1 >> 8) & 0xFF;
            int b1 = color1 & 0xFF;
            
            int r2 = (color2 >> 16) & 0xFF;
            int g2 = (color2 >> 8) & 0xFF;
            int b2 = color2 & 0xFF;
            
            int r = (int) (r1 + (r2 - r1) * t);
            int g = (int) (g1 + (g2 - g1) * t);
            int b = (int) (b1 + (b2 - b1) * t);
            
            return 0xFF000000 | (r << 16) | (g << 8) | b;
        }
        
        @FunctionalInterface
        private interface SliderValueApplier {
            void applyValue(double value);
        }
    }
    
    // =================================================================================
    // CLASSI INTERNE (SLIDERS)
    // =================================================================================

    // 1. Slider Distanza Render
    private static class RenderDistanceSlider extends SciFiSlider {
        public RenderDistanceSlider(int x, int y, int width, int height, int initialValue) { 
            super(x, y, width, height, Component.literal("Render Dist: " + initialValue + " chunks"), (initialValue - 1) / 9.0, value -> {
                ModConfig.renderDistanceChunks = (int) (value * 9) + 1;
            });
        }
        @Override protected void updateMessage() { 
            int value = (int) (this.value * 9) + 1; 
            this.setMessage(Component.literal("Render Dist: " + value + " chunks")); 
        }
    }

    // 2. Slider Tempo Stuck (1-10s)
    private static class TimeSlider extends SciFiSlider {
        public TimeSlider(int x, int y, int width, int height, int initialVal) { 
            super(x, y, width, height, Component.literal("Stuck Time: " + initialVal + "s"), (initialVal - 1) / 9.0, value -> {
                ModConfig.stuckThresholdSeconds = (int)(value * 9) + 1;
            });
        }
        @Override protected void updateMessage() { 
            int val = (int)(this.value * 9) + 1; 
            this.setMessage(Component.literal("Stuck Time: " + val + "s")); 
        }
    }

    // 3. Slider Distanza LoS (5-64 blocchi)
    private static class LoSRangeSlider extends SciFiSlider {
        public LoSRangeSlider(int x, int y, int width, int height, int initialVal) {
            super(x, y, width, height, Component.literal("LoS Ray Len: " + initialVal), (initialVal - 5) / 59.0, value -> {
                ModConfig.losDistance = (int)(value * 59) + 5;
            });
        }
        @Override protected void updateMessage() {
            int val = (int)(this.value * 59) + 5;
            this.setMessage(Component.literal("LoS Ray Len: " + val));
        }
    }

    // 4. Slider Raggio Attivazione All Mobs (1-100 blocchi)
    private static class AllMobsRadiusSlider extends SciFiSlider {
        public AllMobsRadiusSlider(int x, int y, int width, int height, int initialVal) {
            super(x, y, width, height, Component.literal("Scan Radius: " + initialVal), (initialVal - 1) / 99.0, value -> {
                ModConfig.allMobsLoSRadius = (int)(value * 99) + 1;
            });
        }
        @Override protected void updateMessage() {
            int val = (int)(this.value * 99) + 1;
            this.setMessage(Component.literal("Scan Radius: " + val));
        }
    }
}
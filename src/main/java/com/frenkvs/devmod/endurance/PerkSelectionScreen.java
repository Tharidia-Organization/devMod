package com.frenkvs.devmod.endurance;

import com.frenkvs.devmod.ui.UIConstants;
import com.frenkvs.devmod.util.I18n;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Perk selection screen shown after completing a wave.
 * Displays 3 perk choices with tier colors, descriptions, and stack info.
 * Uses standard UIConstants for consistent theming.
 */
@OnlyIn(Dist.CLIENT)
@SuppressWarnings("null")
public class PerkSelectionScreen extends Screen {

    // === Colors - Standardized to UIConstants ===
    private static final int COLOR_BG = UIConstants.Background.SCREEN;
    private static final int COLOR_PANEL_BG = UIConstants.Background.PANEL;
    private static final int COLOR_CARD_BG = UIConstants.Background.INPUT;
    private static final int COLOR_CARD_HOVER = UIConstants.Background.HOVER;
    private static final int COLOR_CARD_SELECTED = UIConstants.Background.ACTIVE;
    private static final int COLOR_BORDER = UIConstants.Border.DEFAULT;  // Blue instead of purple
    private static final int COLOR_TEXT = UIConstants.Text.PRIMARY;
    private static final int COLOR_TEXT_DIM = UIConstants.Text.SECONDARY;
    private static final int COLOR_ACCENT = UIConstants.Accent.BLUE;  // Blue instead of purple

    // === Dimensions ===
    private static final int CARD_WIDTH = 200;
    private static final int CARD_HEIGHT = 220;  // Increased from 180 to fit longer descriptions
    private static final int CARD_SPACING = 20;
    private static final int MAX_DESCRIPTION_LINES = 6;  // Allow more description lines

    // === Animation ===
    private static final long FADE_IN_DURATION = 400;
    private static final long CARD_STAGGER = 150;

    // === Data ===
    private final int waveNumber;
    private final List<PerkChoicesPayload.PerkChoice> choices;
    private int selectedIndex = -1;
    private int hoveredIndex = -1;  // For comparison panel

    // === State ===
    private long openTime;
    private boolean soundPlayed = false;
    private final List<Button> perkButtons = new ArrayList<>();
    private boolean showComparisonPanel = true;  // Show side comparison

    public PerkSelectionScreen(int waveNumber, List<PerkChoicesPayload.PerkChoice> choices) {
        super(I18n.ui("perk.choose_perk"));
        this.waveNumber = waveNumber;
        this.choices = choices;
    }

    @Override
    protected void init() {
        super.init();
        openTime = System.currentTimeMillis();
        perkButtons.clear();

        int totalWidth = choices.size() * CARD_WIDTH + (choices.size() - 1) * CARD_SPACING;
        int startX = (width - totalWidth) / 2;
        int cardY = height / 2 - CARD_HEIGHT / 2 + 20;

        for (int i = 0; i < choices.size(); i++) {
            final int index = i;
            int cardX = startX + i * (CARD_WIDTH + CARD_SPACING);

            Button btn = Button.builder(
                    I18n.ui("perk.select"),
                    b -> selectPerk(index))
                .bounds(cardX + 25, cardY + CARD_HEIGHT - 35, CARD_WIDTH - 50, 25)
                .build();
            btn.visible = false;
            addRenderableWidget(btn);
            perkButtons.add(btn);
        }

        // Skip button (bottom)
        addRenderableWidget(Button.builder(
                I18n.ui("perk.skip"),
                b -> skipPerk())
            .bounds(width / 2 - 60, height - 50, 120, 25)
            .build());
    }

    @Override
    public void tick() {
        super.tick();

        long elapsed = System.currentTimeMillis() - openTime;

        // Show buttons after animation
        for (int i = 0; i < perkButtons.size(); i++) {
            long cardDelay = FADE_IN_DURATION + i * CARD_STAGGER + 200;
            perkButtons.get(i).visible = elapsed > cardDelay;
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        long elapsed = System.currentTimeMillis() - openTime;
        float fadeProgress = Math.min(1.0f, elapsed / (float) FADE_IN_DURATION);

        // Play sound
        if (!soundPlayed && elapsed > 100 && minecraft != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_TOAST_IN, 1.0f));
            soundPlayed = true;
        }

        // Background
        int bgAlpha = (int) (0xEE * fadeProgress);
        graphics.fill(0, 0, width, height, (bgAlpha << 24) | (UIConstants.Background.SCREEN & 0x00FFFFFF));

        // Title
        if (fadeProgress > 0.3f) {
            float titleAlpha = (fadeProgress - 0.3f) / 0.7f;
            String title = I18n.translate("devmod.endurance.wave_complete", waveNumber).getString();
            int titleColor = applyAlpha(COLOR_ACCENT, titleAlpha);
            graphics.drawCenteredString(font, title, width / 2, 40, titleColor);
        }

        // Perk cards
        int totalWidth = choices.size() * CARD_WIDTH + (choices.size() - 1) * CARD_SPACING;
        int startX = (width - totalWidth) / 2;
        int cardY = height / 2 - CARD_HEIGHT / 2 + 20;

        // Track hovered card for comparison panel
        hoveredIndex = -1;

        for (int i = 0; i < choices.size(); i++) {
            long cardDelay = FADE_IN_DURATION + i * CARD_STAGGER;
            if (elapsed > cardDelay) {
                float cardProgress = Math.min(1.0f, (elapsed - cardDelay) / 300f);
                int cardX = startX + i * (CARD_WIDTH + CARD_SPACING);

                boolean isHovered = mouseX >= cardX && mouseX <= cardX + CARD_WIDTH
                    && mouseY >= cardY && mouseY <= cardY + CARD_HEIGHT;
                boolean isSelected = selectedIndex == i;

                if (isHovered) {
                    hoveredIndex = i;
                }

                renderPerkCard(graphics, choices.get(i), cardX, cardY, cardProgress, isHovered, isSelected);
            }
        }

        // Render comparison panel when hovering
        if (showComparisonPanel && hoveredIndex >= 0 && fadeProgress > 0.5f) {
            renderComparisonPanel(graphics, hoveredIndex, fadeProgress);
        }

        // Keybind hints
        if (fadeProgress > 0.8f) {
            graphics.drawCenteredString(font, I18n.translate("devmod.perk.keybind_hint").getString(),
                width / 2, height - 25, applyAlpha(UIConstants.Text.MUTED, fadeProgress));
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderPerkCard(GuiGraphics g, PerkChoicesPayload.PerkChoice perk,
                                 int x, int y, float alpha, boolean hovered, boolean selected) {
        // Card background with animation
        float scale = 0.9f + 0.1f * alpha;
        if (hovered) scale = 1.02f;
        if (selected) scale = 1.05f;

        int cardW = (int) (CARD_WIDTH * scale);
        int cardH = (int) (CARD_HEIGHT * scale);
        int cardX = x + (CARD_WIDTH - cardW) / 2;
        int cardY = y + (CARD_HEIGHT - cardH) / 2;

        // Background
        int bgColor = selected ? COLOR_CARD_SELECTED : (hovered ? COLOR_CARD_HOVER : COLOR_CARD_BG);
        g.fill(cardX, cardY, cardX + cardW, cardY + cardH, applyAlpha(bgColor, alpha));

        // Border with tier color
        int borderColor = applyAlpha(perk.tierColor() | 0xFF000000, alpha);
        g.fill(cardX, cardY, cardX + cardW, cardY + 3, borderColor);
        g.fill(cardX, cardY + cardH - 3, cardX + cardW, cardY + cardH, borderColor);
        g.fill(cardX, cardY, cardX + 3, cardY + cardH, borderColor);
        g.fill(cardX + cardW - 3, cardY, cardX + cardW, cardY + cardH, borderColor);

        int textY = cardY + 12;

        // Tier badge
        String tierText = perk.tierName();
        int tierBadgeW = font.width(tierText) + 10;
        g.fill(cardX + cardW - tierBadgeW - 8, textY - 2, cardX + cardW - 8, textY + 12,
            applyAlpha(perk.tierColor() | 0xFF000000, alpha * 0.8f));
        g.drawString(font, tierText, cardX + cardW - tierBadgeW - 3, textY, applyAlpha(COLOR_TEXT, alpha));

        // Perk name
        g.drawString(font, perk.name(), cardX + 10, textY, applyAlpha(COLOR_TEXT, alpha));
        textY += 20;

        // Category
        g.drawString(font, perk.categoryName(), cardX + 10, textY, applyAlpha(perk.categoryColor() | 0xFF000000, alpha));
        textY += 16;

        // Description (word wrap) - limit to MAX_DESCRIPTION_LINES
        List<String> lines = wrapText(perk.description(), cardW - 20);
        int linesRendered = 0;
        for (String line : lines) {
            if (linesRendered >= MAX_DESCRIPTION_LINES || textY > cardY + cardH - 55) break;
            g.drawString(font, line, cardX + 10, textY, applyAlpha(COLOR_TEXT_DIM, alpha));
            textY += 11;
            linesRendered++;
        }

        // Stack info
        if (perk.stackable()) {
            textY = cardY + cardH - 50;
            String stackText = I18n.translate("devmod.perk.stacks", perk.currentStacks(), perk.maxStacks()).getString();
            g.drawString(font, stackText, cardX + 10, textY, applyAlpha(COLOR_ACCENT, alpha));
        }

        // Comparison highlight when hovered - show key stat
        if (hovered && !perk.description().isEmpty()) {
            // Draw comparison arrow hint
            int hintY = cardY + cardH - 35;
            String hintText = "▲ " + getCompactStatHint(perk);
            int hintColor = applyAlpha(UIConstants.Accent.GREEN, alpha);
            g.drawString(font, hintText, cardX + 10, hintY - 12, hintColor);
        }
    }

    /**
     * Get a compact stat hint for perk comparison (e.g., "+20% Damage", "+50 HP")
     */
    private String getCompactStatHint(PerkChoicesPayload.PerkChoice perk) {
        String desc = perk.description().toLowerCase();
        // Parse common patterns from description
        if (desc.contains("damage")) return I18n.translate("devmod.perk.stat.damage").getString();
        if (desc.contains("health") || desc.contains("hp")) return I18n.translate("devmod.perk.stat.health").getString();
        if (desc.contains("speed")) return I18n.translate("devmod.perk.stat.speed").getString();
        if (desc.contains("armor") || desc.contains("defense")) return I18n.translate("devmod.perk.stat.defense").getString();
        if (desc.contains("heal") || desc.contains("regen")) return I18n.translate("devmod.perk.stat.healing").getString();
        if (desc.contains("crit")) return I18n.translate("devmod.perk.stat.crit").getString();
        if (desc.contains("lifesteal") || desc.contains("leech")) return I18n.translate("devmod.perk.stat.lifesteal").getString();
        if (desc.contains("aoe") || desc.contains("area")) return I18n.translate("devmod.perk.stat.aoe").getString();
        if (desc.contains("cooldown")) return I18n.translate("devmod.perk.stat.cooldown").getString();
        return perk.categoryName();
    }

    /**
     * Render a comparison panel showing all perks side-by-side with key stats.
     * Highlights the currently hovered perk.
     */
    private void renderComparisonPanel(GuiGraphics g, int hoveredIdx, float alpha) {
        int panelW = 280;
        int panelH = 20 + choices.size() * 22;
        int panelX = width - panelW - 15;
        int panelY = 60;

        // Panel background
        g.fill(panelX, panelY, panelX + panelW, panelY + panelH, applyAlpha(UIConstants.Background.PANEL, alpha));

        // Panel border
        int borderColor = applyAlpha(COLOR_BORDER, alpha);
        g.fill(panelX, panelY, panelX + panelW, panelY + 2, borderColor);
        g.fill(panelX, panelY + panelH - 2, panelX + panelW, panelY + panelH, borderColor);
        g.fill(panelX, panelY, panelX + 2, panelY + panelH, borderColor);
        g.fill(panelX + panelW - 2, panelY, panelX + panelW, panelY + panelH, borderColor);

        // Title
        g.drawString(font, I18n.translate("devmod.perk.quick_compare").getString(), panelX + 8, panelY + 6, applyAlpha(COLOR_ACCENT, alpha));

        // List all perks with their key stat
        int lineY = panelY + 22;
        for (int i = 0; i < choices.size(); i++) {
            PerkChoicesPayload.PerkChoice perk = choices.get(i);
            boolean isHovered = (i == hoveredIdx);

            // Highlight bar for hovered
            if (isHovered) {
                g.fill(panelX + 4, lineY - 2, panelX + panelW - 4, lineY + 16, applyAlpha(UIConstants.Background.HOVER, alpha));
            }

            // Tier indicator (colored dot)
            int dotColor = applyAlpha(perk.tierColor() | 0xFF000000, alpha);
            g.fill(panelX + 8, lineY + 3, panelX + 14, lineY + 9, dotColor);

            // Perk name (truncated)
            String name = perk.name();
            if (font.width(name) > 100) {
                while (font.width(name + "..") > 100 && name.length() > 3) {
                    name = name.substring(0, name.length() - 1);
                }
                name += "..";
            }
            int nameColor = isHovered ? applyAlpha(COLOR_TEXT, alpha) : applyAlpha(COLOR_TEXT_DIM, alpha);
            g.drawString(font, name, panelX + 18, lineY, nameColor);

            // Key stat hint (right-aligned)
            String statHint = getCompactStatHint(perk);
            int statColor = applyAlpha(getCategoryStatColor(perk), alpha);
            int statX = panelX + panelW - font.width(statHint) - 10;
            g.drawString(font, statHint, statX, lineY, statColor);

            // Stack indicator if applicable
            if (perk.stackable() && perk.currentStacks() > 0) {
                String stackStr = "x" + perk.currentStacks();
                g.drawString(font, stackStr, panelX + 120, lineY, applyAlpha(UIConstants.Accent.GREEN, alpha));
            }

            lineY += 20;
        }
    }

    /**
     * Get a color based on perk category for stat display
     */
    private int getCategoryStatColor(PerkChoicesPayload.PerkChoice perk) {
        String cat = perk.categoryName().toLowerCase();
        if (cat.contains("offense") || cat.contains("damage")) return UIConstants.Accent.RED;
        if (cat.contains("defense") || cat.contains("armor")) return UIConstants.Accent.BLUE;
        if (cat.contains("utility") || cat.contains("speed")) return UIConstants.Accent.GOLD;
        if (cat.contains("heal") || cat.contains("regen")) return UIConstants.Accent.GREEN;
        return perk.categoryColor() | 0xFF000000;
    }

    private List<String> wrapText(String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        String[] words = text.split(" ");
        StringBuilder current = new StringBuilder();

        for (String word : words) {
            String test = current.isEmpty() ? word : current + " " + word;
            if (font.width(test) > maxWidth) {
                if (!current.isEmpty()) {
                    lines.add(current.toString());
                    current = new StringBuilder(word);
                } else {
                    lines.add(word);
                }
            } else {
                current = new StringBuilder(test);
            }
        }
        if (!current.isEmpty()) {
            lines.add(current.toString());
        }
        return lines;
    }

    private int applyAlpha(int color, float alpha) {
        int a = (int) (((color >> 24) & 0xFF) * alpha);
        if (a <= 0) a = (int) (255 * alpha);
        return (a << 24) | (color & 0x00FFFFFF);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // First let buttons handle clicks
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        // Check card clicks - directly select the perk
        int totalWidth = choices.size() * CARD_WIDTH + (choices.size() - 1) * CARD_SPACING;
        int startX = (width - totalWidth) / 2;
        int cardY = height / 2 - CARD_HEIGHT / 2 + 20;

        for (int i = 0; i < choices.size(); i++) {
            int cardX = startX + i * (CARD_WIDTH + CARD_SPACING);
            if (mouseX >= cardX && mouseX <= cardX + CARD_WIDTH
                && mouseY >= cardY && mouseY <= cardY + CARD_HEIGHT) {
                // Directly select the perk when clicking anywhere on the card
                selectPerk(i);
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Quick select with number keys
        if (keyCode == GLFW.GLFW_KEY_1 && choices.size() >= 1) {
            selectPerk(0);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_2 && choices.size() >= 2) {
            selectPerk(1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_3 && choices.size() >= 3) {
            selectPerk(2);
            return true;
        }

        // ESC to skip
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            skipPerk();
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void selectPerk(int index) {
        if (index < 0 || index >= choices.size()) return;

        PerkChoicesPayload.PerkChoice choice = choices.get(index);

        // Send selection to server
        PacketDistributor.sendToServer(new PerkSelectionPayload(choice.id()));

        if (minecraft != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, 1.2f));
            minecraft.setScreen(null);
        }
    }

    private void skipPerk() {
        // Send empty selection (skip)
        PacketDistributor.sendToServer(new PerkSelectionPayload(""));

        if (minecraft != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f));
            minecraft.setScreen(null);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }
}

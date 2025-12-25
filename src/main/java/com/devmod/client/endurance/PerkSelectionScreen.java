package com.devmod.endurance;

import com.devmod.ui.editor.core.UIConstants;
import com.devmod.ui.editor.components.EditorButton;
import com.devmod.util.I18n;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Perk selection screen shown after completing a wave.
 * Displays 3 perk choices with tier colors, descriptions, and stack info.
 * Uses standard UIConstants for consistent theming.
 */
@OnlyIn(Dist.CLIENT)
public class PerkSelectionScreen extends Screen {

    // === Colors - Standardized to UIConstants ===
    private static final int COLOR_CARD_BG = UIConstants.Background.INPUT();
    private static final int COLOR_CARD_HOVER = UIConstants.Background.HOVER();
    private static final int COLOR_CARD_SELECTED = UIConstants.Background.ACTIVE();
    private static final int COLOR_BORDER = UIConstants.Border.DEFAULT();  // Blue instead of purple
    private static final int COLOR_TEXT = UIConstants.Text.PRIMARY();
    private static final int COLOR_TEXT_DIM = UIConstants.Text.SECONDARY();
    private static final int COLOR_ACCENT = UIConstants.Accent.BLUE();  // Blue instead of purple

    // === Dimensions (base values, may be scaled down for small screens) ===
    private static final int BASE_CARD_WIDTH = 200;
    private static final int BASE_CARD_HEIGHT = 220;  // Increased from 180 to fit longer descriptions
    private static final int BASE_CARD_SPACING = 20;
    private static final int MIN_CARD_WIDTH = 150;  // Minimum card width before switching layout
    private static final int MAX_DESCRIPTION_LINES = 6;  // Allow more description lines
    private static final int SIDE_MARGIN = 20;  // Minimum margin on each side

    // Calculated dimensions (responsive)
    private int cardWidth;
    private int cardHeight;
    private int cardSpacing;

    // === Animation ===
    private static final long FADE_IN_DURATION = 400;
    private static final long CARD_STAGGER = 150;

    // === Data ===
    private final int waveNumber;
    private final List<PerkChoicesPayload.PerkChoice> choices;
    private int selectedIndex = -1;
    private int hoveredIndex = -1;  // For comparison panel
    private final boolean skipAllowed;

    // === State ===
    private long openTime;
    private boolean soundPlayed = false;
    private final List<EditorButton> perkButtons = new ArrayList<>();
    private EditorButton skipButton;
    private boolean showComparisonPanel = true;  // Show side comparison

    public PerkSelectionScreen(int waveNumber, List<PerkChoicesPayload.PerkChoice> choices) {
        super(I18n.ui("perk.choose_perk"));
        this.waveNumber = waveNumber;
        this.choices = choices;
        this.skipAllowed = choices == null || choices.stream().noneMatch(PerkChoicesPayload.PerkChoice::required);
    }

    @Override
    protected void init() {
        super.init();
        openTime = System.currentTimeMillis();
        perkButtons.clear();

        // Calculate responsive card dimensions
        calculateCardDimensions();

        for (int i = 0; i < choices.size(); i++) {
            final int index = i;
            // Use numbered hotkey hint for quick selection (1, 2, 3)
            String buttonLabel = "Choose [" + (i + 1) + "]";

            EditorButton btn = EditorButton.builder("select-perk-" + index, buttonLabel)
                .style(EditorButton.Style.PRIMARY)
                .size(EditorButton.Size.MEDIUM)
                .onClick(() -> selectPerk(index))
                .build();
            perkButtons.add(btn);
        }

        // Skip button (bottom - prominent height for visibility)
        String skipLabel = skipAllowed ? "Skip Wave Perk" : "Must Choose";
        skipButton = EditorButton.builder("perk-skip", skipLabel)
            .style(skipAllowed ? EditorButton.Style.GHOST : EditorButton.Style.DANGER)
            .size(EditorButton.Size.LARGE)
            .onClick(this::skipPerk)
            .build();
        skipButton.setEnabled(skipAllowed);
    }

    /**
     * Calculate responsive card dimensions based on screen size.
     * Ensures cards never overlap by recalculating spacing based on actual widths.
     */
    private void calculateCardDimensions() {
        int numCards = choices.size();
        if (numCards == 0) numCards = 1;

        // Available width for cards (leaving margins)
        int availableWidth = width - (SIDE_MARGIN * 2);

        // Calculate maximum card width that fits with base spacing
        int maxCardWidth = (availableWidth - (numCards - 1) * BASE_CARD_SPACING) / numCards;

        // Use base dimensions if they fit, otherwise scale down
        if (maxCardWidth >= BASE_CARD_WIDTH) {
            cardWidth = BASE_CARD_WIDTH;
            cardSpacing = BASE_CARD_SPACING;
        } else if (maxCardWidth >= MIN_CARD_WIDTH) {
            cardWidth = maxCardWidth;
            // Recalculate spacing to ensure no overlap: spacing = (availableWidth - numCards * cardWidth) / (numCards - 1)
            if (numCards > 1) {
                cardSpacing = Math.max(4, (availableWidth - numCards * cardWidth) / (numCards - 1));
            } else {
                cardSpacing = 0;
            }
        } else {
            // Very small screen - use minimum width and calculate remaining spacing
            cardWidth = MIN_CARD_WIDTH;
            if (numCards > 1) {
                // Calculate actual spacing available, minimum 4px to prevent overlap
                cardSpacing = Math.max(4, (availableWidth - numCards * MIN_CARD_WIDTH) / (numCards - 1));
            } else {
                cardSpacing = 0;
            }
        }

        // Scale height proportionally
        float scale = (float) cardWidth / BASE_CARD_WIDTH;
        cardHeight = (int) (BASE_CARD_HEIGHT * scale);
    }

    @Override
    public void tick() {
        super.tick();

        // Button visibility handled during render based on elapsed time
    }

    @Override
    public void render(@javax.annotation.Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        long elapsed = System.currentTimeMillis() - openTime;
        float fadeProgress = Math.min(1.0f, elapsed / (float) FADE_IN_DURATION);

        // Play sound
        if (!soundPlayed && elapsed > 100 && minecraft != null) {
            minecraft.getSoundManager().play(Objects.requireNonNull(SimpleSoundInstance.forUI(Objects.requireNonNull(SoundEvents.UI_TOAST_IN), 1.0f)));
            soundPlayed = true;
        }

        // Background
        int bgAlpha = (int) (0xEE * fadeProgress);
        graphics.fill(0, 0, width, height, (bgAlpha << 24) | (UIConstants.Background.SCREEN() & 0x00FFFFFF));

        // Title
        if (fadeProgress > 0.3f) {
            float titleAlpha = (fadeProgress - 0.3f) / 0.7f;
            String title = Objects.requireNonNull(I18n.translate("devmod.endurance.wave_complete", waveNumber).getString());
            int titleColor = applyAlpha(COLOR_ACCENT, titleAlpha);
            graphics.drawCenteredString(Objects.requireNonNull(font), title, width / 2, 40, titleColor);
        }

        // Perk cards
        int totalWidth = choices.size() * cardWidth + (choices.size() - 1) * cardSpacing;
        int startX = (width - totalWidth) / 2;
        int cardY = height / 2 - cardHeight / 2 + 20;

        // Track hovered card for comparison panel
        hoveredIndex = -1;

        for (int i = 0; i < choices.size(); i++) {
            long cardDelay = FADE_IN_DURATION + i * CARD_STAGGER;
            if (elapsed > cardDelay) {
                float cardProgress = Math.min(1.0f, (elapsed - cardDelay) / 300f);
                int cardX = startX + i * (cardWidth + cardSpacing);

                boolean isHovered = mouseX >= cardX && mouseX <= cardX + cardWidth
                    && mouseY >= cardY && mouseY <= cardY + cardHeight;
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

        // Render buttons after cards are visible
        renderPerkButtons(graphics, mouseX, mouseY, elapsed);

        // Keybind hints
        if (fadeProgress > 0.8f) {
            graphics.drawCenteredString(Objects.requireNonNull(font), Objects.requireNonNull(I18n.translate("devmod.perk.keybind_hint").getString()),
                width / 2, height - 25, applyAlpha(UIConstants.Text.MUTED(), fadeProgress));
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderPerkCard(GuiGraphics g, PerkChoicesPayload.PerkChoice perk,
                                 int x, int y, float alpha, boolean hovered, boolean selected) {
        // Card background with animation
        float scale = 0.9f + 0.1f * alpha;
        if (hovered) scale = 1.02f;
        if (selected) scale = 1.05f;

        int cardW = (int) (cardWidth * scale);
        int cardH = (int) (cardHeight * scale);
        int cardX = x + (cardWidth - cardW) / 2;
        int cardY = y + (cardHeight - cardH) / 2;

        // Background
        int bgColor = selected ? COLOR_CARD_SELECTED : (hovered ? COLOR_CARD_HOVER : COLOR_CARD_BG);
        g.fill(cardX, cardY, cardX + cardW, cardY + cardH, applyAlpha(bgColor, alpha));

        // Border with tier color
        int borderColor = applyAlpha(perk.tierColor() | 0xFF000000, alpha);
        g.fill(cardX, cardY, cardX + cardW, cardY + 3, borderColor);
        g.fill(cardX, cardY + cardH - 3, cardX + cardW, cardY + cardH, borderColor);
        g.fill(cardX, cardY, cardX + 3, cardY + cardH, borderColor);
        g.fill(cardX + cardW - 3, cardY, cardX + cardW, cardY + cardH, borderColor);

        var safeFont = Objects.requireNonNull(font);
        int textY = cardY + 10;
        int contentPadding = 8;
        int buttonAreaHeight = 40; // Reserve space for button at bottom

        // === HEADER ROW: Perk name + Tier badge ===
        String tierText = Objects.requireNonNull(perk.tierName());
        int tierBadgeW = safeFont.width(tierText) + 8;
        int tierBadgeH = 14;

        // Tier badge (top right)
        g.fill(cardX + cardW - tierBadgeW - contentPadding, textY,
               cardX + cardW - contentPadding, textY + tierBadgeH,
               applyAlpha(perk.tierColor() | 0xFF000000, alpha * 0.85f));
        g.drawString(safeFont, tierText, cardX + cardW - tierBadgeW - contentPadding + 4, textY + 3,
                     applyAlpha(COLOR_TEXT, alpha));

        // Perk name (left, truncated to avoid tier badge)
        int maxNameWidth = cardW - tierBadgeW - contentPadding * 3;
        String perkName = truncateText(perk.name(), maxNameWidth);
        g.drawString(safeFont, perkName, cardX + contentPadding, textY + 2, applyAlpha(COLOR_TEXT, alpha));
        textY += 18;

        // === CATEGORY ROW ===
        String categoryName = truncateText(perk.categoryName(), cardW - contentPadding * 2);
        g.drawString(safeFont, categoryName, cardX + contentPadding, textY,
                     applyAlpha(perk.categoryColor() | 0xFF000000, alpha));
        textY += 14;

        // === REQUIRED/SUGGESTED TAG (below category, not overlapping) ===
        if (perk.required() || perk.suggested()) {
            String tagText = perk.required() ? "!" : "\u2605"; // ! or ★
            String tagLabel = perk.required() ? "Required" : "Suggested";
            int tagColor = perk.required() ? 0xFFE85C5C : 0xFF5B9BD5;
            g.drawString(safeFont, tagText + " " + tagLabel, cardX + contentPadding, textY, applyAlpha(tagColor, alpha));
            textY += 12;
        }

        // === DESCRIPTION (word wrap) ===
        textY += 2; // Small gap
        int maxDescY = cardY + cardH - buttonAreaHeight - 25; // Leave room for stack info
        List<String> lines = wrapText(perk.description(), cardW - contentPadding * 2);
        int linesRendered = 0;
        for (String line : lines) {
            if (textY > maxDescY || linesRendered >= MAX_DESCRIPTION_LINES) break;
            g.drawString(safeFont, line, cardX + contentPadding, textY, applyAlpha(COLOR_TEXT_DIM, alpha));
            textY += 10;
            linesRendered++;
        }

        // === BOTTOM AREA: Stack info (above button) ===
        int bottomInfoY = cardY + cardH - buttonAreaHeight - 14;
        if (perk.stackable()) {
            String stackText = "Stacks: " + perk.currentStacks() + "/" + perk.maxStacks();
            g.drawString(safeFont, stackText, cardX + contentPadding, bottomInfoY, applyAlpha(COLOR_ACCENT, alpha));
        } else if (hovered && !perk.description().isEmpty()) {
            // Show stat hint only if not stackable (to avoid overlap)
            String hintText = "\u2191 " + getCompactStatHint(perk); // ↑ prefix
            g.drawString(safeFont, hintText, cardX + contentPadding, bottomInfoY, applyAlpha(UIConstants.Accent.GREEN(), alpha));
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
        g.fill(panelX, panelY, panelX + panelW, panelY + panelH, applyAlpha(UIConstants.Background.PANEL(), alpha));

        // Panel border
        int borderColor = applyAlpha(COLOR_BORDER, alpha);
        g.fill(panelX, panelY, panelX + panelW, panelY + 2, borderColor);
        g.fill(panelX, panelY + panelH - 2, panelX + panelW, panelY + panelH, borderColor);
        g.fill(panelX, panelY, panelX + 2, panelY + panelH, borderColor);
        g.fill(panelX + panelW - 2, panelY, panelX + panelW, panelY + panelH, borderColor);

        var safeFont = Objects.requireNonNull(font);

        // Title
        g.drawString(safeFont, I18n.translate("devmod.perk.quick_compare").getString(), panelX + 8, panelY + 6, applyAlpha(COLOR_ACCENT, alpha));

        // List all perks with their key stat
        int lineY = panelY + 22;
        for (int i = 0; i < choices.size(); i++) {
            PerkChoicesPayload.PerkChoice perk = choices.get(i);
            boolean isHovered = (i == hoveredIdx);

            // Highlight bar for hovered
            if (isHovered) {
                g.fill(panelX + 4, lineY - 2, panelX + panelW - 4, lineY + 16, applyAlpha(UIConstants.Background.HOVER(), alpha));
            }

            // Tier indicator (colored dot)
            int dotColor = applyAlpha(perk.tierColor() | 0xFF000000, alpha);
            g.fill(panelX + 8, lineY + 3, panelX + 14, lineY + 9, dotColor);

            // Perk name (truncated - keep at least 6 chars for readability)
            String name = Objects.requireNonNull(perk.name());
            if (safeFont.width(name) > 100) {
                String ellipsis = "...";
                int minChars = Math.min(6, name.length());
                while (safeFont.width(name + ellipsis) > 100 && name.length() > minChars) {
                    name = name.substring(0, name.length() - 1);
                }
                name += ellipsis;
            }
            int nameColor = isHovered ? applyAlpha(COLOR_TEXT, alpha) : applyAlpha(COLOR_TEXT_DIM, alpha);
            g.drawString(safeFont, name, panelX + 18, lineY, nameColor);

            // Key stat hint (right-aligned)
            String statHint = Objects.requireNonNull(getCompactStatHint(perk));
            int statColor = applyAlpha(getCategoryStatColor(perk), alpha);
            int statX = panelX + panelW - safeFont.width(statHint) - 10;
            g.drawString(safeFont, statHint, statX, lineY, statColor);

            // Stack indicator if applicable
            if (perk.stackable() && perk.currentStacks() > 0) {
                String stackStr = "x" + perk.currentStacks();
                g.drawString(safeFont, stackStr, panelX + 120, lineY, applyAlpha(UIConstants.Accent.GREEN(), alpha));
            }

            lineY += 20;
        }
    }

    /**
     * Get a color based on perk category for stat display
     */
    private int getCategoryStatColor(PerkChoicesPayload.PerkChoice perk) {
        String cat = perk.categoryName().toLowerCase();
        if (cat.contains("offense") || cat.contains("damage")) return UIConstants.Accent.RED();
        if (cat.contains("defense") || cat.contains("armor")) return UIConstants.Accent.BLUE();
        if (cat.contains("utility") || cat.contains("speed")) return UIConstants.Accent.GOLD();
        if (cat.contains("heal") || cat.contains("regen")) return UIConstants.Accent.GREEN();
        return perk.categoryColor() | 0xFF000000;
    }

    private List<String> wrapText(String text, int maxWidth) {
        var safeFont = Objects.requireNonNull(font);
        List<String> lines = new ArrayList<>();
        String[] words = text.split(" ");
        StringBuilder current = new StringBuilder();

        for (String word : words) {
            String test = current.isEmpty() ? word : current + " " + word;
            if (safeFont.width(Objects.requireNonNull(test)) > maxWidth) {
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

    /**
     * Truncate text to fit within maxWidth pixels, adding ellipsis if needed.
     */
    private String truncateText(String text, int maxWidth) {
        var safeFont = Objects.requireNonNull(font);
        if (safeFont.width(Objects.requireNonNull(text)) <= maxWidth) return text;
        String ellipsis = "...";
        int minChars = Math.min(6, text.length());
        String truncated = text;
        while (safeFont.width(truncated + ellipsis) > maxWidth && truncated.length() > minChars) {
            truncated = truncated.substring(0, truncated.length() - 1);
        }
        return truncated + ellipsis;
    }

    private int applyAlpha(int color, float alpha) {
        int a = (int) (((color >> 24) & 0xFF) * alpha);
        if (a <= 0) a = (int) (255 * alpha);
        return (a << 24) | (color & 0x00FFFFFF);
    }

    private void renderPerkButtons(GuiGraphics graphics, int mouseX, int mouseY, long elapsed) {
        int totalWidth = choices.size() * cardWidth + (choices.size() - 1) * cardSpacing;
        int startX = (width - totalWidth) / 2;
        int cardY = height / 2 - cardHeight / 2 + 20;

        for (int i = 0; i < choices.size(); i++) {
            long cardDelay = FADE_IN_DURATION + i * CARD_STAGGER + 200;
            if (elapsed <= cardDelay) {
                continue;
            }

            int cardX = startX + i * (cardWidth + cardSpacing);
            int buttonMargin = Math.max(10, cardWidth / 8);
            int buttonWidth = Math.max(60, cardWidth - buttonMargin * 2);
            int buttonHeight = Math.min(25, cardHeight / 8);
            int buttonY = cardY + cardHeight - buttonHeight - 10;

            EditorButton btn = perkButtons.get(i);
            btn.render(graphics, cardX + buttonMargin, buttonY, buttonWidth, buttonHeight, mouseX, mouseY);
        }

        // Skip button
        if (skipButton != null && elapsed > FADE_IN_DURATION * 0.5f) {
            int skipW = UIConstants.Size.BUTTON_WIDTH_MEDIUM;
            int skipH = UIConstants.Size.BUTTON_HEIGHT_PROMINENT;
            int skipX = width / 2 - skipW / 2;
            int skipY = height - 50;
            skipButton.render(graphics, skipX, skipY, skipW, skipH, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        long elapsed = System.currentTimeMillis() - openTime;

        if (button == 0) {
            // Perk buttons
            int totalWidth = choices.size() * cardWidth + (choices.size() - 1) * cardSpacing;
            int startX = (width - totalWidth) / 2;
            int cardY = height / 2 - cardHeight / 2 + 20;

            for (int i = 0; i < perkButtons.size(); i++) {
                long cardDelay = FADE_IN_DURATION + i * CARD_STAGGER + 200;
                if (elapsed <= cardDelay) continue;
                int cardX = startX + i * (cardWidth + cardSpacing);
                if (perkButtons.get(i).mouseClicked(mouseX, mouseY, button)) {
                    return true;
                }
                // Direct card click select
                if (mouseX >= cardX && mouseX <= cardX + cardWidth
                    && mouseY >= cardY && mouseY <= cardY + cardHeight) {
                    selectPerk(i);
                    return true;
                }
            }

            // Skip button
            if (skipButton != null && elapsed > FADE_IN_DURATION * 0.5f) {
                if (skipButton.mouseClicked(mouseX, mouseY, button)) {
                    return true;
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        boolean handled = false;
        long elapsed = System.currentTimeMillis() - openTime;

        if (button == 0) {
            for (int i = 0; i < perkButtons.size(); i++) {
                long cardDelay = FADE_IN_DURATION + i * CARD_STAGGER + 200;
                if (elapsed <= cardDelay) continue;
                handled |= perkButtons.get(i).mouseReleased(mouseX, mouseY, button);
            }
            if (skipButton != null && elapsed > FADE_IN_DURATION * 0.5f) {
                handled |= skipButton.mouseReleased(mouseX, mouseY, button);
            }
        }

        if (handled) return true;
        return super.mouseReleased(mouseX, mouseY, button);
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

        var mc = minecraft;
        if (mc != null) {
            mc.getSoundManager().play(Objects.requireNonNull(SimpleSoundInstance.forUI(Objects.requireNonNull(SoundEvents.PLAYER_LEVELUP), 1.2f)));
            mc.setScreen(null);
        }
    }

    private void skipPerk() {
        if (!skipAllowed) {
            return;
        }
        // Send empty selection (skip)
        PacketDistributor.sendToServer(new PerkSelectionPayload(""));

        var mc = minecraft;
        if (mc != null) {
            mc.getSoundManager().play(Objects.requireNonNull(SimpleSoundInstance.forUI(Objects.requireNonNull(SoundEvents.UI_BUTTON_CLICK.value()), 1.0f)));
            mc.setScreen(null);
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

package com.devmod.client.endurance;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.lwjgl.glfw.GLFW;

import com.google.common.base.Splitter;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

import com.devmod.client.ui.components.CountdownTimer;
import com.devmod.client.ui.core.UIScaleManager;
import com.devmod.client.ui.editor.components.EditorButton;
import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.endurance.PerkChoicesPayload;
import com.devmod.endurance.PerkSelectionPayload;
import com.devmod.util.I18n;

@OnlyIn(Dist.CLIENT)
public class PerkSelectionScreen extends Screen {

    // === Colors - Using DesignTokens for consistency ===
    private static final int COLOR_CARD_BG = DesignTokens.Surface.LEVEL_0;
    private static final int COLOR_CARD_HOVER = DesignTokens.Surface.LEVEL_2;
    private static final int COLOR_CARD_SELECTED = DesignTokens.Surface.LEVEL_3;
    private static final int COLOR_BORDER = DesignTokens.Accent.PRIMARY;
    private static final int COLOR_TEXT = DesignTokens.Text.PRIMARY;
    private static final int COLOR_TEXT_DIM = DesignTokens.Text.SECONDARY;
    private static final int COLOR_ACCENT = DesignTokens.Accent.PRIMARY;
    private static final int COLOR_SCREEN_BG = DesignTokens.Bg.LEVEL_0;
    private static final int COLOR_PANEL_BG = DesignTokens.Bg.LEVEL_2;
    private static final int COLOR_SUCCESS = DesignTokens.Semantic.SUCCESS;
    private static final int COLOR_ERROR = DesignTokens.Semantic.ERROR;
    private static final int COLOR_WARNING = DesignTokens.Semantic.WARNING;
    private static final int COLOR_INFO = DesignTokens.Semantic.INFO;
    private static final Splitter SPACE_SPLITTER = Splitter.on(' ');

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

    // === Countdown ===
    private static final int COUNTDOWN_WARN_SECONDS = 10;
    private static final int COUNTDOWN_URGENT_SECONDS = 5;
    private static final int COUNTDOWN_AUDIO_SECONDS = 5;
    private static final int COUNTDOWN_MARGIN = 16;

    // === Data ===
    private final int waveNumber;
    private final List<PerkChoicesPayload.PerkChoice> choices;
    private int selectedIndex = -1;
    private int hoveredIndex = -1;  // For comparison panel
    private final boolean skipAllowed;
    private final CountdownTimer countdown;

    // === State ===
    private long openTime;
    private boolean soundPlayed = false;
    private final List<EditorButton> perkButtons = new ArrayList<>();
    @Nullable
    private EditorButton skipButton;
    private boolean showComparisonPanel = true;  // Show side comparison
    private int lastWidth = -1;
    private int lastHeight = -1;

    public PerkSelectionScreen(int waveNumber, List<PerkChoicesPayload.PerkChoice> choices) {
        this(waveNumber, choices, 0L);
    }

    public PerkSelectionScreen(int waveNumber, List<PerkChoicesPayload.PerkChoice> choices, long expiresAt) {
        super(I18n.ui("perk.choose_perk"));
        this.waveNumber = waveNumber;
        this.choices = choices;
        this.skipAllowed = choices == null || choices.stream().noneMatch(PerkChoicesPayload.PerkChoice::required);
        this.countdown = new CountdownTimer(expiresAt, COUNTDOWN_WARN_SECONDS, COUNTDOWN_URGENT_SECONDS, COUNTDOWN_AUDIO_SECONDS);
    }

    @Override
    protected void init() {
        super.init();
        openTime = System.currentTimeMillis();
        perkButtons.clear();

        // Calculate responsive card dimensions
        calculateCardDimensions();
        lastWidth = width;
        lastHeight = height;

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
        EditorButton skip = EditorButton.builder("perk-skip", skipLabel)
            .style(skipAllowed ? EditorButton.Style.GHOST : EditorButton.Style.DANGER)
            .size(EditorButton.Size.LARGE)
            .onClick(this::skipPerk)
            .build();
        skip.setEnabled(skipAllowed);
        skipButton = skip;
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

        countdown.tick();

        // Button visibility handled during render based on elapsed time
    }

    @Override
    public void render(@javax.annotation.Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        UIScaleManager.update();
        if (width != lastWidth || height != lastHeight) {
            calculateCardDimensions();
            lastWidth = width;
            lastHeight = height;
        }
        long elapsed = System.currentTimeMillis() - openTime;
        float fadeProgress = Math.min(1.0f, elapsed / (float) FADE_IN_DURATION);

        // Play sound
        if (!soundPlayed && elapsed > 100 && minecraft != null) {
            minecraft.getSoundManager().play(Objects.requireNonNull(SimpleSoundInstance.forUI(Objects.requireNonNull(SoundEvents.UI_TOAST_IN), 1.0f)));
            soundPlayed = true;
        }

        // Background
        int bgAlpha = (int) (0xEE * fadeProgress);
        graphics.fill(0, 0, width, height, (bgAlpha << 24) | (COLOR_SCREEN_BG & DesignTokens.Mask.RGB));

        // Title
        if (fadeProgress > 0.3f) {
            float titleAlpha = (fadeProgress - 0.3f) / 0.7f;
            String title = Objects.requireNonNull(I18n.translate("devmod.endurance.wave_complete", waveNumber).getString());
            int titleColor = applyAlpha(COLOR_ACCENT, titleAlpha);
            UIScaleManager.drawScaledCenteredString(graphics, Objects.requireNonNull(font), title, width / 2, UIScaleManager.scale(40), titleColor);
        }

        renderCountdown(graphics, fadeProgress);

        // Perk cards
        int totalWidth = choices.size() * cardWidth + (choices.size() - 1) * cardSpacing;
        int startX = (width - totalWidth) / 2;
        int cardY = height / 2 - cardHeight / 2 + UIScaleManager.scale(20);

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
            UIScaleManager.drawScaledCenteredString(graphics, Objects.requireNonNull(font), Objects.requireNonNull(I18n.translate("devmod.perk.keybind_hint").getString()),
                width / 2, height - 25, applyAlpha(DesignTokens.Text.MUTED, fadeProgress));
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
        int borderColor = applyAlpha(perk.tierColor() | DesignTokens.Mask.ALPHA, alpha);
        g.fill(cardX, cardY, cardX + cardW, cardY + 3, borderColor);
        g.fill(cardX, cardY + cardH - 3, cardX + cardW, cardY + cardH, borderColor);
        g.fill(cardX, cardY, cardX + 3, cardY + cardH, borderColor);
        g.fill(cardX + cardW - 3, cardY, cardX + cardW, cardY + cardH, borderColor);

        var safeFont = Objects.requireNonNull(font);
        int textY = cardY + UIScaleManager.scale(10);
        int contentPadding = UIScaleManager.scale(8);
        int buttonAreaHeight = UIScaleManager.scale(40); // Reserve space for button at bottom

        // === HEADER ROW: Perk name + Tier badge ===
        String tierText = Objects.requireNonNull(perk.tierName());
        int tierBadgeW = UIScaleManager.getScaledStringWidth(safeFont, tierText) + 8;
        int tierBadgeH = UIScaleManager.scale(14);

        // Tier badge (top right)
        g.fill(cardX + cardW - tierBadgeW - contentPadding, textY,
               cardX + cardW - contentPadding, textY + tierBadgeH,
               applyAlpha(perk.tierColor() | DesignTokens.Mask.ALPHA, alpha * 0.85f));
        UIScaleManager.drawScaledString(g, safeFont, tierText, cardX + cardW - tierBadgeW - contentPadding + 4, textY + 3,
                     applyAlpha(COLOR_TEXT, alpha));

        // Perk name (left, truncated to avoid tier badge)
        int maxNameWidth = cardW - tierBadgeW - contentPadding * 3;
        String perkName = truncateText(perk.name(), maxNameWidth);
        UIScaleManager.drawScaledString(g, safeFont, perkName, cardX + contentPadding, textY + UIScaleManager.scale(2), applyAlpha(COLOR_TEXT, alpha));
        textY += UIScaleManager.scale(18);

        // === CATEGORY ROW ===
        String categoryName = truncateText(perk.categoryName(), cardW - contentPadding * 2);
        UIScaleManager.drawScaledString(g, safeFont, categoryName, cardX + contentPadding, textY,
                     applyAlpha(perk.categoryColor() | DesignTokens.Mask.ALPHA, alpha));
        textY += UIScaleManager.scale(14);

        // === REQUIRED/SUGGESTED TAG (below category, not overlapping) ===
        if (perk.required() || perk.suggested()) {
            String tagText = perk.required() ? "!" : "\u2605"; // ! or ★
            String tagLabel = perk.required() ? "Required" : "Suggested";
            int tagColor = perk.required()
                ? EnduranceUiTheme.PerkSelection.TAG_REQUIRED
                : EnduranceUiTheme.PerkSelection.TAG_OPTIONAL;
            UIScaleManager.drawScaledString(g, safeFont, tagText + " " + tagLabel, cardX + contentPadding, textY, applyAlpha(tagColor, alpha));
            textY += UIScaleManager.scale(12);
        }

        // === DESCRIPTION (word wrap) ===
        textY += UIScaleManager.scale(2); // Small gap
        int maxDescY = cardY + cardH - buttonAreaHeight - 25; // Leave room for stack info
        List<String> lines = wrapText(Objects.requireNonNull(perk.description()), cardW - contentPadding * 2);
        int linesRendered = 0;
        for (String line : lines) {
            if (textY > maxDescY || linesRendered >= MAX_DESCRIPTION_LINES) break;
            UIScaleManager.drawScaledString(g, safeFont, line, cardX + contentPadding, textY, applyAlpha(COLOR_TEXT_DIM, alpha));
            textY += UIScaleManager.scale(10);
            linesRendered++;
        }

        // === BOTTOM AREA: Stack info + Synergy info (above button) ===
        int bottomInfoY = cardY + cardH - buttonAreaHeight - UIScaleManager.scale(14);
        if (perk.stackable()) {
            String stackText = "Stacks: " + perk.currentStacks() + "/" + perk.maxStacks();
            UIScaleManager.drawScaledString(g, safeFont, stackText, cardX + contentPadding, bottomInfoY, applyAlpha(COLOR_ACCENT, alpha));
            bottomInfoY -= UIScaleManager.scale(12);
        }

        // === SYNERGY INDICATOR ===
        if (perk.hasSynergyInfo()) {
            int synergyColor = getSynergyColor(perk);

            // Show synergy badge if completing a synergy
            if (perk.newSynergyCount() > 0) {
                // Completing a synergy - highlight!
                String synergyBadge = "\u2605 SYNERGY!"; // ★ SYNERGY!
                UIScaleManager.drawScaledString(g, safeFont, synergyBadge, cardX + contentPadding, bottomInfoY,
                    applyAlpha(EnduranceUiTheme.PerkSelection.SYNERGY_COMPLETE, alpha));
                bottomInfoY -= UIScaleManager.scale(11);
            } else if (perk.isRecommended()) {
                String recBadge = "\u2192 Recommended"; // → Recommended
                UIScaleManager.drawScaledString(g, safeFont, recBadge, cardX + contentPadding, bottomInfoY, applyAlpha(COLOR_SUCCESS, alpha));
                bottomInfoY -= UIScaleManager.scale(11);
            }

            // Show synergy hint on hover
            if (hovered && !perk.synergyHint().isEmpty()) {
                String hint = perk.synergyHint();
                if (UIScaleManager.getScaledStringWidth(safeFont, Objects.requireNonNull(hint)) > cardW - contentPadding * 2) {
                    hint = truncateText(hint, cardW - contentPadding * 2);
                }
                UIScaleManager.drawScaledString(g, safeFont, hint, cardX + contentPadding, bottomInfoY, applyAlpha(synergyColor, alpha));
            }
        } else if (hovered && !perk.description().isEmpty()) {
            // Show stat hint only if not stackable (to avoid overlap)
            String hintText = "\u2191 " + getCompactStatHint(perk); // ↑ prefix
            UIScaleManager.drawScaledString(g, safeFont, hintText, cardX + contentPadding, bottomInfoY, applyAlpha(COLOR_SUCCESS, alpha));
        }
    }

    /**
     * Get color based on synergy strength.
     */
    private int getSynergyColor(PerkChoicesPayload.PerkChoice perk) {
        if (perk.newSynergyCount() > 0) {
            return EnduranceUiTheme.PerkSelection.SYNERGY_COMPLETE;
        } else if (perk.synergyScore() >= 6) {
            return EnduranceUiTheme.PerkSelection.SYNERGY_STRONG;
        } else if (perk.synergyScore() >= 3) {
            return EnduranceUiTheme.PerkSelection.SYNERGY_MODERATE;
        } else {
            return EnduranceUiTheme.PerkSelection.SYNERGY_MINOR;
        }
    }

    /**
     * Get a compact stat hint for perk comparison (e.g., "+20% Damage", "+50 HP")
     */
    private String getCompactStatHint(PerkChoicesPayload.PerkChoice perk) {
        String desc = perk.description().toLowerCase(Locale.ROOT);
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
        int panelW = Math.min(UIScaleManager.scale(280), width - UIScaleManager.scale(20));
        int panelH = UIScaleManager.scale(20) + choices.size() * UIScaleManager.scale(22);
        int panelX = Math.max(UIScaleManager.scale(10), width - panelW - UIScaleManager.scale(10));
        int panelY = UIScaleManager.scale(60);

        // Panel background
        g.fill(panelX, panelY, panelX + panelW, panelY + panelH, applyAlpha(COLOR_PANEL_BG, alpha));

        // Panel border
        int borderColor = applyAlpha(COLOR_BORDER, alpha);
        g.fill(panelX, panelY, panelX + panelW, panelY + 2, borderColor);
        g.fill(panelX, panelY + panelH - 2, panelX + panelW, panelY + panelH, borderColor);
        g.fill(panelX, panelY, panelX + 2, panelY + panelH, borderColor);
        g.fill(panelX + panelW - 2, panelY, panelX + panelW, panelY + panelH, borderColor);

        var safeFont = Objects.requireNonNull(font);

        // Title
        UIScaleManager.drawScaledString(g, safeFont, I18n.translate("devmod.perk.quick_compare").getString(),
            panelX + UIScaleManager.scale(8), panelY + UIScaleManager.scale(6), applyAlpha(COLOR_ACCENT, alpha));

        // List all perks with their key stat
        int lineY = panelY + UIScaleManager.scale(22);
        for (int i = 0; i < choices.size(); i++) {
            PerkChoicesPayload.PerkChoice perk = choices.get(i);
            boolean isHovered = (i == hoveredIdx);

            // Highlight bar for hovered
            if (isHovered) {
                g.fill(panelX + UIScaleManager.scale(4), lineY - UIScaleManager.scale(2),
                    panelX + panelW - UIScaleManager.scale(4), lineY + UIScaleManager.scale(16),
                    applyAlpha(COLOR_CARD_HOVER, alpha));
            }

            // Synergy indicator (star if completing synergy, dot otherwise)
            if (perk.newSynergyCount() > 0) {
                // Gold star for synergy completion
                UIScaleManager.drawScaledString(g, safeFont, "\u2605", panelX + UIScaleManager.scale(6), lineY,
                    applyAlpha(EnduranceUiTheme.PerkSelection.SYNERGY_COMPLETE, alpha));
            } else {
                // Tier indicator (colored dot)
                int dotColor = applyAlpha(perk.tierColor() | DesignTokens.Mask.ALPHA, alpha);
                g.fill(panelX + UIScaleManager.scale(8), lineY + UIScaleManager.scale(3),
                    panelX + UIScaleManager.scale(14), lineY + UIScaleManager.scale(9), dotColor);
            }

            // Perk name (truncated - keep at least 6 chars for readability)
            String name = Objects.requireNonNull(perk.name());
            if (UIScaleManager.getScaledStringWidth(safeFont, name) > 90) {
                String ellipsis = "...";
                int minChars = Math.min(6, name.length());
                while (UIScaleManager.getScaledStringWidth(safeFont, name + ellipsis) > 90 && name.length() > minChars) {
                    name = name.substring(0, name.length() - 1);
                }
                name += ellipsis;
            }
            int nameColor = isHovered ? applyAlpha(COLOR_TEXT, alpha) : applyAlpha(COLOR_TEXT_DIM, alpha);
            UIScaleManager.drawScaledString(g, safeFont, name, panelX + UIScaleManager.scale(18), lineY, nameColor);

            // Synergy score indicator (if any)
            if (perk.synergyScore() > 0) {
                String synergyStr = "S:" + perk.synergyScore();
                int synergyColor = applyAlpha(getSynergyColor(perk), alpha);
                UIScaleManager.drawScaledString(g, safeFont, synergyStr, panelX + UIScaleManager.scale(115), lineY, synergyColor);
            } else if (perk.stackable() && perk.currentStacks() > 0) {
                // Stack indicator if applicable (only if no synergy score)
                String stackStr = "x" + perk.currentStacks();
                UIScaleManager.drawScaledString(g, safeFont, stackStr, panelX + UIScaleManager.scale(115), lineY, applyAlpha(COLOR_SUCCESS, alpha));
            }

            // Key stat hint (right-aligned)
            String statHint = Objects.requireNonNull(getCompactStatHint(perk));
            int statColor = applyAlpha(getCategoryStatColor(perk), alpha);
            int statX = panelX + panelW - UIScaleManager.getScaledStringWidth(safeFont, statHint) - UIScaleManager.scale(10);
            UIScaleManager.drawScaledString(g, safeFont, statHint, statX, lineY, statColor);

            lineY += UIScaleManager.scale(20);
        }
    }

    /**
     * Get a color based on perk category for stat display
     */
    private int getCategoryStatColor(PerkChoicesPayload.PerkChoice perk) {
        String cat = perk.categoryName().toLowerCase(Locale.ROOT);
        if (cat.contains("offense") || cat.contains("damage")) return COLOR_ERROR;
        if (cat.contains("defense") || cat.contains("armor")) return COLOR_INFO;
        if (cat.contains("utility") || cat.contains("speed")) return COLOR_WARNING;
        if (cat.contains("heal") || cat.contains("regen")) return COLOR_SUCCESS;
        return perk.categoryColor() | DesignTokens.Mask.ALPHA;
    }

    private List<String> wrapText(@Nonnull String text, int maxWidth) {
        var safeFont = Objects.requireNonNull(font);
        List<String> lines = new ArrayList<>();
        Iterable<String> words = SPACE_SPLITTER.split(text);
        StringBuilder current = new StringBuilder();

        for (String word : words) {
            String test = current.isEmpty() ? word : current + " " + word;
            if (UIScaleManager.getScaledStringWidth(safeFont, Objects.requireNonNull(test)) > maxWidth) {
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
        return (a << 24) | (color & DesignTokens.Mask.RGB);
    }

    private void renderCountdown(GuiGraphics graphics, float fadeProgress) {
        if (!countdown.isActive()) {
            return;
        }
        int remaining = countdown.getRemainingSeconds();
        if (remaining < 0) {
            return;
        }
        String text = Objects.requireNonNull(I18n.ui("selection_time_remaining", remaining).getString());
        var safeFont = Objects.requireNonNull(font, "font");
        int textWidth = UIScaleManager.getScaledStringWidth(safeFont, text);
        int margin = UIScaleManager.scale(COUNTDOWN_MARGIN);
        int x = width - textWidth - margin;
        int y = margin;

        int bgColor = applyAlpha(COLOR_PANEL_BG, fadeProgress * 0.6f);
        graphics.fill(x - 6, y - 4, x + textWidth + 6, y + safeFont.lineHeight + 4, bgColor);

        float pulse = countdown.getPulse();
        int textColor = applyAlpha(countdown.getColor(), fadeProgress * pulse);
        UIScaleManager.drawScaledString(graphics, safeFont, text, x, y, textColor, false);
    }

    private void renderPerkButtons(GuiGraphics graphics, int mouseX, int mouseY, long elapsed) {
        int totalWidth = choices.size() * cardWidth + (choices.size() - 1) * cardSpacing;
        int startX = (width - totalWidth) / 2;
        int cardY = height / 2 - cardHeight / 2 + UIScaleManager.scale(20);

        for (int i = 0; i < choices.size(); i++) {
            long cardDelay = FADE_IN_DURATION + i * CARD_STAGGER + 200;
            if (elapsed <= cardDelay) {
                continue;
            }

            int cardX = startX + i * (cardWidth + cardSpacing);
            int buttonMargin = Math.max(UIScaleManager.scale(10), cardWidth / 8);
            int buttonWidth = Math.max(UIScaleManager.scale(60), cardWidth - buttonMargin * 2);
            int buttonHeight = Math.min(UIScaleManager.scale(25), cardHeight / 8);
            int buttonY = cardY + cardHeight - buttonHeight - UIScaleManager.scale(10);

            EditorButton btn = perkButtons.get(i);
            btn.render(graphics, cardX + buttonMargin, buttonY, buttonWidth, buttonHeight, mouseX, mouseY);
        }

        // Skip button
        final var skip = skipButton;
        if (skip != null && elapsed > FADE_IN_DURATION * 0.5f) {
            int skipW = UIScaleManager.scale(DesignTokens.Component.BUTTON_MIN_WIDTH * 2);
            int skipH = UIScaleManager.scale(DesignTokens.Component.BUTTON_HEIGHT_LG);
            int skipX = width / 2 - skipW / 2;
            int skipY = height - UIScaleManager.scale(50);
            skip.render(graphics, skipX, skipY, skipW, skipH, mouseX, mouseY);
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
            final var skip = skipButton;
            if (skip != null && elapsed > FADE_IN_DURATION * 0.5f) {
                if (skip.mouseClicked(mouseX, mouseY, button)) {
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
            final var skip = skipButton;
            if (skip != null && elapsed > FADE_IN_DURATION * 0.5f) {
                handled |= skip.mouseReleased(mouseX, mouseY, button);
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

            // Trigger checkpoint screen opening after perk selection
            // This ensures the checkpoint screen opens even if overlay misses the transition
            openCheckpointScreenAfterDelay(mc);
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

            // Trigger checkpoint screen opening after skipping perk
            openCheckpointScreenAfterDelay(mc);
        }
    }

    /**
     * Opens the checkpoint screen after a short delay to ensure smooth transition.
     * This is needed because the overlay-based detection can miss the state transition
     * when the perk screen closes.
     *
     * Only opens checkpoint if NO screen is currently showing, allowing the user to
     * view debrief/report screens first and close them when ready.
     */
    private void openCheckpointScreenAfterDelay(Minecraft mc) {
        java.util.concurrent.CompletableFuture.delayedExecutor(
            500, java.util.concurrent.TimeUnit.MILLISECONDS
        ).execute(() -> mc.execute(() -> {
            // Check quest state
            boolean hasQuest = ClientQuestCache.hasActiveQuest();
            var data = ClientQuestCache.getData();
            var state = data != null ? data.getState() : null;

            var currentScreen = mc.screen;
            String screenName = currentScreen != null ? currentScreen.getClass().getSimpleName() : "null";

            org.slf4j.LoggerFactory.getLogger("PerkSelectionScreen")
                .info("[PerkSelect] Delayed checkpoint check: screen={}, hasQuest={}, state={}",
                    screenName, hasQuest, state);

            // Open checkpoint screen if quest is at WAVE_COMPLETE state
            if (hasQuest && data != null
                && state == com.devmod.endurance.EnduranceQuestState.WAVE_COMPLETE) {
                // Don't replace if already showing checkpoint or perk screens
                if (currentScreen instanceof WaveCheckpointScreen
                    || currentScreen instanceof PerkSelectionScreen) {
                    org.slf4j.LoggerFactory.getLogger("PerkSelectionScreen")
                        .info("[PerkSelect] Already showing correct screen, skipping");
                    return;
                }
                // Only open checkpoint if NO screen is showing (user closed debrief/report)
                // This allows user to analyze reports before proceeding
                if (currentScreen != null) {
                    org.slf4j.LoggerFactory.getLogger("PerkSelectionScreen")
                        .info("[PerkSelect] Screen {} is open, not replacing (user should close it first)", screenName);
                    return;
                }
                org.slf4j.LoggerFactory.getLogger("PerkSelectionScreen")
                    .info("[PerkSelect] Opening checkpoint screen (no screen active)");
                com.devmod.client.ui.ScreenSafety.openSafe(
                    "wave_checkpoint",
                    () -> new WaveCheckpointScreen());
            }
        }));
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

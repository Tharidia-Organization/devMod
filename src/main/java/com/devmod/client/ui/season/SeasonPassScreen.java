package com.devmod.client.ui.season;

import java.util.List;
import java.util.Objects;

import javax.annotation.Nonnull;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

import com.devmod.client.season.ClientSeasonPassCache;
import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.endurance.season.RequestSeasonPassPayload;
import com.devmod.util.I18n;

/**
 * Season Pass progress screen.
 * Shows current season tier, XP progress, and available rewards.
 */
@OnlyIn(Dist.CLIENT)
public class SeasonPassScreen extends Screen {

    // === Colors (Gold/Bronze theme for season pass) ===
    private static final int COLOR_BG_TOP = 0xF0181818;
    private static final int COLOR_BG_BOTTOM = 0xF00d0d1a;
    private static final int COLOR_BORDER = 0xFFD4AF37;      // Gold accent
    private static final int COLOR_TITLE = 0xFFFFD700;       // Gold
    private static final int COLOR_SUBTITLE = 0xFFDAA520;    // Goldenrod
    private static final int COLOR_TEXT = DesignTokens.Text.PRIMARY;
    private static final int COLOR_TEXT_DIM = DesignTokens.Text.SECONDARY;
    private static final int COLOR_FREE_TRACK = 0xFF60a5fa;  // Blue for free
    private static final int COLOR_PREMIUM_TRACK = 0xFFFFD700; // Gold for premium
    private static final int COLOR_LOCKED = 0xFF555555;
    private static final int COLOR_PROGRESS_BG = 0xFF2a2a2a;
    private static final int COLOR_PROGRESS_FILL = 0xFFD4AF37;

    // === Dimensions ===
    private static final int PANEL_WIDTH = 520;
    private static final int PANEL_HEIGHT = 420;
    private static final int TIER_CARD_WIDTH = 80;
    private static final int TIER_CARD_HEIGHT = 100;
    private static final int VISIBLE_TIERS = 5;

    // === Animation Timing (ms) ===
    private static final long FADE_IN_DURATION = 300;

    // === State ===
    private long openTime;
    private int scrollOffset = 0;
    private int highlightedTier = -1;

    // === Season Data (from ClientSeasonPassCache) ===
    private int currentTier = 1;
    private int currentXP = 0;
    private int xpPerTier = 1000;
    private int maxTier = 100;
    private String seasonName = "Season 1";
    private long remainingDays = 45;
    private boolean hasPremium = false;
    private List<ClientSeasonPassCache.RewardEntry> rewardEntries = List.of();

    public SeasonPassScreen() {
        super(Component.translatable("devmod.ui.season_pass.title"));
    }

    /**
     * Open the Season Pass screen.
     */
    public static void open() {
        Minecraft mc = Minecraft.getInstance();
        mc.setScreen(new SeasonPassScreen());
    }

    /**
     * Open with specific tier highlighted (from notification).
     */
    public static void openAtTier(int tier) {
        SeasonPassScreen screen = new SeasonPassScreen();
        screen.highlightedTier = tier;
        screen.scrollOffset = Math.max(0, tier - 3);
        Minecraft.getInstance().setScreen(screen);
    }

    @Override
    protected void init() {
        super.init();
        openTime = System.currentTimeMillis();
        loadSeasonData();
    }

    /**
     * Load season data from client-side cache and request sync from server.
     */
    private void loadSeasonData() {
        // Request fresh data from server
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.getConnection() != null) {
            PacketDistributor.sendToServer(new RequestSeasonPassPayload());
        }

        // Load from cache (may be stale until server responds)
        refreshFromCache();
    }

    /**
     * Refresh local state from cache.
     * Called initially and can be called when cache updates.
     */
    private void refreshFromCache() {
        ClientSeasonPassCache cache = ClientSeasonPassCache.INSTANCE;

        this.currentTier = cache.getCurrentTier();
        this.currentXP = cache.getCurrentTierXP();
        this.xpPerTier = cache.getXpPerTier();
        this.seasonName = cache.getSeasonName();
        this.remainingDays = cache.getRemainingDays();
        this.hasPremium = cache.isPremium();
        this.rewardEntries = cache.getUpcomingRewards();
    }

    @Override
    public void render(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        @Nonnull GuiGraphics g = Objects.requireNonNull(graphics, "graphics");
        @Nonnull Font f = safeFont();
        long elapsed = System.currentTimeMillis() - openTime;

        // Periodically refresh from cache to pick up server updates
        if (elapsed % 500 < 20) {
            refreshFromCache();
        }

        float fadeProgress = Math.min(1.0f, (float) elapsed / FADE_IN_DURATION);

        // Background
        renderBackground(g, mouseX, mouseY, partialTick);

        int centerX = width / 2;
        int centerY = height / 2;
        int panelX = centerX - PANEL_WIDTH / 2;
        int panelY = centerY - PANEL_HEIGHT / 2;

        // Panel background
        renderPanel(g, panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT, fadeProgress);

        // Header
        renderHeader(g, centerX, panelY, fadeProgress);

        // Progress bar
        renderProgressBar(g, panelX + 30, panelY + 85, PANEL_WIDTH - 60, 20, fadeProgress);

        // Tier cards
        renderTierCards(g, panelX + 30, panelY + 130, mouseX, mouseY, fadeProgress);

        // Legend
        renderLegend(g, panelX + 30, panelY + PANEL_HEIGHT - 60, fadeProgress);

        // Close hint
        int hintColor = applyAlpha(COLOR_TEXT_DIM, fadeProgress * 0.7f);
        g.drawCenteredString(f, "Press ESC to close", centerX, panelY + PANEL_HEIGHT - 20, hintColor);

        super.render(g, mouseX, mouseY, partialTick);
    }

    private void renderPanel(GuiGraphics g, int x, int y, int w, int h, float alpha) {
        // Outer glow
        int glowAlpha = (int) (0x33 * alpha);
        int glowColor = (glowAlpha << 24) | (COLOR_BORDER & 0x00FFFFFF);
        g.fill(x - 5, y - 5, x + w + 5, y + h + 5, glowColor);

        // Border
        int borderColor = applyAlpha(COLOR_BORDER, alpha);
        g.fill(x - 2, y - 2, x + w + 2, y + h + 2, borderColor);

        // Background gradient
        int topColor = applyAlpha(COLOR_BG_TOP, alpha);
        int bottomColor = applyAlpha(COLOR_BG_BOTTOM, alpha);
        for (int i = 0; i < h; i++) {
            float gradProgress = (float) i / h;
            int lineColor = lerpColor(topColor, bottomColor, gradProgress);
            g.fill(x, y + i, x + w, y + i + 1, lineColor);
        }

        // Inner highlight
        int highlightColor = applyAlpha(0x22FFFFFF, alpha);
        g.fill(x, y, x + w, y + 1, highlightColor);
    }

    private void renderHeader(GuiGraphics g, int centerX, int panelY, float alpha) {
        @Nonnull Font f = safeFont();

        // Title
        int titleColor = applyAlpha(COLOR_TITLE, alpha);
        g.pose().pushPose();
        g.pose().translate(centerX, panelY + 20, 0);
        g.pose().scale(1.8f, 1.8f, 1.0f);
        String title = Objects.requireNonNull(I18n.translate("devmod.ui.season_pass.title").getString());
        int titleWidth = f.width(title);
        g.drawString(f, title, -titleWidth / 2, 0, titleColor, true);
        g.pose().popPose();

        // Season name and days remaining
        int subtitleColor = applyAlpha(COLOR_SUBTITLE, alpha);
        String daysText = Objects.requireNonNull(I18n.translate("devmod.ui.season_pass.days_remaining").getString());
        String subtitle = seasonName + " - " + remainingDays + " " + daysText;
        g.drawCenteredString(f, subtitle, centerX, panelY + 50, subtitleColor);

        // Current tier display
        int tierColor = applyAlpha(COLOR_TEXT, alpha);
        String tierText = Objects.requireNonNull(I18n.translate("devmod.ui.season_pass.current_tier", currentTier).getString());
        g.drawCenteredString(f, tierText, centerX, panelY + 68, tierColor);
    }

    private void renderProgressBar(GuiGraphics g, int x, int y, int w, int h, float alpha) {
        @Nonnull Font f = safeFont();

        // Background
        int bgColor = applyAlpha(COLOR_PROGRESS_BG, alpha);
        g.fill(x, y, x + w, y + h, bgColor);

        // Progress fill
        float progress = (float) (currentXP % xpPerTier) / xpPerTier;
        int fillWidth = (int) (w * progress);
        int fillColor = applyAlpha(COLOR_PROGRESS_FILL, alpha);
        g.fill(x, y, x + fillWidth, y + h, fillColor);

        // Border
        int borderColor = applyAlpha(COLOR_BORDER, alpha * 0.5f);
        g.fill(x, y, x + w, y + 1, borderColor);
        g.fill(x, y + h - 1, x + w, y + h, borderColor);
        g.fill(x, y, x + 1, y + h, borderColor);
        g.fill(x + w - 1, y, x + w, y + h, borderColor);

        // XP text
        int textColor = applyAlpha(COLOR_TEXT, alpha);
        String xpText = (currentXP % xpPerTier) + " / " + xpPerTier + " XP";
        int textWidth = f.width(xpText);
        g.drawString(f, xpText, x + (w - textWidth) / 2, y + (h - 8) / 2, textColor, true);
    }

    private void renderTierCards(GuiGraphics g, int startX, int startY, int mouseX, int mouseY, float alpha) {
        @Nonnull Font f = safeFont();
        int cardSpacing = 10;
        int totalWidth = VISIBLE_TIERS * (TIER_CARD_WIDTH + cardSpacing) - cardSpacing;
        int offsetX = (PANEL_WIDTH - 60 - totalWidth) / 2;

        for (int i = 0; i < VISIBLE_TIERS; i++) {
            int tier = scrollOffset + i + 1;
            if (tier > maxTier) break;

            int cardX = startX + offsetX + i * (TIER_CARD_WIDTH + cardSpacing);
            int cardY = startY;

            boolean isUnlocked = tier <= currentTier;
            boolean isHighlighted = tier == highlightedTier;
            boolean isHovered = mouseX >= cardX && mouseX < cardX + TIER_CARD_WIDTH &&
                               mouseY >= cardY && mouseY < cardY + TIER_CARD_HEIGHT;

            renderTierCard(g, cardX, cardY, tier, isUnlocked, isHighlighted, isHovered, alpha);
        }

        // Scroll arrows
        if (scrollOffset > 0) {
            int arrowColor = applyAlpha(COLOR_TEXT, alpha);
            g.drawString(f, "<", startX + 5, startY + TIER_CARD_HEIGHT / 2, arrowColor, false);
        }
        if (scrollOffset + VISIBLE_TIERS < maxTier) {
            int arrowColor = applyAlpha(COLOR_TEXT, alpha);
            g.drawString(f, ">", startX + PANEL_WIDTH - 70, startY + TIER_CARD_HEIGHT / 2, arrowColor, false);
        }
    }

    private void renderTierCard(GuiGraphics g, int x, int y, int tier, boolean unlocked,
                                 boolean highlighted, boolean hovered, float alpha) {
        @Nonnull Font f = safeFont();

        // Card background
        int bgColor;
        if (highlighted) {
            bgColor = applyAlpha(COLOR_BORDER, alpha * 0.3f);
        } else if (hovered) {
            bgColor = applyAlpha(0x444444, alpha);
        } else if (unlocked) {
            bgColor = applyAlpha(0x333333, alpha);
        } else {
            bgColor = applyAlpha(COLOR_LOCKED, alpha * 0.5f);
        }
        g.fill(x, y, x + TIER_CARD_WIDTH, y + TIER_CARD_HEIGHT, bgColor);

        // Highlighted border
        if (highlighted) {
            int borderColor = applyAlpha(COLOR_BORDER, alpha);
            g.fill(x, y, x + TIER_CARD_WIDTH, y + 2, borderColor);
            g.fill(x, y + TIER_CARD_HEIGHT - 2, x + TIER_CARD_WIDTH, y + TIER_CARD_HEIGHT, borderColor);
            g.fill(x, y, x + 2, y + TIER_CARD_HEIGHT, borderColor);
            g.fill(x + TIER_CARD_WIDTH - 2, y, x + TIER_CARD_WIDTH, y + TIER_CARD_HEIGHT, borderColor);
        }

        // Tier number
        int tierColor = unlocked ? applyAlpha(COLOR_TITLE, alpha) : applyAlpha(COLOR_LOCKED, alpha);
        g.pose().pushPose();
        g.pose().translate(x + TIER_CARD_WIDTH / 2.0, y + 15, 0);
        g.pose().scale(1.5f, 1.5f, 1.0f);
        String tierNum = Objects.requireNonNull(String.valueOf(tier));
        int numWidth = f.width(tierNum);
        g.drawString(f, tierNum, -numWidth / 2, 0, tierColor, true);
        g.pose().popPose();

        // Get reward entry for this tier if available
        ClientSeasonPassCache.RewardEntry rewardEntry = findRewardEntry(tier);

        // Free track reward
        int freeY = y + 40;
        int freeColor = unlocked ? applyAlpha(COLOR_FREE_TRACK, alpha) : applyAlpha(COLOR_LOCKED, alpha);
        g.fill(x + 5, freeY, x + TIER_CARD_WIDTH - 5, freeY + 20, applyAlpha(COLOR_FREE_TRACK, alpha * 0.2f));
        String freeLabel = rewardEntry != null && !rewardEntry.getSafeFreeRewardName().isEmpty()
            ? Objects.requireNonNull(truncateLabel(rewardEntry.getSafeFreeRewardName(), TIER_CARD_WIDTH - 12))
            : (tier % 5 == 0 ? "Reward" : "-");
        int freeLabelWidth = f.width(freeLabel);
        g.drawString(f, freeLabel, x + (TIER_CARD_WIDTH - freeLabelWidth) / 2, freeY + 6, freeColor, false);

        // Premium track reward
        int premiumY = y + 65;
        int premiumColor = (unlocked && hasPremium) ? applyAlpha(COLOR_PREMIUM_TRACK, alpha)
                                                     : applyAlpha(COLOR_LOCKED, alpha);
        g.fill(x + 5, premiumY, x + TIER_CARD_WIDTH - 5, premiumY + 20, applyAlpha(COLOR_PREMIUM_TRACK, alpha * 0.2f));
        String premiumLabel = rewardEntry != null && !rewardEntry.getSafePremiumRewardName().isEmpty()
            ? Objects.requireNonNull(truncateLabel(rewardEntry.getSafePremiumRewardName(), TIER_CARD_WIDTH - 12))
            : "Premium";
        int premiumLabelWidth = f.width(premiumLabel);
        g.drawString(f, premiumLabel, x + (TIER_CARD_WIDTH - premiumLabelWidth) / 2, premiumY + 6, premiumColor, false);

        // Lock icon for locked tiers
        if (!unlocked) {
            int lockColor = applyAlpha(COLOR_LOCKED, alpha);
            g.drawCenteredString(f, "\uD83D\uDD12", x + TIER_CARD_WIDTH / 2, y + TIER_CARD_HEIGHT - 15, lockColor);
        }
    }

    private void renderLegend(GuiGraphics g, int x, int y, float alpha) {
        @Nonnull Font f = safeFont();

        // Free track legend
        int freeBoxColor = applyAlpha(COLOR_FREE_TRACK, alpha);
        g.fill(x, y, x + 12, y + 12, freeBoxColor);
        int freeLabelColor = applyAlpha(COLOR_TEXT, alpha);
        String freeTrackText = Objects.requireNonNull(I18n.translate("devmod.ui.season_pass.free_track").getString());
        g.drawString(f, freeTrackText, x + 18, y + 2, freeLabelColor, false);

        // Premium track legend
        int premiumBoxColor = applyAlpha(COLOR_PREMIUM_TRACK, alpha);
        g.fill(x + 120, y, x + 132, y + 12, premiumBoxColor);
        int premiumLabelColor = applyAlpha(COLOR_TEXT, alpha);
        String premiumText = Objects.requireNonNull(I18n.translate("devmod.ui.season_pass.premium_track").getString());
        if (!hasPremium) {
            String lockedText = Objects.requireNonNull(I18n.translate("devmod.ui.season_pass.locked").getString());
            premiumText += " (" + lockedText + ")";
        }
        g.drawString(f, premiumText, x + 138, y + 2, premiumLabelColor, false);
    }

    // === Scroll handling ===

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY > 0 && scrollOffset > 0) {
            scrollOffset--;
            return true;
        } else if (scrollY < 0 && scrollOffset + VISIBLE_TIERS < maxTier) {
            scrollOffset++;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT && scrollOffset > 0) {
            scrollOffset--;
            return true;
        }
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT && scrollOffset + VISIBLE_TIERS < maxTier) {
            scrollOffset++;
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // === Helper Methods ===

    @Nonnull
    private Font safeFont() {
        return Objects.requireNonNull(font, "font");
    }

    /**
     * Find reward entry for a specific tier from cached data.
     */
    @javax.annotation.Nullable
    private ClientSeasonPassCache.RewardEntry findRewardEntry(int tier) {
        for (ClientSeasonPassCache.RewardEntry entry : rewardEntries) {
            if (entry.tier() == tier) {
                return entry;
            }
        }
        return null;
    }

    /**
     * Truncate label to fit within max pixel width.
     */
    private String truncateLabel(String label, int maxPixelWidth) {
        if (label == null || label.isEmpty()) return "";
        Font f = safeFont();
        if (f.width(label) <= maxPixelWidth) {
            return label;
        }
        // Truncate and add ellipsis
        String ellipsis = "...";
        int ellipsisWidth = f.width(ellipsis);
        int availableWidth = maxPixelWidth - ellipsisWidth;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < label.length(); i++) {
            char c = label.charAt(i);
            if (f.width(sb.toString() + c) > availableWidth) {
                break;
            }
            sb.append(c);
        }
        return sb + ellipsis;
    }

    private static int applyAlpha(int color, float alpha) {
        int a = (int) (((color >> 24) & 0xFF) * alpha);
        return (a << 24) | (color & 0x00FFFFFF);
    }

    private static int lerpColor(int color1, int color2, float t) {
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

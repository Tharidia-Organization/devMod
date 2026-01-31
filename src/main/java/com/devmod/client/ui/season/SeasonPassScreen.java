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
import com.devmod.client.ui.core.UIScaleManager;
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
    private static final int COLOR_BG_TOP = DesignTokens.SeasonPass.BG_TOP;
    private static final int COLOR_BG_BOTTOM = DesignTokens.SeasonPass.BG_BOTTOM;
    private static final int COLOR_BORDER = DesignTokens.SeasonPass.BORDER;
    private static final int COLOR_TITLE = DesignTokens.SeasonPass.TITLE;
    private static final int COLOR_SUBTITLE = DesignTokens.SeasonPass.SUBTITLE;
    private static final int COLOR_TEXT = DesignTokens.Text.PRIMARY;
    private static final int COLOR_TEXT_DIM = DesignTokens.Text.SECONDARY;
    private static final int COLOR_FREE_TRACK = DesignTokens.SeasonPass.FREE_TRACK;
    private static final int COLOR_PREMIUM_TRACK = DesignTokens.SeasonPass.PREMIUM_TRACK;
    private static final int COLOR_LOCKED = DesignTokens.SeasonPass.LOCKED;
    private static final int COLOR_PROGRESS_BG = DesignTokens.SeasonPass.PROGRESS_BG;
    private static final int COLOR_PROGRESS_FILL = DesignTokens.SeasonPass.PROGRESS_FILL;
    private static final int COLOR_CLAIMED = DesignTokens.SeasonPass.CLAIMED;
    private static final int COLOR_BOOST = DesignTokens.SeasonPass.BOOST;
    private static final int COLOR_BADGE = DesignTokens.SeasonPass.BADGE;
    private static final int COLOR_INACTIVE = DesignTokens.SeasonPass.INACTIVE;

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
    private int seasonNumber = 1;
    private int currentTier = 1;
    private int currentXP = 0;
    private int rawCurrentXP = 0;
    private int xpToNextTier = 1000;
    private int xpPerTier = 1000;
    private float tierProgress = 0.0f;
    private int maxTier = 100;
    private String seasonName = "Season 1";
    private long remainingDays = 45;
    private boolean seasonActive = true;
    private boolean hasPremium = false;
    private int unclaimedRewards = 0;
    private boolean hasBoost = false;
    private float boostMultiplier = 1.0f;
    private long boostRemainingSeconds = 0;
    private boolean synced = false;
    private long lastSyncTime = 0;
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

        // Season info
        this.seasonNumber = cache.getSeasonNumber();
        this.seasonName = cache.getSeasonName();
        this.remainingDays = cache.getRemainingDays();
        this.seasonActive = cache.isSeasonActive();

        // Player progress
        this.currentTier = cache.getCurrentTier();
        this.currentXP = cache.getCurrentTierXP();
        this.rawCurrentXP = cache.getCurrentXP();
        this.xpToNextTier = cache.getXpToNextTier();
        this.xpPerTier = cache.getXpPerTier();
        this.tierProgress = cache.getTierProgress();
        this.hasPremium = cache.isPremium();
        this.unclaimedRewards = cache.getUnclaimedRewards();

        // XP Boost
        this.hasBoost = cache.hasBoost();
        this.boostMultiplier = cache.getBoostMultiplier();
        this.boostRemainingSeconds = cache.getBoostRemainingSeconds();

        // Sync state
        this.synced = cache.isSynced();
        this.lastSyncTime = cache.getLastSyncTime();

        // Rewards
        this.rewardEntries = cache.getUpcomingRewards();
    }

    @Override
    public void render(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        UIScaleManager.update();
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

        // Sync status and close hint
        int hintColor = applyAlpha(COLOR_TEXT_DIM, fadeProgress * 0.7f);
        String syncStatus = "";
        if (synced && lastSyncTime > 0) {
            long secondsAgo = (System.currentTimeMillis() - lastSyncTime) / 1000;
            if (secondsAgo < 60) {
                syncStatus = " | Synced " + secondsAgo + "s ago";
            } else {
                syncStatus = " | Synced " + (secondsAgo / 60) + "m ago";
            }
        } else if (!synced) {
            syncStatus = " | Not synced";
        }
        g.drawCenteredString(f, "Press ESC to close" + syncStatus, centerX, panelY + PANEL_HEIGHT - 20, hintColor);

        super.render(g, mouseX, mouseY, partialTick);
    }

    private void renderPanel(GuiGraphics g, int x, int y, int w, int h, float alpha) {
        // Outer glow
        int glowAlpha = (int) (DesignTokens.Alpha.A20 * alpha);
        int glowColor = (glowAlpha << 24) | (COLOR_BORDER & DesignTokens.Mask.RGB);
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
        int highlightColor = applyAlpha(DesignTokens.SeasonPass.HIGHLIGHT, alpha);
        g.fill(x, y, x + w, y + 1, highlightColor);
    }

    private void renderHeader(GuiGraphics g, int centerX, int panelY, float alpha) {
        @Nonnull Font f = safeFont();

        // Title with season number
        int titleColor = applyAlpha(COLOR_TITLE, alpha);
        g.pose().pushPose();
        g.pose().translate(centerX, panelY + 20, 0);
        g.pose().scale(1.8f, 1.8f, 1.0f);
        String title = Objects.requireNonNull(I18n.translate("devmod.ui.season_pass.title").getString());
        int titleWidth = f.width(title);
        g.drawString(f, title, -titleWidth / 2, 0, titleColor, true);
        g.pose().popPose();

        // Season name, number, and days remaining
        int subtitleColor = seasonActive ? applyAlpha(COLOR_SUBTITLE, alpha) : applyAlpha(COLOR_INACTIVE, alpha);
        String daysText = Objects.requireNonNull(I18n.translate("devmod.ui.season_pass.days_remaining").getString());
        String seasonStatus = seasonActive ? "" : " [ENDED]";
        String subtitle = seasonName + " (#" + seasonNumber + ") - " + remainingDays + " " + daysText + seasonStatus;
        g.drawCenteredString(f, subtitle, centerX, panelY + 50, subtitleColor);

        // Current tier and XP display
        int tierColor = applyAlpha(COLOR_TEXT, alpha);
        String tierText = Objects.requireNonNull(I18n.translate("devmod.ui.season_pass.current_tier", currentTier).getString());
        tierText += " | " + rawCurrentXP + " XP (" + xpToNextTier + " to next)";
        g.drawCenteredString(f, tierText, centerX, panelY + 68, tierColor);

        // Unclaimed rewards badge (top right)
        if (unclaimedRewards > 0) {
            int badgeX = centerX + PANEL_WIDTH / 2 - 50;
            int badgeY = panelY + 15;
            int badgeColor = applyAlpha(COLOR_BADGE, alpha);
            g.fill(badgeX, badgeY, badgeX + 40, badgeY + 18, badgeColor);
            String badgeText = unclaimedRewards + " NEW";
            int badgeTextWidth = f.width(badgeText);
            g.drawString(f, badgeText, badgeX + (40 - badgeTextWidth) / 2, badgeY + 5, applyAlpha(DesignTokens.Text.WHITE, alpha), true);
        }

        // XP Boost indicator (below title if active)
        if (hasBoost && boostRemainingSeconds > 0) {
            int boostColor = applyAlpha(COLOR_BOOST, alpha);
            long minutes = boostRemainingSeconds / 60;
            long seconds = boostRemainingSeconds % 60;
            String boostText = Objects.requireNonNull(String.format("XP BOOST x%.1f (%d:%02d)", boostMultiplier, minutes, seconds));
            g.drawCenteredString(f, boostText, centerX, panelY + 38, boostColor);
        }
    }

    private void renderProgressBar(GuiGraphics g, int x, int y, int w, int h, float alpha) {
        @Nonnull Font f = safeFont();

        // Background
        int bgColor = applyAlpha(COLOR_PROGRESS_BG, alpha);
        g.fill(x, y, x + w, y + h, bgColor);

        // Progress fill - use tierProgress directly from cache for accuracy
        float progress = tierProgress > 0 ? tierProgress : (xpPerTier > 0 ? (float) currentXP / xpPerTier : 0);
        int fillWidth = (int) (w * Math.min(1.0f, progress));
        int fillColor = applyAlpha(COLOR_PROGRESS_FILL, alpha);
        g.fill(x, y, x + fillWidth, y + h, fillColor);

        // Border
        int borderColor = applyAlpha(COLOR_BORDER, alpha * 0.5f);
        g.fill(x, y, x + w, y + 1, borderColor);
        g.fill(x, y + h - 1, x + w, y + h, borderColor);
        g.fill(x, y, x + 1, y + h, borderColor);
        g.fill(x + w - 1, y, x + w, y + h, borderColor);

        // XP text with sync indicator
        int textColor = applyAlpha(COLOR_TEXT, alpha);
        String xpText = currentXP + " / " + xpPerTier + " XP";
        if (!synced) {
            xpText = "Loading...";
            textColor = applyAlpha(COLOR_TEXT_DIM, alpha);
        }
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
            bgColor = applyAlpha(DesignTokens.SeasonPass.ROW_BG, alpha);
        } else if (unlocked) {
            bgColor = applyAlpha(DesignTokens.SeasonPass.ROW_BG_ALT, alpha);
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

        // Determine claim states
        boolean freeUnlocked = rewardEntry != null && rewardEntry.freeUnlocked();
        boolean freeClaimed = rewardEntry != null && rewardEntry.freeClaimed();
        boolean premiumUnlocked = rewardEntry != null && rewardEntry.premiumUnlocked();
        boolean premiumClaimed = rewardEntry != null && rewardEntry.premiumClaimed();

        // Free track reward
        int freeY = y + 40;
        int freeColor;
        if (freeClaimed) {
            freeColor = applyAlpha(COLOR_CLAIMED, alpha);
        } else if (freeUnlocked) {
            freeColor = applyAlpha(COLOR_FREE_TRACK, alpha);
        } else {
            freeColor = applyAlpha(COLOR_LOCKED, alpha);
        }
        g.fill(x + 5, freeY, x + TIER_CARD_WIDTH - 5, freeY + 20, applyAlpha(COLOR_FREE_TRACK, alpha * 0.2f));
        String freeLabel = rewardEntry != null && !rewardEntry.getSafeFreeRewardName().isEmpty()
            ? Objects.requireNonNull(truncateLabel(rewardEntry.getSafeFreeRewardName(), TIER_CARD_WIDTH - 18))
            : (tier % 5 == 0 ? "Reward" : "-");

        // Show checkmark for claimed free rewards
        if (freeClaimed) {
            g.drawString(f, "\u2713", x + 7, freeY + 6, applyAlpha(COLOR_CLAIMED, alpha), false);
            g.drawString(f, freeLabel, x + 18, freeY + 6, freeColor, false);
        } else {
            int freeLabelWidth = f.width(freeLabel);
            g.drawString(f, freeLabel, x + (TIER_CARD_WIDTH - freeLabelWidth) / 2, freeY + 6, freeColor, false);
        }

        // Premium track reward
        int premiumY = y + 65;
        int premiumColor;
        if (premiumClaimed) {
            premiumColor = applyAlpha(COLOR_CLAIMED, alpha);
        } else if (premiumUnlocked && hasPremium) {
            premiumColor = applyAlpha(COLOR_PREMIUM_TRACK, alpha);
        } else {
            premiumColor = applyAlpha(COLOR_LOCKED, alpha);
        }
        g.fill(x + 5, premiumY, x + TIER_CARD_WIDTH - 5, premiumY + 20, applyAlpha(COLOR_PREMIUM_TRACK, alpha * 0.2f));
        String premiumLabel = rewardEntry != null && !rewardEntry.getSafePremiumRewardName().isEmpty()
            ? Objects.requireNonNull(truncateLabel(rewardEntry.getSafePremiumRewardName(), TIER_CARD_WIDTH - 18))
            : "Premium";

        // Show checkmark for claimed premium rewards
        if (premiumClaimed) {
            g.drawString(f, "\u2713", x + 7, premiumY + 6, applyAlpha(COLOR_CLAIMED, alpha), false);
            g.drawString(f, premiumLabel, x + 18, premiumY + 6, premiumColor, false);
        } else {
            int premiumLabelWidth = f.width(premiumLabel);
            g.drawString(f, premiumLabel, x + (TIER_CARD_WIDTH - premiumLabelWidth) / 2, premiumY + 6, premiumColor, false);
        }

        // Lock icon for locked tiers, or claim indicator for unclaimed
        if (!unlocked) {
            int lockColor = applyAlpha(COLOR_LOCKED, alpha);
            g.drawCenteredString(f, "\uD83D\uDD12", x + TIER_CARD_WIDTH / 2, y + TIER_CARD_HEIGHT - 15, lockColor);
        } else if ((freeUnlocked && !freeClaimed) || (premiumUnlocked && hasPremium && !premiumClaimed)) {
            // Show exclamation for claimable rewards
            int claimColor = applyAlpha(COLOR_BADGE, alpha);
            g.drawCenteredString(f, "!", x + TIER_CARD_WIDTH / 2, y + TIER_CARD_HEIGHT - 15, claimColor);
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
        return (a << 24) | (color & DesignTokens.Mask.RGB);
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

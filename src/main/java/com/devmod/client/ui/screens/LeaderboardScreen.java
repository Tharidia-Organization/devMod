package com.devmod.client.ui.screens;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nonnull;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

import com.devmod.client.endurance.ClientLeaderboardCache;
import com.devmod.client.ui.AxiomRenderer;
import com.devmod.client.ui.core.UIScaleManager;
import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.client.ui.overlay.OverlayTheme;
import com.devmod.endurance.RequestLeaderboardPayload;
import com.devmod.stats.LeaderboardEntry;
import com.devmod.util.I18n;

/**
 * Leaderboard screen showing competitive rankings.
 * Tabs: Arena, Style, Quests, Overall.
 */
@OnlyIn(Dist.CLIENT)
public class LeaderboardScreen extends Screen {

    private static final int PANEL_WIDTH = 420;
    private static final int TAB_HEIGHT = 22;
    private static final int TAB_GAP = 2;
    private static final int PADDING = 8;
    private static final int ROW_HEIGHT = 16;
    private static final int HEADER_ROW_HEIGHT = 18;

    // Medal colors
    private static final int COLOR_GOLD = 0xFFFFD700;
    private static final int COLOR_SILVER = 0xFFC0C0C0;
    private static final int COLOR_BRONZE = 0xFFCD7F32;

    private enum Tab { ARENA, STYLE, QUESTS, OVERALL }

    private Tab activeTab = Tab.ARENA;
    private int panelX, panelY, panelW, panelH;
    private int contentY;
    private int scrollOffset = 0;
    private int maxScroll = 0;

    // Tab regions
    private final int[] tabX = new int[4];
    private final int[] tabW = new int[4];
    private int tabY;

    // Cached data (populated from server via ClientLeaderboardCache)
    private List<LeaderboardEntry> arenaEntries = List.of();
    private List<LeaderboardEntry> styleEntries = List.of();
    private List<LeaderboardEntry> questEntries = List.of();

    public LeaderboardScreen() {
        super(I18n.screenTitle("leaderboard"));
        // Load from client cache if available, then request fresh data from server
        refreshFromCache();
        PacketDistributor.sendToServer(new RequestLeaderboardPayload());
    }

    @Override
    protected void init() {
        panelW = UIScaleManager.scale(PANEL_WIDTH);
        panelH = this.height - UIScaleManager.scale(40);
        panelX = (this.width - panelW) / 2;
        panelY = UIScaleManager.scale(20);

        int scaledTabH = UIScaleManager.scale(TAB_HEIGHT);
        tabY = panelY;
        contentY = panelY + scaledTabH + 1;

        int tabCount = Tab.values().length;
        int gap = UIScaleManager.scale(TAB_GAP);
        int totalGaps = gap * (tabCount - 1);
        int singleTabW = (panelW - totalGaps) / tabCount;
        for (int i = 0; i < tabCount; i++) {
            tabX[i] = panelX + i * (singleTabW + gap);
            tabW[i] = singleTabW;
        }

        scrollOffset = 0;
    }

    @Override
    public void render(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        UIScaleManager.update();
        refreshFromCache();
        Font f = Objects.requireNonNull(font, "font");

        AxiomRenderer.drawScreenBackground(graphics, this.width, this.height);

        // Panel
        graphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, DesignTokens.Background.PANEL);
        AxiomRenderer.drawSimplePanel(graphics, panelX, panelY, panelW, panelH);

        // Tabs
        renderTabs(graphics, f, mouseX, mouseY);

        // Content (clipped)
        int pad = UIScaleManager.scale(PADDING);
        int yourRankH = UIScaleManager.scale(30);
        int contentH = panelY + panelH - contentY - pad - yourRankH;
        graphics.enableScissor(panelX, contentY, panelX + panelW, contentY + contentH);

        int drawn = switch (activeTab) {
            case ARENA -> renderArenaTab(graphics, f, panelX, contentY - scrollOffset, panelW);
            case STYLE -> renderStyleTab(graphics, f, panelX, contentY - scrollOffset, panelW);
            case QUESTS -> renderQuestsTab(graphics, f, panelX, contentY - scrollOffset, panelW);
            case OVERALL -> renderOverallTab(graphics, f, panelX, contentY - scrollOffset, panelW);
        };

        maxScroll = Math.max(0, drawn - contentH);
        graphics.disableScissor();

        // Scrollbar
        if (maxScroll > 0) {
            renderScrollbar(graphics, panelX + panelW - UIScaleManager.scale(3), contentY, UIScaleManager.scale(3), contentH, drawn);
        }

        // "Your Rank" section at bottom
        int yourRankY = contentY + contentH + UIScaleManager.scale(2);
        renderYourRank(graphics, f, panelX, yourRankY, panelW, yourRankH);

        // Footer hint
        int hintY = panelY + panelH + UIScaleManager.scale(4);
        String hint = "ESC to close";
        UIScaleManager.drawScaledString(graphics, f, hint, (this.width - UIScaleManager.getScaledStringWidth(f, hint)) / 2, hintY, OverlayTheme.Text.HINT, false);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // TABS
    // ═══════════════════════════════════════════════════════════════════════════

    private void renderTabs(GuiGraphics graphics, Font f, int mx, int my) {
        String[] labels = {
            t("devmod.leaderboard.tab.arena"),
            t("devmod.leaderboard.tab.style"),
            t("devmod.leaderboard.tab.quests"),
            t("devmod.leaderboard.tab.overall")
        };
        Tab[] tabs = Tab.values();
        int scaledTabH = UIScaleManager.scale(TAB_HEIGHT);

        for (int i = 0; i < tabs.length; i++) {
            boolean selected = activeTab == tabs[i];
            boolean hovered = mx >= tabX[i] && mx < tabX[i] + tabW[i] && my >= tabY && my < tabY + scaledTabH;
            AxiomRenderer.drawTab(graphics, f, tabX[i], tabY, tabW[i], scaledTabH, labels[i], selected, hovered);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ARENA TAB
    // ═══════════════════════════════════════════════════════════════════════════

    private int renderArenaTab(GuiGraphics graphics, Font f, int x, int startY, int w) {
        int pad = UIScaleManager.scale(PADDING);
        int cx = x + pad;
        int cw = w - pad * 2;
        int y = startY + pad;

        // Column headers
        String[] headers = {"#", t("devmod.leaderboard.col.player"), t("devmod.leaderboard.col.best_wave"), t("devmod.leaderboard.col.time"), t("devmod.leaderboard.col.score")};
        int[] colW = computeColumns(cw, new float[]{0.08f, 0.30f, 0.20f, 0.20f, 0.22f});
        y = drawTableHeader(graphics, f, cx, y, colW, headers);

        // Rows
        UUID localId = getLocalPlayerId();
        for (LeaderboardEntry entry : arenaEntries) {
            boolean isLocal = entry.playerId().equals(localId);
            String[] cells = {
                String.valueOf(entry.rank()),
                entry.playerName(),
                String.valueOf(entry.score()),
                formatTime(entry.secondary()),
                String.format("%,d", entry.tertiary())
            };
            y = drawTableRow(graphics, f, cx, y, colW, cells, entry.rank(), isLocal);
        }

        y += pad;
        return y - startY;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // STYLE TAB
    // ═══════════════════════════════════════════════════════════════════════════

    private int renderStyleTab(GuiGraphics graphics, Font f, int x, int startY, int w) {
        int pad = UIScaleManager.scale(PADDING);
        int cx = x + pad;
        int cw = w - pad * 2;
        int y = startY + pad;

        String[] headers = {"#", t("devmod.leaderboard.col.player"), t("devmod.leaderboard.col.best_combo"), t("devmod.leaderboard.col.best_rank"), t("devmod.leaderboard.col.total_style")};
        int[] colW = computeColumns(cw, new float[]{0.08f, 0.28f, 0.20f, 0.18f, 0.26f});
        y = drawTableHeader(graphics, f, cx, y, colW, headers);

        UUID localId = getLocalPlayerId();
        for (LeaderboardEntry entry : styleEntries) {
            boolean isLocal = entry.playerId().equals(localId);
            String[] cells = {
                String.valueOf(entry.rank()),
                entry.playerName(),
                String.valueOf(entry.score()),
                LeaderboardEntry.styleRankName(entry.secondary()),
                String.format("%,d", entry.tertiary())
            };
            y = drawTableRow(graphics, f, cx, y, colW, cells, entry.rank(), isLocal);
        }

        y += pad;
        return y - startY;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // QUESTS TAB
    // ═══════════════════════════════════════════════════════════════════════════

    private int renderQuestsTab(GuiGraphics graphics, Font f, int x, int startY, int w) {
        int pad = UIScaleManager.scale(PADDING);
        int cx = x + pad;
        int cw = w - pad * 2;
        int y = startY + pad;

        String[] headers = {"#", t("devmod.leaderboard.col.player"), t("devmod.leaderboard.col.quests_done"), t("devmod.leaderboard.col.unique_quests")};
        int[] colW = computeColumns(cw, new float[]{0.08f, 0.35f, 0.28f, 0.29f});
        y = drawTableHeader(graphics, f, cx, y, colW, headers);

        UUID localId = getLocalPlayerId();
        for (LeaderboardEntry entry : questEntries) {
            boolean isLocal = entry.playerId().equals(localId);
            String[] cells = {
                String.valueOf(entry.rank()),
                entry.playerName(),
                String.valueOf(entry.score()),
                String.valueOf(entry.secondary())
            };
            y = drawTableRow(graphics, f, cx, y, colW, cells, entry.rank(), isLocal);
        }

        y += pad;
        return y - startY;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // OVERALL TAB
    // ═══════════════════════════════════════════════════════════════════════════

    private int renderOverallTab(GuiGraphics graphics, Font f, int x, int startY, int w) {
        int pad = UIScaleManager.scale(PADDING);
        int cx = x + pad;
        int cw = w - pad * 2;
        int y = startY + pad;

        String[] headers = {"#", t("devmod.leaderboard.col.player"), t("devmod.leaderboard.col.arena_score"), t("devmod.leaderboard.col.style_score"), t("devmod.leaderboard.col.combined")};
        int[] colW = computeColumns(cw, new float[]{0.08f, 0.28f, 0.20f, 0.20f, 0.24f});
        y = drawTableHeader(graphics, f, cx, y, colW, headers);

        // Combine arena and style entries into overall scores
        UUID localId = getLocalPlayerId();
        java.util.Map<UUID, long[]> combined = new java.util.LinkedHashMap<>();

        for (LeaderboardEntry e : arenaEntries) {
            combined.computeIfAbsent(e.playerId(), k -> new long[]{0, 0, 0})[0] = e.tertiary();
            combined.get(e.playerId())[2] += e.tertiary();
        }
        for (LeaderboardEntry e : styleEntries) {
            combined.computeIfAbsent(e.playerId(), k -> new long[]{0, 0, 0})[1] = e.tertiary();
            combined.get(e.playerId())[2] += e.tertiary();
        }

        // Sort by combined score descending
        java.util.List<java.util.Map.Entry<UUID, long[]>> sorted = new java.util.ArrayList<>(combined.entrySet());
        sorted.sort((a, b) -> Long.compare(b.getValue()[2], a.getValue()[2]));

        int rank = 0;
        for (java.util.Map.Entry<UUID, long[]> entry : sorted) {
            rank++;
            UUID id = entry.getKey();
            long[] scores = entry.getValue();
            boolean isLocal = id.equals(localId);

            // Find player name from arena or style entries
            String playerName = findPlayerName(id);

            String[] cells = {
                String.valueOf(rank),
                playerName,
                String.format("%,d", scores[0]),
                String.format("%,d", scores[1]),
                String.format("%,d", scores[2])
            };
            y = drawTableRow(graphics, f, cx, y, colW, cells, rank, isLocal);
        }

        y += pad;
        return y - startY;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // YOUR RANK FOOTER
    // ═══════════════════════════════════════════════════════════════════════════

    private void renderYourRank(GuiGraphics graphics, Font f, int x, int y, int w, int h) {
        int pad = UIScaleManager.scale(PADDING);

        // Background
        graphics.fill(x, y, x + w, y + h, OverlayTheme.Panel.BG_HEADER);
        graphics.fill(x, y, x + w, y + 1, OverlayTheme.Border.ACCENT);

        UUID localId = getLocalPlayerId();
        List<LeaderboardEntry> entries = getActiveEntries();
        int rank = -1;
        long score = 0;
        for (LeaderboardEntry e : entries) {
            if (e.playerId().equals(localId)) {
                rank = e.rank();
                score = e.score();
                break;
            }
        }

        int textY = y + (h - UIScaleManager.getScaledLineHeight()) / 2;
        if (rank > 0) {
            String text = I18n.translate("devmod.leaderboard.your_rank", rank, String.format("%,d", score)).getString();
            UIScaleManager.drawScaledString(graphics, f, text, x + pad, textY, OverlayTheme.Text.TITLE, false);
        } else {
            UIScaleManager.drawScaledString(graphics, f, t("devmod.leaderboard.not_ranked"), x + pad, textY, OverlayTheme.Text.MUTED, false);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // TABLE RENDERING
    // ═══════════════════════════════════════════════════════════════════════════

    private int drawTableHeader(GuiGraphics graphics, Font f, int x, int y, int[] colW, String[] headers) {
        int rowH = UIScaleManager.scale(HEADER_ROW_HEIGHT);
        int totalW = 0;
        for (int w : colW) totalW += w;

        // Header background
        graphics.fill(x, y, x + totalW, y + rowH, OverlayTheme.Panel.BG_HEADER);
        graphics.fill(x, y + rowH - 1, x + totalW, y + rowH, OverlayTheme.Border.ACCENT);

        int textY = y + (rowH - UIScaleManager.getScaledLineHeight()) / 2;
        int colX = x;
        for (int i = 0; i < headers.length && i < colW.length; i++) {
            UIScaleManager.drawScaledString(graphics, f, headers[i], colX + UIScaleManager.scale(4), textY, OverlayTheme.Text.TITLE, false);
            colX += colW[i];
        }

        return y + rowH;
    }

    private int drawTableRow(GuiGraphics graphics, Font f, int x, int y, int[] colW, String[] cells, int rank, boolean isLocal) {
        int rowH = UIScaleManager.scale(ROW_HEIGHT);
        int totalW = 0;
        for (int w : colW) totalW += w;

        // Alternating row background
        int bgColor;
        if (isLocal) {
            bgColor = OverlayTheme.withAlpha(OverlayTheme.Border.ACCENT, 40);
        } else if (rank % 2 == 0) {
            bgColor = OverlayTheme.withAlpha(OverlayTheme.Utility.BLACK, 30);
        } else {
            bgColor = OverlayTheme.withAlpha(OverlayTheme.Utility.BLACK, 15);
        }
        graphics.fill(x, y, x + totalW, y + rowH, bgColor);

        // Highlight local player row border
        if (isLocal) {
            graphics.fill(x, y, x + 2, y + rowH, OverlayTheme.Border.ACCENT);
        }

        int textY = y + (rowH - UIScaleManager.getScaledLineHeight()) / 2;
        int colX = x;
        for (int i = 0; i < cells.length && i < colW.length; i++) {
            int color;
            if (i == 0) {
                // Rank column - medal colors
                color = rankColor(rank);
            } else if (isLocal) {
                color = OverlayTheme.Text.TITLE;
            } else {
                color = OverlayTheme.Text.PRIMARY;
            }
            UIScaleManager.drawScaledString(graphics, f, cells[i], colX + UIScaleManager.scale(4), textY, color, false);
            colX += colW[i];
        }

        return y + rowH;
    }

    private int rankColor(int rank) {
        return switch (rank) {
            case 1 -> COLOR_GOLD;
            case 2 -> COLOR_SILVER;
            case 3 -> COLOR_BRONZE;
            default -> OverlayTheme.Text.MUTED;
        };
    }

    private int[] computeColumns(int totalWidth, float[] ratios) {
        int[] cols = new int[ratios.length];
        int used = 0;
        for (int i = 0; i < ratios.length - 1; i++) {
            cols[i] = (int) (totalWidth * ratios[i]);
            used += cols[i];
        }
        cols[ratios.length - 1] = totalWidth - used;
        return cols;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // DATA
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Refreshes local entry lists from the client-side leaderboard cache.
     */
    private void refreshFromCache() {
        ClientLeaderboardCache cache = ClientLeaderboardCache.INSTANCE;
        if (cache.isLoaded()) {
            arenaEntries = cache.getArenaEntries();
            styleEntries = cache.getStyleEntries();
            questEntries = cache.getQuestEntries();
        }
    }

    /** Shorthand for translating a key to a string. */
    private static String t(String key) {
        return I18n.translate(key).getString();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════════════════════════════

    private void renderScrollbar(GuiGraphics graphics, int x, int y, int w, int viewH, int totalH) {
        if (totalH <= viewH) return;
        float thumbRatio = (float) viewH / totalH;
        int thumbH = Math.max(UIScaleManager.scale(10), (int) (viewH * thumbRatio));
        float scrollRatio = maxScroll > 0 ? (float) scrollOffset / maxScroll : 0;
        int thumbY = y + (int) ((viewH - thumbH) * scrollRatio);

        graphics.fill(x, y, x + w, y + viewH, OverlayTheme.withAlpha(OverlayTheme.Utility.BLACK, 60));
        graphics.fill(x, thumbY, x + w, thumbY + thumbH, OverlayTheme.withAlpha(OverlayTheme.Text.MUTED, 120));
    }

    private UUID getLocalPlayerId() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        return player != null ? player.getUUID() : new UUID(0, 0);
    }

    private String findPlayerName(UUID id) {
        for (LeaderboardEntry e : arenaEntries) {
            if (e.playerId().equals(id)) return e.playerName();
        }
        for (LeaderboardEntry e : styleEntries) {
            if (e.playerId().equals(id)) return e.playerName();
        }
        for (LeaderboardEntry e : questEntries) {
            if (e.playerId().equals(id)) return e.playerName();
        }
        return "Unknown";
    }

    private List<LeaderboardEntry> getActiveEntries() {
        return switch (activeTab) {
            case ARENA -> arenaEntries;
            case STYLE -> styleEntries;
            case QUESTS -> questEntries;
            case OVERALL -> arenaEntries; // Overall uses combined, but for rank lookup use arena
        };
    }

    private String formatTime(long totalSeconds) {
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return String.format("%d:%02d", minutes, seconds);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // INPUT
    // ═══════════════════════════════════════════════════════════════════════════

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);

        int scaledTabH = UIScaleManager.scale(TAB_HEIGHT);
        Tab[] tabs = Tab.values();
        for (int i = 0; i < tabs.length; i++) {
            if (mouseX >= tabX[i] && mouseX < tabX[i] + tabW[i] && mouseY >= tabY && mouseY < tabY + scaledTabH) {
                activeTab = tabs[i];
                scrollOffset = 0;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) (scrollY * UIScaleManager.scale(12))));
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, DesignTokens.Background.SCREEN);
    }

    @Override
    protected void renderBlurredBackground(float partialTick) {
        // Disable blur
    }

    @Override
    protected void renderMenuBackground(@Nonnull GuiGraphics graphics) {
        graphics.fill(0, 0, this.width, this.height, DesignTokens.Background.SCREEN);
    }
}

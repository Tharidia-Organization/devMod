package com.devmod.mailbox.client.screen;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

import com.devmod.client.ui.editor.components.EditorButton;
import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.client.ui.editor.core.UiSounds;
import com.devmod.mailbox.client.ClientNewsCache;
import com.devmod.mailbox.client.MailboxUiTheme;
import com.devmod.mailbox.network.payload.NewsReadPayload;
import com.devmod.mailbox.network.payload.NewsSyncPayload.NewsArticleData;

/**
 * News center screen for viewing announcements and updates.
 */
@OnlyIn(Dist.CLIENT)
public class NewsScreen extends Screen {
    // Layout constants
    private static final int PANEL_WIDTH = 650;
    private static final int PANEL_HEIGHT = 420;
    private static final int LIST_WIDTH = 220;
    private static final int DETAIL_WIDTH = PANEL_WIDTH - LIST_WIDTH - 30;

    // Date formatter
    private static final DateTimeFormatter DATE_FORMAT =
        DateTimeFormatter.ofPattern("MMM dd, yyyy", Locale.ROOT).withZone(ZoneId.systemDefault());

    // UI state
    private int panelX, panelY;
    private int scrollOffset = 0;
    private static final int VISIBLE_ARTICLES = 10;
    private static final int ARTICLE_HEIGHT = 36;

    @Nullable
    private UUID selectedArticleId = null;
    private int hoveredArticleIndex = -1;
    private int detailScrollOffset = 0;

    // Filter
    private int selectedCategory = -1; // -1 = all

    // Buttons
    @Nullable private EditorButton closeButton;
    @Nullable private EditorButton allButton;
    @Nullable private EditorButton patchButton;
    @Nullable private EditorButton eventButton;
    @Nullable private EditorButton announceButton;

    public NewsScreen() {
        super(Objects.requireNonNull(Component.translatable("devmod.news.title")));
    }

    @Nonnull
    private net.minecraft.client.gui.Font getFont() {
        return Objects.requireNonNull(this.font, "Font not initialized");
    }

    @Override
    protected void init() {
        super.init();

        panelX = (width - PANEL_WIDTH) / 2;
        panelY = (height - PANEL_HEIGHT) / 2;

        initButtons();

        // Select first unread article by default
        List<NewsArticleData> articles = getFilteredArticles();
        for (NewsArticleData article : articles) {
            if (!article.isRead()) {
                selectedArticleId = article.id();
                break;
            }
        }
        if (selectedArticleId == null && !articles.isEmpty()) {
            selectedArticleId = articles.get(0).id();
        }
    }

    private void initButtons() {
        // Close button
        closeButton = EditorButton.builder("close", "Close")
            .style(EditorButton.Style.GHOST)
            .size(EditorButton.Size.MEDIUM)
            .onClick(this::onClose)
            .build();

        // Category filter buttons
        allButton = EditorButton.builder("all", "All")
            .style(EditorButton.Style.PRIMARY)
            .size(EditorButton.Size.SMALL)
            .onClick(() -> setCategory(-1))
            .build();

        patchButton = EditorButton.builder("patch", "Patches")
            .style(EditorButton.Style.GHOST)
            .size(EditorButton.Size.SMALL)
            .onClick(() -> setCategory(0))
            .build();

        eventButton = EditorButton.builder("event", "Events")
            .style(EditorButton.Style.GHOST)
            .size(EditorButton.Size.SMALL)
            .onClick(() -> setCategory(1))
            .build();

        announceButton = EditorButton.builder("announce", "News")
            .style(EditorButton.Style.GHOST)
            .size(EditorButton.Size.SMALL)
            .onClick(() -> setCategory(2))
            .build();
    }

    private void setCategory(int category) {
        selectedCategory = category;
        scrollOffset = 0;
        selectedArticleId = null;

        // Select first article in new filter
        List<NewsArticleData> articles = getFilteredArticles();
        if (!articles.isEmpty()) {
            selectedArticleId = articles.get(0).id();
        }

        UiSounds.click();
    }

    private List<NewsArticleData> getFilteredArticles() {
        if (selectedCategory < 0) {
            return ClientNewsCache.getArticles();
        }
        return ClientNewsCache.getArticlesByCategory(selectedCategory);
    }

    @Override
    public void render(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);

        // Main panel
        renderMainPanel(graphics);

        // Header
        renderHeader(graphics);

        // Category filters
        renderCategoryFilters(graphics, mouseX, mouseY);

        // Article list
        hoveredArticleIndex = renderArticleList(graphics, mouseX, mouseY);

        // Article detail
        renderArticleDetail(graphics);

        // Close button
        renderButtons(graphics, mouseX, mouseY);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderMainPanel(GuiGraphics graphics) {
        // Background
        graphics.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, MailboxUiTheme.Panel.BG);

        // Border
        int borderColor = DesignTokens.Border.DEFAULT();
        graphics.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + 1, borderColor);
        graphics.fill(panelX, panelY + PANEL_HEIGHT - 1, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, borderColor);
        graphics.fill(panelX, panelY, panelX + 1, panelY + PANEL_HEIGHT, borderColor);
        graphics.fill(panelX + PANEL_WIDTH - 1, panelY, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, borderColor);

        // Divider between list and detail
        int dividerX = panelX + LIST_WIDTH + 10;
        graphics.fill(dividerX, panelY + 70, dividerX + 1, panelY + PANEL_HEIGHT - 45, MailboxUiTheme.Divider.LINE);
    }

    private void renderHeader(GuiGraphics graphics) {
        int headerY = panelY + 10;

        // Title with icon
        graphics.drawString(getFont(), "📰 News Center", panelX + 15, headerY, DesignTokens.Text.PRIMARY(), false);

        // Unread count
        int unread = ClientNewsCache.getUnreadCount();
        if (unread > 0) {
            String badge = "(" + unread + " new)";
            graphics.drawString(getFont(), badge, panelX + 120, headerY, DesignTokens.Accent.GOLD(), false);
        }

        // Separator
        graphics.fill(panelX + 10, panelY + 30, panelX + PANEL_WIDTH - 10, panelY + 31, DesignTokens.Border.DEFAULT());
    }

    private void renderCategoryFilters(GuiGraphics graphics, int mouseX, int mouseY) {
        int filterY = panelY + 38;
        int filterX = panelX + 15;
        int buttonWidth = 55;
        int spacing = 5;

        if (allButton != null) {
            allButton.render(graphics, filterX, filterY, buttonWidth, 18, mouseX, mouseY);
            filterX += buttonWidth + spacing;
        }
        if (patchButton != null) {
            patchButton.render(graphics, filterX, filterY, buttonWidth + 10, 18, mouseX, mouseY);
            filterX += buttonWidth + 10 + spacing;
        }
        if (eventButton != null) {
            eventButton.render(graphics, filterX, filterY, buttonWidth, 18, mouseX, mouseY);
            filterX += buttonWidth + spacing;
        }
        if (announceButton != null) {
            announceButton.render(graphics, filterX, filterY, buttonWidth, 18, mouseX, mouseY);
        }

        // Update button styles based on selection
        updateFilterButtonStyles();
    }

    private void updateFilterButtonStyles() {
        if (allButton != null) allButton.style(selectedCategory == -1 ? EditorButton.Style.PRIMARY : EditorButton.Style.GHOST);
        if (patchButton != null) patchButton.style(selectedCategory == 0 ? EditorButton.Style.PRIMARY : EditorButton.Style.GHOST);
        if (eventButton != null) eventButton.style(selectedCategory == 1 ? EditorButton.Style.PRIMARY : EditorButton.Style.GHOST);
        if (announceButton != null) announceButton.style(selectedCategory == 2 ? EditorButton.Style.PRIMARY : EditorButton.Style.GHOST);
    }

    private int renderArticleList(GuiGraphics graphics, int mouseX, int mouseY) {
        List<NewsArticleData> articles = getFilteredArticles();
        int listX = panelX + 10;
        int listY = panelY + 65;
        int listHeight = PANEL_HEIGHT - 110;

        // Empty state
        if (articles.isEmpty()) {
            String emptyText = "No articles";
            int textX = listX + (LIST_WIDTH - getFont().width(emptyText)) / 2;
            int textY = listY + listHeight / 2;
            graphics.drawString(getFont(), emptyText, textX, textY, DesignTokens.Text.MUTED(), false);
            return -1;
        }

        int hovered = -1;
        int visibleCount = Math.min(VISIBLE_ARTICLES, articles.size() - scrollOffset);

        for (int i = 0; i < visibleCount; i++) {
            int actualIndex = i + scrollOffset;
            if (actualIndex >= articles.size()) break;

            NewsArticleData article = articles.get(actualIndex);
            int itemY = listY + i * ARTICLE_HEIGHT;

            // Check hover
            boolean isHovered = mouseX >= listX && mouseX < listX + LIST_WIDTH &&
                               mouseY >= itemY && mouseY < itemY + ARTICLE_HEIGHT - 2;
            if (isHovered) {
                hovered = actualIndex;
            }

            // Selection/hover background
            boolean isSelected = article.id().equals(selectedArticleId);
            if (isSelected) {
                graphics.fill(listX, itemY, listX + LIST_WIDTH, itemY + ARTICLE_HEIGHT - 2, MailboxUiTheme.List.SELECTED_BG);
            } else if (isHovered) {
                graphics.fill(listX, itemY, listX + LIST_WIDTH, itemY + ARTICLE_HEIGHT - 2, MailboxUiTheme.List.HOVER_BG);
            }

            // Unread indicator
            if (!article.isRead()) {
                int dotX = listX + 5;
                int dotY = itemY + ARTICLE_HEIGHT / 2 - 3;
                graphics.fill(dotX, dotY, dotX + 6, dotY + 6, DesignTokens.Accent.GOLD());
            }

            // Category color bar
            int catColor = getCategoryColor(article.categoryOrdinal());
            graphics.fill(listX + 14, itemY + 4, listX + 17, itemY + ARTICLE_HEIGHT - 6, catColor);

            // Title
            String title = truncate(article.title(), 22);
            int textColor = article.isRead() ? DesignTokens.Text.MUTED() : DesignTokens.Text.PRIMARY();
            graphics.drawString(getFont(), title, listX + 22, itemY + 6, textColor, false);

            // Category + date
            String meta = article.getCategoryName() + " • " + DATE_FORMAT.format(Instant.ofEpochMilli(article.publishedAtMillis()));
            graphics.drawString(getFont(), truncate(meta, 26), listX + 22, itemY + 20, DesignTokens.Text.SECONDARY(), false);
        }

        // Scrollbar
        if (articles.size() > VISIBLE_ARTICLES) {
            int scrollbarX = listX + LIST_WIDTH - 4;
            int scrollbarH = listHeight - 4;
            int thumbHeight = Math.max(20, scrollbarH * VISIBLE_ARTICLES / articles.size());
            int thumbY = listY + 2 + (scrollbarH - thumbHeight) * scrollOffset / Math.max(1, articles.size() - VISIBLE_ARTICLES);

            graphics.fill(scrollbarX, listY + 2, scrollbarX + 3, listY + scrollbarH, MailboxUiTheme.Scrollbar.TRACK);
            graphics.fill(scrollbarX, thumbY, scrollbarX + 3, thumbY + thumbHeight, MailboxUiTheme.Scrollbar.THUMB);
        }

        return hovered;
    }

    private void renderArticleDetail(GuiGraphics graphics) {
        int detailX = panelX + LIST_WIDTH + 20;
        int detailY = panelY + 65;
        int detailHeight = PANEL_HEIGHT - 110;

        @Nullable NewsArticleData selected = selectedArticleId != null
            ? ClientNewsCache.getArticle(selectedArticleId)
            : null;

        if (selected == null) {
            String noSelection = "Select an article";
            int textX = detailX + (DETAIL_WIDTH - getFont().width(noSelection)) / 2;
            int textY = detailY + detailHeight / 2;
            graphics.drawString(getFont(), noSelection, textX, textY, DesignTokens.Text.MUTED(), false);
            return;
        }

        int y = detailY;

        // Category badge
        String category = Objects.requireNonNull(selected.getCategoryName(), "categoryName");
        int catColor = getCategoryColor(selected.categoryOrdinal());
        int catWidth = getFont().width(category) + 10;
        graphics.fill(detailX, y, detailX + catWidth, y + 14, catColor);
        graphics.drawString(getFont(), category, detailX + 5, y + 3, DesignTokens.Text.PRIMARY(), false);
        y += 20;

        // Title
        graphics.drawString(getFont(), selected.title(), detailX, y, DesignTokens.Text.PRIMARY(), false);
        y += 14;

        // Author and date
        String author = selected.authorName() != null ? selected.authorName() : "DevMod Team";
        String dateStr = DATE_FORMAT.format(Instant.ofEpochMilli(selected.publishedAtMillis()));
        String meta = "By " + author + " • " + dateStr;
        graphics.drawString(getFont(), meta, detailX, y, DesignTokens.Text.SECONDARY(), false);
        y += 20;

        // Separator
        graphics.fill(detailX, y, detailX + DETAIL_WIDTH, y + 1, DesignTokens.Border.DEFAULT());
        y += 10;

        // Content
        List<String> lines = wrapText(selected.content(), DETAIL_WIDTH - 10);
        int maxY = detailY + detailHeight - 20;

        for (int i = detailScrollOffset; i < lines.size() && y < maxY; i++) {
            graphics.drawString(getFont(), lines.get(i), detailX, y, DesignTokens.Text.PRIMARY(), false);
            y += 12;
        }

        // Scroll indicator
        if (lines.size() > (maxY - detailY - 60) / 12) {
            if (detailScrollOffset > 0) {
                graphics.drawString(getFont(), "▲", detailX + DETAIL_WIDTH - 15, detailY, DesignTokens.Text.MUTED(), false);
            }
            if (detailScrollOffset < lines.size() - (maxY - detailY - 60) / 12) {
                graphics.drawString(getFont(), "▼", detailX + DETAIL_WIDTH - 15, maxY - 10, DesignTokens.Text.MUTED(), false);
            }
        }
    }

    private void renderButtons(GuiGraphics graphics, int mouseX, int mouseY) {
        int buttonY = panelY + PANEL_HEIGHT - 35;

        if (closeButton != null) {
            closeButton.render(graphics, panelX + PANEL_WIDTH / 2 - 35, buttonY, 70, 22, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);

        // Article list click
        if (hoveredArticleIndex >= 0) {
            List<NewsArticleData> articles = getFilteredArticles();
            if (hoveredArticleIndex < articles.size()) {
                NewsArticleData clicked = articles.get(hoveredArticleIndex);
                selectedArticleId = clicked.id();
                detailScrollOffset = 0;

                // Mark as read
                if (!clicked.isRead()) {
                    PacketDistributor.sendToServer(new NewsReadPayload(clicked.id()));
                    ClientNewsCache.markAsRead(clicked.id());
                }

                UiSounds.click();
                return true;
            }
        }

        // Button clicks
        if (closeButton != null && closeButton.mouseClicked(mouseX, mouseY, button)) return true;
        if (allButton != null && allButton.mouseClicked(mouseX, mouseY, button)) return true;
        if (patchButton != null && patchButton.mouseClicked(mouseX, mouseY, button)) return true;
        if (eventButton != null && eventButton.mouseClicked(mouseX, mouseY, button)) return true;
        if (announceButton != null && announceButton.mouseClicked(mouseX, mouseY, button)) return true;

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (closeButton != null) closeButton.mouseReleased(mouseX, mouseY, button);
        if (allButton != null) allButton.mouseReleased(mouseX, mouseY, button);
        if (patchButton != null) patchButton.mouseReleased(mouseX, mouseY, button);
        if (eventButton != null) eventButton.mouseReleased(mouseX, mouseY, button);
        if (announceButton != null) announceButton.mouseReleased(mouseX, mouseY, button);
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int listX = panelX + 10;
        int listY = panelY + 65;
        int detailX = panelX + LIST_WIDTH + 20;

        // List scroll
        if (mouseX >= listX && mouseX < listX + LIST_WIDTH &&
            mouseY >= listY && mouseY < panelY + PANEL_HEIGHT - 45) {
            List<NewsArticleData> articles = getFilteredArticles();
            int maxScroll = Math.max(0, articles.size() - VISIBLE_ARTICLES);
            scrollOffset = Mth.clamp(scrollOffset - (int) verticalAmount, 0, maxScroll);
            return true;
        }

        // Detail scroll
        if (mouseX >= detailX && mouseX < detailX + DETAIL_WIDTH &&
            mouseY >= listY && mouseY < panelY + PANEL_HEIGHT - 45) {
            detailScrollOffset = Math.max(0, detailScrollOffset - (int) verticalAmount);
            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256 /* ESCAPE */) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ========== UTILITIES ==========

    private int getCategoryColor(int categoryOrdinal) {
        int[] categoryColors = MailboxUiTheme.News.categoryColors();
        if (categoryOrdinal >= 0 && categoryOrdinal < categoryColors.length) {
            return categoryColors[categoryOrdinal];
        }
        return DesignTokens.Text.SECONDARY();
    }

    private String truncate(String text, int maxLength) {
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength - 3) + "...";
    }

    private List<String> wrapText(String text, int maxWidth) {
        List<String> lines = new java.util.ArrayList<>();
        List<String> words = new java.util.ArrayList<>();
        int start = 0;
        for (int i = 0; i <= text.length(); i++) {
            if (i == text.length() || text.charAt(i) == ' ') {
                if (i > start) {
                    words.add(text.substring(start, i));
                }
                start = i + 1;
            }
        }
        StringBuilder currentLine = new StringBuilder();

        for (String word : words) {
            if (currentLine.isEmpty()) {
                currentLine.append(word);
            } else if (getFont().width(currentLine + " " + word) <= maxWidth) {
                currentLine.append(" ").append(word);
            } else {
                lines.add(currentLine.toString());
                currentLine = new StringBuilder(word);
            }
        }

        if (!currentLine.isEmpty()) {
            lines.add(currentLine.toString());
        }

        return lines;
    }

    /**
     * Open the news screen.
     */
    public static void open() {
        Minecraft mc = Minecraft.getInstance();
        mc.setScreen(new NewsScreen());
    }
}

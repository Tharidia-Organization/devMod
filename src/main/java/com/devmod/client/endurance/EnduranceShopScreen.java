package com.devmod.client.endurance;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.google.common.base.Splitter;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

import com.devmod.actions.ActionIds;
import com.devmod.actions.ActionOrigin;
import com.devmod.actions.ActionRegistry;
import com.devmod.actions.client.ClientActionContexts;
import com.devmod.client.ui.AxiomRenderer;
import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.endurance.ClientShopCache;
import com.devmod.endurance.RequestShopSyncPayload;
import com.devmod.endurance.RewardSystem;
import com.devmod.endurance.ShopPurchasePayload;
import com.devmod.util.I18n;

@OnlyIn(Dist.CLIENT)
public class EnduranceShopScreen extends Screen {
    private static final Splitter SPACE_SPLITTER = Splitter.on(' ');

    // Layout constants - using DesignTokens for consistency
    private static final int CATEGORY_WIDTH = 140;  // Category sidebar width
    private static final int ITEM_HEIGHT = 70;
    private static final int ITEM_MARGIN = 5;  // Small gap between items

    // Colors - Standardized to DesignTokens
    private static final int COLOR_BG = DesignTokens.Bg.LEVEL_0;
    private static final int COLOR_CATEGORY_BG = DesignTokens.Bg.LEVEL_1;
    private static final int COLOR_ITEM_BG = DesignTokens.Surface.LEVEL_0;
    private static final int COLOR_ITEM_HOVER = DesignTokens.Surface.LEVEL_1;
    private static final int COLOR_ITEM_DISABLED = DesignTokens.Text.MUTED;
    private static final int COLOR_ACCENT = DesignTokens.Semantic.WARNING;  // Gold for tokens
    private static final int COLOR_TEXT = DesignTokens.Text.PRIMARY;
    private static final int COLOR_TEXT_DIM = DesignTokens.Text.SECONDARY;
    private static final int COLOR_SUCCESS = DesignTokens.Semantic.SUCCESS;
    private static final int COLOR_ERROR = DesignTokens.Semantic.ERROR;

    // Shop-specific colors (no direct tokens)
    private static final int COLOR_BLUE = DesignTokens.Semantic.INFO;
    private static final int COLOR_PURPLE = 0xFFA371F7;
    private static final int COLOR_YELLOW = 0xFFFFD700;
    private static final int COLOR_CYAN = DesignTokens.Accent.PRIMARY;

    // Category colors matching ShopCategory
    private static final Map<RewardSystem.ShopCategory, Integer> CATEGORY_COLORS = Map.of(
        RewardSystem.ShopCategory.STATS, COLOR_SUCCESS,
        RewardSystem.ShopCategory.PERKS, COLOR_BLUE,
        RewardSystem.ShopCategory.UTILITY, COLOR_ACCENT,
        RewardSystem.ShopCategory.COSMETICS, COLOR_PURPLE
    );

    // Currency colors
    private static final Map<RewardSystem.Currency, Integer> CURRENCY_COLORS = Map.of(
        RewardSystem.Currency.TOKENS, COLOR_ACCENT,
        RewardSystem.Currency.COINS, COLOR_YELLOW,
        RewardSystem.Currency.PRESTIGE, COLOR_PURPLE,
        RewardSystem.Currency.GEMS, COLOR_CYAN,
        RewardSystem.Currency.BLOOD_GEMS, COLOR_ERROR
    );

    private enum ViewMode {
        SHOP,
        CHALLENGES
    }

    // State
    private ViewMode viewMode = ViewMode.SHOP;
    private RewardSystem.ShopCategory selectedCategory = RewardSystem.ShopCategory.STATS;
    private List<RewardSystem.ShopItem> categoryItems = new ArrayList<>();
    private int scrollOffset = 0;
    private int maxScroll = 0;
    @Nullable
    private RewardSystem.ShopItem selectedItem = null;

    // Player data (cached from client cache or loaded via packet)
    private int playerTokens = 0;
    private int playerCoins = 0;
    private int playerPrestige = 0;
    private int playerGems = 0;
    private int playerBloodGems = 0;
    private Map<String, Integer> playerPurchases = new HashMap<>();

    public EnduranceShopScreen() {
        super(I18n.endurance("shop_title"));
    }

    @Override
    protected void init() {
        super.init();

        // Load player currency (would need client-side sync)
        loadPlayerData();

        // Load items for selected category
        updateCategoryItems();

        // All buttons are now rendered custom - no vanilla widgets needed
    }

    private List<ClientChallengeCache.ChallengeDisplayData> getChallengeSnapshot() {
        if (ClientChallengeCache.INSTANCE.hasCachedData()) {
            return ClientChallengeCache.INSTANCE.getChallenges();
        }
        return List.of();
    }

    private void loadPlayerData() {
        // Load from client-side cache (synced from server)
        playerTokens = ClientShopCache.getTokens();
        playerCoins = ClientShopCache.getCoins();
        playerPrestige = ClientShopCache.getPrestige();
        playerGems = ClientShopCache.getGems();
        playerBloodGems = ClientShopCache.getBloodGems();
        playerPurchases = ClientShopCache.getPurchases();

        // Request fresh data from server if cache is stale
        if (!ClientShopCache.hasRecentData()) {
            // Request sync from server - will update cache when response arrives
            PacketDistributor.sendToServer(new RequestShopSyncPayload());
        }
    }

    private void updateCategoryItems() {
        categoryItems = RewardSystem.INSTANCE.getShopItemsByCategory(selectedCategory);
        scrollOffset = 0;
        calculateMaxScroll();
    }

    private void calculateMaxScroll() {
        int contentHeight = categoryItems.size() * (ITEM_HEIGHT + ITEM_MARGIN);
        int viewportHeight = height - 100;
        maxScroll = Math.max(0, contentHeight - viewportHeight);
    }

    @Override
    public void tick() {
        super.tick();
        // Refresh currency display from cache (in case server sent update)
        playerTokens = ClientShopCache.getTokens();
        playerCoins = ClientShopCache.getCoins();
        playerPrestige = ClientShopCache.getPrestige();
        playerGems = ClientShopCache.getGems();
        playerBloodGems = ClientShopCache.getBloodGems();
        playerPurchases = ClientShopCache.getPurchases();
    }

    @Override
    public void render(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Background
        graphics.fill(0, 0, width, height, COLOR_BG);

        // Header
        renderHeader(graphics);

        // Category sidebar with custom buttons
        renderCategorySidebar(graphics, mouseX, mouseY);

        if (viewMode == ViewMode.CHALLENGES) {
            // Render challenges view
            renderChallengeList(graphics, mouseX, mouseY);
        } else {
            // Item list
            renderItemList(graphics, mouseX, mouseY);

            // Selected item details
            RewardSystem.ShopItem item = selectedItem;
            if (item != null) {
                renderItemDetails(graphics, item);
            }
        }

        // Custom action buttons (Back, Purchase)
        renderActionButtons(graphics, mouseX, mouseY);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderHeader(GuiGraphics graphics) {
        // Title
        graphics.drawCenteredString(Objects.requireNonNull(font), Objects.requireNonNull(I18n.translate("devmod.endurance.shop_title").getString()), width / 2, 10, COLOR_ACCENT);

        // Currency display
        int currencyY = 10;
        int currencyX = width - 200;

        // Tokens
        graphics.drawString(Objects.requireNonNull(font), Objects.requireNonNull(I18n.translate("devmod.reward.tokens").getString()) + ": " + playerTokens,
            currencyX, currencyY, getCurrencyColor(RewardSystem.Currency.TOKENS));
        currencyY += 12;

        // Coins
        graphics.drawString(Objects.requireNonNull(font), Objects.requireNonNull(I18n.translate("devmod.reward.coins").getString()) + ": " + playerCoins,
            currencyX, currencyY, getCurrencyColor(RewardSystem.Currency.COINS));
        currencyY += 12;

        // Prestige
        graphics.drawString(Objects.requireNonNull(font), Objects.requireNonNull(I18n.translate("devmod.reward.prestige").getString()) + ": " + playerPrestige,
            currencyX, currencyY, getCurrencyColor(RewardSystem.Currency.PRESTIGE));
        currencyY += 12;

        // Gems
        graphics.drawString(Objects.requireNonNull(font), Objects.requireNonNull(I18n.translate("devmod.reward.gems").getString()) + ": " + playerGems,
            currencyX, currencyY, getCurrencyColor(RewardSystem.Currency.GEMS));
        currencyY += 12;

        // Blood Gems
        graphics.drawString(Objects.requireNonNull(font), Objects.requireNonNull(I18n.translate("devmod.reward.blood_gems").getString()) + ": " + playerBloodGems,
            currencyX, currencyY, getCurrencyColor(RewardSystem.Currency.BLOOD_GEMS));
    }

    private void renderCategorySidebar(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.fill(0, 40, CATEGORY_WIDTH, height - 50, COLOR_CATEGORY_BG);

        // Category header
        graphics.drawCenteredString(Objects.requireNonNull(font), Objects.requireNonNull(I18n.translate("devmod.ui.categories").getString()), CATEGORY_WIDTH / 2, 45, COLOR_TEXT_DIM);

        // Category buttons (custom rendered)
        int catY = 60;
        int catBtnW = CATEGORY_WIDTH - DesignTokens.Space.PANEL_PADDING * 2;
        int catBtnH = DesignTokens.Component.BUTTON_HEIGHT_LG;

        for (RewardSystem.ShopCategory category : RewardSystem.ShopCategory.values()) {
            int catBtnX = DesignTokens.Space.PANEL_PADDING;
            boolean isSelected = viewMode == ViewMode.SHOP && category == selectedCategory;
            boolean isHovered = AxiomRenderer.isMouseOver(mouseX, mouseY, catBtnX, catY, catBtnW, catBtnH);
            int catColor = getCategoryColor(category);

            String catName = getCategoryLabel(category);
            renderButton(graphics, catBtnX, catY, catBtnW, catBtnH, catName, isHovered, isSelected ? catColor : DesignTokens.Accent.PRIMARY);

            catY += catBtnH + 4;
        }

        // Separator
        catY += 10;
        graphics.fill(DesignTokens.Space.PANEL_PADDING, catY, CATEGORY_WIDTH - DesignTokens.Space.PANEL_PADDING, catY + 1, DesignTokens.Stroke.MUTED);
        catY += 15;

        // Challenges button (special category)
        int catBtnX = DesignTokens.Space.PANEL_PADDING;
        boolean challengesSelected = viewMode == ViewMode.CHALLENGES;
        boolean challengesHovered = AxiomRenderer.isMouseOver(mouseX, mouseY, catBtnX, catY, catBtnW, catBtnH);
        String challengesLabel = Objects.requireNonNull(I18n.translate("devmod.endurance.challenges").getString());

        // Show challenge count badge
        List<ClientChallengeCache.ChallengeDisplayData> challenges = getChallengeSnapshot();
        int challengeCount = challenges.size();
        int completedCount = (int) challenges.stream().filter(ClientChallengeCache.ChallengeDisplayData::completed).count();
        String badge = completedCount + "/" + challengeCount;

        renderButton(graphics, catBtnX, catY, catBtnW, catBtnH, challengesLabel, challengesHovered, challengesSelected ? COLOR_ACCENT : DesignTokens.Accent.PRIMARY);

        // Badge showing completion
        if (challengeCount > 0) {
            int badgeColor = completedCount == challengeCount ? COLOR_SUCCESS : COLOR_ACCENT;
            int badgeX = catBtnX + catBtnW - Objects.requireNonNull(font).width(badge) - 5;
            graphics.drawString(Objects.requireNonNull(font), badge, badgeX, catY + (catBtnH - 8) / 2, badgeColor);
        }
    }

    private void renderItemList(GuiGraphics graphics, int mouseX, int mouseY) {
        int listX = CATEGORY_WIDTH + 10;
        int listY = 50;
        int listWidth = width - CATEGORY_WIDTH - 230;
        int listHeight = height - 100;

        // Clip area
        graphics.enableScissor(listX, listY, listX + listWidth, listY + listHeight);

        int y = listY - scrollOffset;
        for (RewardSystem.ShopItem item : categoryItems) {
            if (y + ITEM_HEIGHT > listY && y < listY + listHeight) {
                renderShopItem(graphics, item, listX, y, listWidth, mouseX, mouseY);
            }
            y += ITEM_HEIGHT + ITEM_MARGIN;
        }

        graphics.disableScissor();

        // Scrollbar
        if (maxScroll > 0) {
            int scrollbarHeight = (int) ((float) listHeight / (listHeight + maxScroll) * listHeight);
            int scrollbarY = listY + (int) ((float) scrollOffset / maxScroll * (listHeight - scrollbarHeight));
            graphics.fill(listX + listWidth - 5, listY, listX + listWidth, listY + listHeight, DesignTokens.Stroke.MUTED);
            graphics.fill(listX + listWidth - 5, scrollbarY, listX + listWidth, scrollbarY + scrollbarHeight, COLOR_ACCENT);
        }
    }

    /**
     * Renders the daily challenges list view.
     * Uses all ChallengeDisplayData methods for complete integration.
     */
    private void renderChallengeList(GuiGraphics graphics, int mouseX, int mouseY) {
        int listX = CATEGORY_WIDTH + 10;
        int listY = 50;
        int listWidth = width - CATEGORY_WIDTH - 20;
        int listHeight = height - 100;
        int challengeItemHeight = ITEM_HEIGHT + 10;
        List<ClientChallengeCache.ChallengeDisplayData> challenges = getChallengeSnapshot();

        // Title
        String title = Objects.requireNonNull(I18n.translate("devmod.endurance.daily_challenges").getString());
        graphics.drawCenteredString(Objects.requireNonNull(font), title, listX + listWidth / 2, listY, COLOR_ACCENT);
        listY += 20;
        listHeight -= 20;

        if (challenges.isEmpty()) {
            // No challenges available
            String noData = Objects.requireNonNull(I18n.translate("devmod.endurance.no_challenges").getString());
            graphics.drawCenteredString(Objects.requireNonNull(font), noData, listX + listWidth / 2, listY + 50, COLOR_TEXT_DIM);
            return;
        }

        // Clip area for scrolling
        graphics.enableScissor(listX, listY, listX + listWidth, listY + listHeight);

        int y = listY - scrollOffset;
        for (ClientChallengeCache.ChallengeDisplayData challenge : challenges) {
            if (y + challengeItemHeight > listY && y < listY + listHeight) {
                renderChallengeItem(graphics, challenge, listX, y, listWidth, challengeItemHeight, mouseX, mouseY);
            }
            y += challengeItemHeight + ITEM_MARGIN;
        }

        graphics.disableScissor();

        // Calculate scroll for challenges
        int contentHeight = challenges.size() * (challengeItemHeight + ITEM_MARGIN);
        int challengeMaxScroll = Math.max(0, contentHeight - listHeight);
        if (challengeMaxScroll > 0) {
            int scrollbarHeight = (int) ((float) listHeight / (listHeight + challengeMaxScroll) * listHeight);
            int scrollbarY = listY + (int) ((float) scrollOffset / challengeMaxScroll * (listHeight - scrollbarHeight));
            graphics.fill(listX + listWidth - 5, listY, listX + listWidth, listY + listHeight, DesignTokens.Stroke.MUTED);
            graphics.fill(listX + listWidth - 5, scrollbarY, listX + listWidth, scrollbarY + scrollbarHeight, COLOR_ACCENT);
        }
    }

    /**
     * Renders a single challenge item using all ChallengeDisplayData record methods.
     */
    private void renderChallengeItem(GuiGraphics graphics, ClientChallengeCache.ChallengeDisplayData challenge,
                                      int x, int y, int itemWidth, int itemHeight, int mouseX, int mouseY) {
        boolean isHovered = mouseX >= x && mouseX <= x + itemWidth && mouseY >= y && mouseY < y + itemHeight;
        boolean isCompleted = challenge.completed();

        // Background
        int bgColor = isCompleted ? DesignTokens.Surface.LEVEL_2 : (isHovered ? COLOR_ITEM_HOVER : COLOR_ITEM_BG);
        graphics.fill(x, y, x + itemWidth, y + itemHeight, bgColor);

        // Difficulty color indicator (using difficultyColor from record)
        graphics.fill(x, y, x + 4, y + itemHeight, challenge.difficultyColor());

        // Challenge name (Component from record)
        int nameColor = isCompleted ? COLOR_SUCCESS : COLOR_TEXT;
        graphics.drawString(Objects.requireNonNull(font), Objects.requireNonNull(challenge.name()), x + 10, y + 5, nameColor);

        // Description (Component from record)
        graphics.drawString(Objects.requireNonNull(font), Objects.requireNonNull(challenge.description()), x + 10, y + 18, COLOR_TEXT_DIM);

        // Difficulty badge (using difficultyName from record)
        String diffBadge = "[" + challenge.difficultyName() + "]";
        int diffBadgeX = x + itemWidth - Objects.requireNonNull(font).width(diffBadge) - 10;
        graphics.drawString(Objects.requireNonNull(font), diffBadge, diffBadgeX, y + 5, challenge.difficultyColor());

        // Progress bar using getProgress() method
        int barX = x + 10;
        int barY = y + 35;
        int barWidth = itemWidth - 120;
        int barHeight = 8;

        // Background bar
        graphics.fill(barX, barY, barX + barWidth, barY + barHeight, DesignTokens.Stroke.MUTED);

        // Filled portion using getProgress() (0.0-1.0)
        float progress = challenge.getProgress();
        int filledWidth = (int) (barWidth * progress);
        int progressColor = isCompleted ? COLOR_SUCCESS : COLOR_ACCENT;
        graphics.fill(barX, barY, barX + filledWidth, barY + barHeight, progressColor);

        // Progress text using getProgressText() method
        String progressText = challenge.getProgressText();
        graphics.drawString(Objects.requireNonNull(font), progressText, barX + barWidth + 5, barY, COLOR_TEXT);

        // Reward text using getRewardText() method
        String rewardText = challenge.getRewardText();
        int rewardColor = isCompleted ? COLOR_TEXT_DIM : COLOR_ACCENT;
        graphics.drawString(Objects.requireNonNull(font), rewardText, x + 10, y + itemHeight - 15, rewardColor);

        // Completed/Rewarded status
        if (challenge.rewarded()) {
            String claimed = Objects.requireNonNull(I18n.translate("devmod.endurance.reward_claimed").getString());
            graphics.drawString(Objects.requireNonNull(font), claimed, x + itemWidth - font.width(claimed) - 10, y + itemHeight - 15, COLOR_SUCCESS);
        } else if (isCompleted) {
            String claimable = Objects.requireNonNull(I18n.translate("devmod.endurance.claim_reward").getString());
            graphics.drawString(Objects.requireNonNull(font), claimable, x + itemWidth - font.width(claimable) - 10, y + itemHeight - 15, COLOR_YELLOW);
        }
    }

    private void renderShopItem(GuiGraphics graphics, RewardSystem.ShopItem item,
                                 int x, int y, int width, int mouseX, int mouseY) {
        boolean isHovered = mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY < y + ITEM_HEIGHT;
        boolean isSelected = item == selectedItem;
        boolean canAfford = canAfford(item);
        int owned = playerPurchases.getOrDefault(item.id, 0);
        boolean maxedOut = owned >= item.maxPurchases;

        int bgColor;
        if (maxedOut) {
            bgColor = COLOR_ITEM_DISABLED;
        } else if (isSelected) {
            bgColor = DesignTokens.Surface.LEVEL_2;
        } else if (isHovered) {
            bgColor = COLOR_ITEM_HOVER;
        } else {
            bgColor = COLOR_ITEM_BG;
        }

        graphics.fill(x, y, x + width, y + ITEM_HEIGHT, bgColor);

        // Category color indicator
        int catColor = getCategoryColor(item.category);
        graphics.fill(x, y, x + 4, y + ITEM_HEIGHT, catColor);

        // Item name (truncated to prevent overflow)
        int nameColor = maxedOut ? COLOR_TEXT_DIM : COLOR_TEXT;
        String displayName = truncateText(getItemName(item), width - 100); // Leave room for owned count
        graphics.drawString(Objects.requireNonNull(font), displayName, x + 10, y + 5, nameColor);

        // Description (truncated to prevent overflow)
        String description = truncateText(getItemDescription(item), width - 20);
        graphics.drawString(Objects.requireNonNull(font), description, x + 10, y + 18, COLOR_TEXT_DIM);

        // Price
        int currencyColor = getCurrencyColor(item.currency);
        String priceText = item.price + " " + getCurrencyLabel(item.currency);
        int priceColor = canAfford ? currencyColor : COLOR_ERROR;
        graphics.drawString(Objects.requireNonNull(font), priceText, x + 10, y + 35, priceColor);

        // Owned count
        String ownedText = Objects.requireNonNull(I18n.translate("devmod.ui.owned").getString()) + ": " + owned + "/" + item.maxPurchases;
        int ownedColor = maxedOut ? COLOR_SUCCESS : COLOR_TEXT_DIM;
        graphics.drawString(Objects.requireNonNull(font), ownedText, x + width - font.width(ownedText) - 10, y + 5, ownedColor);

        // Status indicator
        if (maxedOut) {
            graphics.drawString(Objects.requireNonNull(font), Objects.requireNonNull(I18n.translate("devmod.ui.max").getString()), x + width - 30, y + ITEM_HEIGHT - 15, COLOR_SUCCESS);
        } else if (!canAfford) {
            String cantAfford = Objects.requireNonNull(I18n.translate("devmod.reward.cannot_afford").getString());
            graphics.drawString(Objects.requireNonNull(font), cantAfford, x + width - font.width(cantAfford) - 10, y + ITEM_HEIGHT - 15, COLOR_ERROR);
        }
    }

    private void renderItemDetails(GuiGraphics graphics, RewardSystem.ShopItem item) {
        int panelX = width - 210;
        int panelY = 50;
        int panelWidth = 200;
        int panelHeight = height - 150;

        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, COLOR_CATEGORY_BG);

        int y = panelY + 10;

        // Item name
        graphics.drawCenteredString(Objects.requireNonNull(font), getItemName(item), panelX + panelWidth / 2, y, COLOR_TEXT);
        y += 20;

        // Category
        int catColor = getCategoryColor(item.category);
        graphics.drawCenteredString(Objects.requireNonNull(font), getCategoryLabel(item.category), panelX + panelWidth / 2, y, catColor);
        y += 25;

        // Divider
        graphics.fill(panelX + 10, y, panelX + panelWidth - 10, y + 1, DesignTokens.Stroke.MUTED);
        y += 10;

        // Description (word wrap)
        String desc = getItemDescription(item);
        int maxWidth = panelWidth - 20;
        List<String> lines = wrapText(desc, maxWidth);
        for (String line : lines) {
            graphics.drawString(Objects.requireNonNull(font), line, panelX + 10, y, COLOR_TEXT_DIM);
            y += 11;
        }
        y += 10;

        // Price details
        graphics.drawString(Objects.requireNonNull(font), Objects.requireNonNull(I18n.translate("devmod.ui.price").getString()) + ":", panelX + 10, y, COLOR_ACCENT);
        y += 12;
        int currencyColor = getCurrencyColor(item.currency);
        graphics.drawString(Objects.requireNonNull(font), "  " + item.price + " " + getCurrencyLabel(item.currency),
            panelX + 10, y, currencyColor);
        y += 20;

        // Purchase info
        int owned = playerPurchases.getOrDefault(item.id, 0);
        graphics.drawString(Objects.requireNonNull(font), Objects.requireNonNull(I18n.translate("devmod.shop.purchases").getString()) + ":", panelX + 10, y, COLOR_ACCENT);
        y += 12;
        graphics.drawString(Objects.requireNonNull(font), "  " + owned + " / " + item.maxPurchases,
            panelX + 10, y, owned >= item.maxPurchases ? COLOR_SUCCESS : COLOR_TEXT_DIM);
    }

    /**
     * Render custom action buttons (Back, Purchase).
     */
    private void renderActionButtons(GuiGraphics graphics, int mouseX, int mouseY) {
        int buttonY = height - 40;

        // Back button (secondary - left side)
        int backW = 80 - 20;
        int backH = DesignTokens.Component.BUTTON_HEIGHT_LG;
        int backX = DesignTokens.Space.PANEL_PADDING;
        boolean backHovered = AxiomRenderer.isMouseOver(mouseX, mouseY, backX, buttonY, backW, backH);
        renderButton(graphics, backX, buttonY, backW, backH,
            I18n.translate("devmod.ui.back").getString(), backHovered, DesignTokens.Accent.PRIMARY);

        // Purchase button (primary CTA - green, right side)
        int purchaseW = 80;
        int purchaseH = DesignTokens.Component.BUTTON_HEIGHT_LG;
        int purchaseX = width - purchaseW - 10;
        boolean canPurchase = selectedItem != null && canAfford(selectedItem)
            && playerPurchases.getOrDefault(selectedItem.id, 0) < selectedItem.maxPurchases;
        boolean purchaseHovered = AxiomRenderer.isMouseOver(mouseX, mouseY, purchaseX, buttonY, purchaseW, purchaseH);
        int purchaseColor = canPurchase ? COLOR_SUCCESS : DesignTokens.Text.MUTED;
        renderButton(graphics, purchaseX, buttonY, purchaseW, purchaseH,
            I18n.translate("devmod.ui.purchase").getString(), purchaseHovered && canPurchase, purchaseColor);
    }

    /**
     * Render a custom styled button.
     */
    private void renderButton(GuiGraphics graphics, int x, int y, int w, int h, String text, boolean hovered, int color) {
        int bgColor = hovered ? color : DesignTokens.Surface.LEVEL_0;
        graphics.fill(x, y, x + w, y + h, bgColor);
        AxiomRenderer.drawBorder(graphics, x, y, w, h, color);
        int textX = x + (w - Objects.requireNonNull(font).width(Objects.requireNonNull(text))) / 2;
        int textY = y + (h - 8) / 2;
        graphics.drawString(Objects.requireNonNull(font), Objects.requireNonNull(text), textX, textY, hovered ? 0xFFFFFFFF : color, false);
    }

    private String getCategoryLabel(RewardSystem.ShopCategory category) {
        return Objects.requireNonNull(I18n.translate("devmod.shop.category." + category.name().toLowerCase(Locale.ROOT)).getString());
    }

    private String getCurrencyLabel(RewardSystem.Currency currency) {
        return Objects.requireNonNull(I18n.translate("devmod.currency." + currency.key).getString());
    }

    private String getItemName(RewardSystem.ShopItem item) {
        return Objects.requireNonNull(I18n.translate("devmod.shop." + item.id).getString());
    }

    private String getItemDescription(RewardSystem.ShopItem item) {
        return Objects.requireNonNull(I18n.translate("devmod.shop." + item.id + ".desc").getString());
    }

    private List<String> wrapText(String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        StringBuilder currentLine = new StringBuilder();

        for (String word : SPACE_SPLITTER.split(text)) {
            String testLine = currentLine.isEmpty() ? word : currentLine + " " + word;
            if (Objects.requireNonNull(font).width(Objects.requireNonNull(testLine)) > maxWidth) {
                if (!currentLine.isEmpty()) {
                    lines.add(currentLine.toString());
                    currentLine = new StringBuilder(word);
                } else {
                    lines.add(word);
                }
            } else {
                currentLine = new StringBuilder(testLine);
            }
        }
        if (!currentLine.isEmpty()) {
            lines.add(currentLine.toString());
        }

        return lines;
    }

    /**
     * Truncate text to fit within maxWidth pixels, adding ellipsis if needed.
     */
    private String truncateText(String text, int maxWidth) {
        if (Objects.requireNonNull(font).width(Objects.requireNonNull(text)) <= maxWidth) return text;
        String ellipsis = "...";
        int minChars = Math.min(6, text.length());
        String truncated = text;
        while (Objects.requireNonNull(font).width(truncated + ellipsis) > maxWidth && truncated.length() > minChars) {
            truncated = truncated.substring(0, truncated.length() - 1);
        }
        return truncated + ellipsis;
    }

    private boolean canAfford(RewardSystem.ShopItem item) {
        return switch (item.currency) {
            case TOKENS -> playerTokens >= item.price;
            case COINS -> playerCoins >= item.price;
            case PRESTIGE -> playerPrestige >= item.price;
            case GEMS -> playerGems >= item.price;
            case BLOOD_GEMS -> playerBloodGems >= item.price;
            default -> false;
        };
    }

    private static int getCategoryColor(RewardSystem.ShopCategory category) {
        return Objects.requireNonNull(CATEGORY_COLORS.get(category));
    }

    private static int getCurrencyColor(RewardSystem.Currency currency) {
        return Objects.requireNonNull(CURRENCY_COLORS.get(currency));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int mx = (int) mouseX;
        int my = (int) mouseY;

        // Check category button clicks
        int catY = 60;
        int catBtnW = CATEGORY_WIDTH - DesignTokens.Space.PANEL_PADDING * 2;
        int catBtnH = DesignTokens.Component.BUTTON_HEIGHT_LG;
        int catBtnX = DesignTokens.Space.PANEL_PADDING;

        for (RewardSystem.ShopCategory category : RewardSystem.ShopCategory.values()) {
            if (AxiomRenderer.isMouseOver(mx, my, catBtnX, catY, catBtnW, catBtnH)) {
                selectCategory(category);
                return true;
            }
            catY += catBtnH + 4;
        }

        // Check Challenges button click (after separator)
        catY += 10 + 1 + 15; // separator spacing
        if (AxiomRenderer.isMouseOver(mx, my, catBtnX, catY, catBtnW, catBtnH)) {
            viewMode = ViewMode.CHALLENGES;
            selectedItem = null;
            scrollOffset = 0;
            return true;
        }

        // Check action button clicks
        int buttonY = height - 40;

        // Back button
        int backW = 80 - 20;
        int backH = DesignTokens.Component.BUTTON_HEIGHT_LG;
        int backX = DesignTokens.Space.PANEL_PADDING;
        if (AxiomRenderer.isMouseOver(mx, my, backX, buttonY, backW, backH)) {
            goBack();
            return true;
        }

        // Purchase button
        int purchaseW = 80;
        int purchaseH = DesignTokens.Component.BUTTON_HEIGHT_LG;
        int purchaseX = width - purchaseW - 10;
        if (AxiomRenderer.isMouseOver(mx, my, purchaseX, buttonY, purchaseW, purchaseH)) {
            purchaseSelected();
            return true;
        }

        // Check item list clicks
        int listX = CATEGORY_WIDTH + 10;
        int listY = 50;
        int listWidth = width - CATEGORY_WIDTH - 230;
        int listHeight = height - 100;

        if (mouseX >= listX && mouseX <= listX + listWidth && mouseY >= listY && mouseY <= listY + listHeight) {
            int relativeY = (int) mouseY - listY + scrollOffset;
            int index = relativeY / (ITEM_HEIGHT + ITEM_MARGIN);
            if (index >= 0 && index < categoryItems.size()) {
                selectedItem = categoryItems.get(index);
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) (scrollY * 20)));
        return true;
    }

    private void selectCategory(RewardSystem.ShopCategory category) {
        viewMode = ViewMode.SHOP;
        selectedCategory = category;
        selectedItem = null;
        scrollOffset = 0;
        updateCategoryItems();
    }

    private void purchaseSelected() {
        if (selectedItem == null) return;

        int owned = playerPurchases.getOrDefault(selectedItem.id, 0);
        if (owned >= selectedItem.maxPurchases) return;
        if (!canAfford(selectedItem)) return;

        // Send purchase request to server
        PacketDistributor.sendToServer(new ShopPurchasePayload(selectedItem.id));

        // Optimistically update local state and cache
        ClientShopCache.optimisticPurchase(selectedItem.id, selectedItem.currency, selectedItem.price);

        // Update local display values from cache
        playerTokens = ClientShopCache.getTokens();
        playerCoins = ClientShopCache.getCoins();
        playerPrestige = ClientShopCache.getPrestige();
        playerGems = ClientShopCache.getGems();
        playerBloodGems = ClientShopCache.getBloodGems();
        playerPurchases = ClientShopCache.getPurchases();
    }

    private void goBack() {
        ActionRegistry.invoke(ActionIds.UI_ENDURANCE_SCREEN_OPEN,
            ClientActionContexts.forClient(ActionOrigin.UI));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

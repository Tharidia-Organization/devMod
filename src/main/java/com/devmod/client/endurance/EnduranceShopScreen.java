package com.devmod.client.endurance;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

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
import com.devmod.client.ui.editor.core.UIConstants;
import com.devmod.endurance.ClientShopCache;
import com.devmod.endurance.RequestShopSyncPayload;
import com.devmod.endurance.RewardSystem;
import com.devmod.endurance.ShopPurchasePayload;
import com.devmod.util.I18n;

@OnlyIn(Dist.CLIENT)
public class EnduranceShopScreen extends Screen {

    // Layout constants - using UIConstants for consistency
    private static final int CATEGORY_WIDTH = UIConstants.Size.CATEGORY_WIDTH;
    private static final int ITEM_HEIGHT = 70;
    private static final int ITEM_MARGIN = UIConstants.Spacing.GAP_SMALL + 1;

    // Colors - Standardized to UIConstants
    private static final int COLOR_BG = UIConstants.Background.PANEL();
    private static final int COLOR_CATEGORY_BG = UIConstants.Background.HEADER();
    private static final int COLOR_ITEM_BG = UIConstants.Background.INPUT();
    private static final int COLOR_ITEM_HOVER = UIConstants.Background.HOVER();
    private static final int COLOR_ITEM_DISABLED = UIConstants.Text.DISABLED();
    private static final int COLOR_ACCENT = UIConstants.Accent.GOLD();  // Gold for tokens
    private static final int COLOR_TEXT = UIConstants.Text.PRIMARY();
    private static final int COLOR_TEXT_DIM = UIConstants.Text.SECONDARY();
    private static final int COLOR_SUCCESS = UIConstants.Accent.GREEN();
    private static final int COLOR_ERROR = UIConstants.Accent.RED();

    // Category colors matching ShopCategory
    private static final Map<RewardSystem.ShopCategory, Integer> CATEGORY_COLORS = Map.of(
        RewardSystem.ShopCategory.STATS, UIConstants.Accent.GREEN(),
        RewardSystem.ShopCategory.PERKS, UIConstants.Accent.BLUE(),
        RewardSystem.ShopCategory.UTILITY, UIConstants.Accent.GOLD(),
        RewardSystem.ShopCategory.COSMETICS, UIConstants.Accent.PURPLE()
    );

    // Currency colors
    private static final Map<RewardSystem.Currency, Integer> CURRENCY_COLORS = Map.of(
        RewardSystem.Currency.TOKENS, UIConstants.Accent.GOLD(),
        RewardSystem.Currency.PRESTIGE, UIConstants.Accent.PURPLE(),
        RewardSystem.Currency.BLOOD_GEMS, UIConstants.Accent.RED()
    );

    // State
    private RewardSystem.ShopCategory selectedCategory = RewardSystem.ShopCategory.STATS;
    private List<RewardSystem.ShopItem> categoryItems = new ArrayList<>();
    private int scrollOffset = 0;
    private int maxScroll = 0;
    @Nullable
    private RewardSystem.ShopItem selectedItem = null;

    // Player data (cached from client cache or loaded via packet)
    private int playerTokens = 0;
    private int playerPrestige = 0;
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

    private void loadPlayerData() {
        // Load from client-side cache (synced from server)
        playerTokens = ClientShopCache.getTokens();
        playerPrestige = ClientShopCache.getPrestige();
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
        playerPrestige = ClientShopCache.getPrestige();
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

        // Item list
        renderItemList(graphics, mouseX, mouseY);

        // Selected item details
        RewardSystem.ShopItem item = selectedItem;
        if (item != null) {
            renderItemDetails(graphics, item);
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

        // Prestige
        graphics.drawString(Objects.requireNonNull(font), Objects.requireNonNull(I18n.translate("devmod.reward.prestige").getString()) + ": " + playerPrestige,
            currencyX, currencyY, getCurrencyColor(RewardSystem.Currency.PRESTIGE));
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
        int catBtnW = CATEGORY_WIDTH - UIConstants.Spacing.PANEL_MARGIN * 2;
        int catBtnH = UIConstants.Size.BUTTON_HEIGHT_PROMINENT;

        for (RewardSystem.ShopCategory category : RewardSystem.ShopCategory.values()) {
            int catBtnX = UIConstants.Spacing.PANEL_MARGIN;
            boolean isSelected = category == selectedCategory;
            boolean isHovered = AxiomRenderer.isMouseOver(mouseX, mouseY, catBtnX, catY, catBtnW, catBtnH);
            int catColor = getCategoryColor(category);

            String catName = getCategoryLabel(category);
            renderButton(graphics, catBtnX, catY, catBtnW, catBtnH, catName, isHovered, isSelected ? catColor : UIConstants.Border.DEFAULT());

            catY += catBtnH + UIConstants.Spacing.GAP_SMALL;
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
            graphics.fill(listX + listWidth - 5, listY, listX + listWidth, listY + listHeight, UIConstants.Border.SEPARATOR());
            graphics.fill(listX + listWidth - 5, scrollbarY, listX + listWidth, scrollbarY + scrollbarHeight, COLOR_ACCENT);
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
            bgColor = UIConstants.Background.ACTIVE();
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
        graphics.fill(panelX + 10, y, panelX + panelWidth - 10, y + 1, UIConstants.Border.SEPARATOR());
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
        int backW = UIConstants.Size.BUTTON_WIDTH_SMALL - 20;
        int backH = UIConstants.Size.BUTTON_HEIGHT_PROMINENT;
        int backX = UIConstants.Spacing.PANEL_MARGIN;
        boolean backHovered = AxiomRenderer.isMouseOver(mouseX, mouseY, backX, buttonY, backW, backH);
        renderButton(graphics, backX, buttonY, backW, backH,
            I18n.translate("devmod.ui.back").getString(), backHovered, UIConstants.Border.DEFAULT());

        // Purchase button (primary CTA - green, right side)
        int purchaseW = UIConstants.Size.BUTTON_WIDTH_SMALL;
        int purchaseH = UIConstants.Size.BUTTON_HEIGHT_PROMINENT;
        int purchaseX = width - purchaseW - 10;
        boolean canPurchase = selectedItem != null && canAfford(selectedItem)
            && playerPurchases.getOrDefault(selectedItem.id, 0) < selectedItem.maxPurchases;
        boolean purchaseHovered = AxiomRenderer.isMouseOver(mouseX, mouseY, purchaseX, buttonY, purchaseW, purchaseH);
        int purchaseColor = canPurchase ? UIConstants.Accent.GREEN() : UIConstants.Text.DISABLED();
        renderButton(graphics, purchaseX, buttonY, purchaseW, purchaseH,
            I18n.translate("devmod.ui.purchase").getString(), purchaseHovered && canPurchase, purchaseColor);
    }

    /**
     * Render a custom styled button.
     */
    private void renderButton(GuiGraphics graphics, int x, int y, int w, int h, String text, boolean hovered, int color) {
        int bgColor = hovered ? color : UIConstants.Background.INPUT();
        graphics.fill(x, y, x + w, y + h, bgColor);
        AxiomRenderer.drawBorder(graphics, x, y, w, h, color);
        int textX = x + (w - Objects.requireNonNull(font).width(Objects.requireNonNull(text))) / 2;
        int textY = y + (h - 8) / 2;
        graphics.drawString(Objects.requireNonNull(font), Objects.requireNonNull(text), textX, textY, hovered ? UIConstants.Text.WHITE() : color, false);
    }

    private String getCategoryLabel(RewardSystem.ShopCategory category) {
        return Objects.requireNonNull(I18n.translate("devmod.shop.category." + category.name().toLowerCase()).getString());
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
        String[] words = text.split(" ");
        StringBuilder currentLine = new StringBuilder();

        for (String word : words) {
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
            case PRESTIGE -> playerPrestige >= item.price;
            case BLOOD_GEMS -> playerBloodGems >= item.price;
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
        int catBtnW = CATEGORY_WIDTH - UIConstants.Spacing.PANEL_MARGIN * 2;
        int catBtnH = UIConstants.Size.BUTTON_HEIGHT_PROMINENT;
        int catBtnX = UIConstants.Spacing.PANEL_MARGIN;

        for (RewardSystem.ShopCategory category : RewardSystem.ShopCategory.values()) {
            if (AxiomRenderer.isMouseOver(mx, my, catBtnX, catY, catBtnW, catBtnH)) {
                selectCategory(category);
                return true;
            }
            catY += catBtnH + UIConstants.Spacing.GAP_SMALL;
        }

        // Check action button clicks
        int buttonY = height - 40;

        // Back button
        int backW = UIConstants.Size.BUTTON_WIDTH_SMALL - 20;
        int backH = UIConstants.Size.BUTTON_HEIGHT_PROMINENT;
        int backX = UIConstants.Spacing.PANEL_MARGIN;
        if (AxiomRenderer.isMouseOver(mx, my, backX, buttonY, backW, backH)) {
            goBack();
            return true;
        }

        // Purchase button
        int purchaseW = UIConstants.Size.BUTTON_WIDTH_SMALL;
        int purchaseH = UIConstants.Size.BUTTON_HEIGHT_PROMINENT;
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
        selectedCategory = category;
        selectedItem = null;
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
        playerPrestige = ClientShopCache.getPrestige();
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

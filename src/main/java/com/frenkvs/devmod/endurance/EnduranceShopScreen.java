package com.frenkvs.devmod.endurance;

import com.frenkvs.devmod.util.I18n;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.*;

/**
 * Shop screen for purchasing permanent upgrades with Endurance Tokens.
 */
public class EnduranceShopScreen extends Screen {

    // Layout constants
    private static final int CATEGORY_WIDTH = 150;
    private static final int ITEM_HEIGHT = 70;
    private static final int ITEM_MARGIN = 5;

    // Colors
    private static final int COLOR_BG = 0xDD1a1a2e;
    private static final int COLOR_CATEGORY_BG = 0xDD16213e;
    private static final int COLOR_ITEM_BG = 0xDD0f3460;
    private static final int COLOR_ITEM_HOVER = 0xDD1a5490;
    private static final int COLOR_ITEM_DISABLED = 0xDD333333;
    private static final int COLOR_ACCENT = 0xFFFFD700;  // Gold for tokens
    private static final int COLOR_TEXT = 0xFFFFFFFF;
    private static final int COLOR_TEXT_DIM = 0xFFAAAAAA;
    private static final int COLOR_SUCCESS = 0xFF4ade80;
    private static final int COLOR_ERROR = 0xFFef4444;

    // Category colors matching ShopCategory
    private static final Map<RewardSystem.ShopCategory, Integer> CATEGORY_COLORS = Map.of(
        RewardSystem.ShopCategory.STATS, 0xFF55FF55,
        RewardSystem.ShopCategory.PERKS, 0xFF5555FF,
        RewardSystem.ShopCategory.UTILITY, 0xFFFFFF55,
        RewardSystem.ShopCategory.COSMETICS, 0xFFFF55FF
    );

    // Currency colors
    private static final Map<RewardSystem.Currency, Integer> CURRENCY_COLORS = Map.of(
        RewardSystem.Currency.TOKENS, 0xFFFFD700,
        RewardSystem.Currency.PRESTIGE, 0xFFFF00FF,
        RewardSystem.Currency.BLOOD_GEMS, 0xFFFF3333
    );

    // State
    private RewardSystem.ShopCategory selectedCategory = RewardSystem.ShopCategory.STATS;
    private List<RewardSystem.ShopItem> categoryItems = new ArrayList<>();
    private int scrollOffset = 0;
    private int maxScroll = 0;
    private RewardSystem.ShopItem selectedItem = null;

    // Player data (cached from client cache or loaded via packet)
    private int playerTokens = 0;
    private int playerPrestige = 0;
    private int playerBloodGems = 0;
    private Map<String, Integer> playerPurchases = new HashMap<>();

    public EnduranceShopScreen() {
        super(I18n.ui("endurance.shop_title"));
    }

    @Override
    protected void init() {
        super.init();

        // Load player currency (would need client-side sync)
        loadPlayerData();

        // Load items for selected category
        updateCategoryItems();

        // Category buttons
        int catY = 50;
        for (RewardSystem.ShopCategory category : RewardSystem.ShopCategory.values()) {
            final RewardSystem.ShopCategory cat = category;
            addRenderableWidget(Button.builder(I18n.ui("shop.category." + category.name().toLowerCase()), btn -> selectCategory(cat))
                .bounds(10, catY, CATEGORY_WIDTH - 20, 25)
                .build());
            catY += 30;
        }

        // Back button
        addRenderableWidget(Button.builder(I18n.ui("back"), btn -> goBack())
            .bounds(10, height - 40, 80, 25)
            .build());

        // Purchase button
        addRenderableWidget(Button.builder(I18n.ui("purchase"), btn -> purchaseSelected())
            .bounds(width - 110, height - 40, 100, 25)
            .build());
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
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Background
        graphics.fill(0, 0, width, height, COLOR_BG);

        // Header
        renderHeader(graphics);

        // Category sidebar
        renderCategorySidebar(graphics, mouseX, mouseY);

        // Item list
        renderItemList(graphics, mouseX, mouseY);

        // Selected item details
        if (selectedItem != null) {
            renderItemDetails(graphics);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderHeader(GuiGraphics graphics) {
        // Title
        graphics.drawCenteredString(font, I18n.translate("devmod.endurance.shop_title").getString(), width / 2, 10, COLOR_ACCENT);

        // Currency display
        int currencyY = 10;
        int currencyX = width - 200;

        // Tokens
        graphics.drawString(font, I18n.translate("devmod.reward.tokens").getString() + ": " + playerTokens,
            currencyX, currencyY, CURRENCY_COLORS.get(RewardSystem.Currency.TOKENS));
        currencyY += 12;

        // Prestige
        graphics.drawString(font, I18n.translate("devmod.reward.prestige").getString() + ": " + playerPrestige,
            currencyX, currencyY, CURRENCY_COLORS.get(RewardSystem.Currency.PRESTIGE));
        currencyY += 12;

        // Blood Gems
        graphics.drawString(font, I18n.translate("devmod.reward.blood_gems").getString() + ": " + playerBloodGems,
            currencyX, currencyY, CURRENCY_COLORS.get(RewardSystem.Currency.BLOOD_GEMS));
    }

    private void renderCategorySidebar(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.fill(0, 40, CATEGORY_WIDTH, height - 50, COLOR_CATEGORY_BG);

        // Category header
        graphics.drawCenteredString(font, I18n.translate("devmod.ui.categories").getString(), CATEGORY_WIDTH / 2, 45, COLOR_TEXT_DIM);
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
            graphics.fill(listX + listWidth - 5, listY, listX + listWidth, listY + listHeight, 0x44FFFFFF);
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
            bgColor = 0xDD2a74b0;
        } else if (isHovered) {
            bgColor = COLOR_ITEM_HOVER;
        } else {
            bgColor = COLOR_ITEM_BG;
        }

        graphics.fill(x, y, x + width, y + ITEM_HEIGHT, bgColor);

        // Category color indicator
        int catColor = CATEGORY_COLORS.get(item.category);
        graphics.fill(x, y, x + 4, y + ITEM_HEIGHT, catColor);

        // Item name
        int nameColor = maxedOut ? COLOR_TEXT_DIM : COLOR_TEXT;
        graphics.drawString(font, item.displayName, x + 10, y + 5, nameColor);

        // Description
        graphics.drawString(font, item.description, x + 10, y + 18, COLOR_TEXT_DIM);

        // Price
        int currencyColor = CURRENCY_COLORS.get(item.currency);
        String priceText = item.price + " " + item.currency.displayName;
        int priceColor = canAfford ? currencyColor : COLOR_ERROR;
        graphics.drawString(font, priceText, x + 10, y + 35, priceColor);

        // Owned count
        String ownedText = I18n.translate("devmod.ui.owned").getString() + ": " + owned + "/" + item.maxPurchases;
        int ownedColor = maxedOut ? COLOR_SUCCESS : COLOR_TEXT_DIM;
        graphics.drawString(font, ownedText, x + width - font.width(ownedText) - 10, y + 5, ownedColor);

        // Status indicator
        if (maxedOut) {
            graphics.drawString(font, I18n.translate("devmod.ui.max").getString(), x + width - 30, y + ITEM_HEIGHT - 15, COLOR_SUCCESS);
        } else if (!canAfford) {
            String cantAfford = I18n.translate("devmod.reward.cannot_afford").getString();
            graphics.drawString(font, cantAfford, x + width - font.width(cantAfford) - 10, y + ITEM_HEIGHT - 15, COLOR_ERROR);
        }
    }

    private void renderItemDetails(GuiGraphics graphics) {
        int panelX = width - 210;
        int panelY = 50;
        int panelWidth = 200;
        int panelHeight = height - 150;

        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, COLOR_CATEGORY_BG);

        int y = panelY + 10;

        // Item name
        graphics.drawCenteredString(font, selectedItem.displayName, panelX + panelWidth / 2, y, COLOR_TEXT);
        y += 20;

        // Category
        int catColor = CATEGORY_COLORS.get(selectedItem.category);
        graphics.drawCenteredString(font, selectedItem.category.displayName, panelX + panelWidth / 2, y, catColor);
        y += 25;

        // Divider
        graphics.fill(panelX + 10, y, panelX + panelWidth - 10, y + 1, 0x44FFFFFF);
        y += 10;

        // Description (word wrap)
        String desc = selectedItem.description;
        int maxWidth = panelWidth - 20;
        List<String> lines = wrapText(desc, maxWidth);
        for (String line : lines) {
            graphics.drawString(font, line, panelX + 10, y, COLOR_TEXT_DIM);
            y += 11;
        }
        y += 10;

        // Price details
        graphics.drawString(font, I18n.translate("devmod.ui.price").getString() + ":", panelX + 10, y, COLOR_ACCENT);
        y += 12;
        int currencyColor = CURRENCY_COLORS.get(selectedItem.currency);
        graphics.drawString(font, "  " + selectedItem.price + " " + selectedItem.currency.displayName,
            panelX + 10, y, currencyColor);
        y += 20;

        // Purchase info
        int owned = playerPurchases.getOrDefault(selectedItem.id, 0);
        graphics.drawString(font, I18n.translate("devmod.shop.purchases").getString() + ":", panelX + 10, y, COLOR_ACCENT);
        y += 12;
        graphics.drawString(font, "  " + owned + " / " + selectedItem.maxPurchases,
            panelX + 10, y, owned >= selectedItem.maxPurchases ? COLOR_SUCCESS : COLOR_TEXT_DIM);
    }

    private List<String> wrapText(String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        String[] words = text.split(" ");
        StringBuilder currentLine = new StringBuilder();

        for (String word : words) {
            String testLine = currentLine.isEmpty() ? word : currentLine + " " + word;
            if (font.width(testLine) > maxWidth) {
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

    private boolean canAfford(RewardSystem.ShopItem item) {
        return switch (item.currency) {
            case TOKENS -> playerTokens >= item.price;
            case PRESTIGE -> playerPrestige >= item.price;
            case BLOOD_GEMS -> playerBloodGems >= item.price;
        };
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
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
        Minecraft.getInstance().setScreen(new EnduranceQuestScreen());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

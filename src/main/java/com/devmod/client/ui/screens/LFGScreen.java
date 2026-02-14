package com.devmod.client.ui.screens;

import java.util.List;
import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

import com.devmod.client.party.ClientLFGCache;
import com.devmod.client.ui.core.UIScaleManager;
import com.devmod.client.ui.editor.components.EditorButton;
import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.party.lfg.LFGActivity;
import com.devmod.party.lfg.LFGActionPayload;
import com.devmod.party.lfg.LFGSyncPayload;
import com.devmod.util.I18n;

/**
 * LFG (Looking-For-Group) screen.
 * Two tabs: "Browse Listings" and "Create Listing".
 */
@OnlyIn(Dist.CLIENT)
public class LFGScreen extends Screen {

    // Panel dimensions (base values, scaled via UIScaleManager)
    private static final int BASE_PANEL_WIDTH = 400;
    private static final int BASE_PANEL_HEIGHT = 320;
    private static final int BASE_HEADER_HEIGHT = 32;
    private static final int BASE_TAB_HEIGHT = 24;
    private static final int BASE_ROW_HEIGHT = 36;
    private static final int BASE_PADDING = 8;
    private static final int MAX_VISIBLE_ROWS = 6;

    // Scaled dimensions (computed in init)
    private int panelW, panelH, headerH, tabH, rowH, pad;

    // Colors
    private static final int BG_COLOR = DesignTokens.Bg.LEVEL_1;
    private static final int HEADER_BG = DesignTokens.Surface.LEVEL_1;
    private static final int ROW_BG = DesignTokens.Surface.LEVEL_0;
    private static final int ROW_BG_ALT = DesignTokens.Bg.LEVEL_2;
    private static final int ROW_HOVER = DesignTokens.Surface.LEVEL_2;
    private static final int TEXT_PRIMARY = DesignTokens.Text.PRIMARY;
    private static final int TEXT_SECONDARY = DesignTokens.Text.SECONDARY;
    private static final int TEXT_MUTED = DesignTokens.Text.MUTED;
    private static final int ACCENT = DesignTokens.Accent.PRIMARY;
    private static final int BORDER = DesignTokens.Stroke.DEFAULT;
    private static final int TAB_ACTIVE = DesignTokens.Accent.PRIMARY;
    private static final int TAB_INACTIVE = DesignTokens.Surface.LEVEL_1;
    private static final int SUCCESS = DesignTokens.Semantic.SUCCESS;
    private static final int WARNING = DesignTokens.Semantic.WARNING;

    // State
    private Tab activeTab = Tab.BROWSE;
    private int scrollOffset = 0;
    private int hoveredRow = -1;

    // Create form state
    private int selectedActivityIndex = 0;
    private EditBox descriptionBox;
    private int createMinPlayers = 2;
    private int createMaxPlayers = 4;
    private boolean createAutoAccept = false;

    // Buttons (EditorButton wrapped as vanilla widgets)
    private EditorButton refreshBtn;
    private EditorButton createBtn;
    private EditorButton removeBtn;
    private Button refreshButton;
    private Button createButton;
    private Button removeButton;

    @Nullable
    private final Screen parent;

    public LFGScreen(@Nullable Screen parent) {
        super(I18n.screenTitle("lfg"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        UIScaleManager.update();

        panelW = UIScaleManager.scale(BASE_PANEL_WIDTH);
        panelH = UIScaleManager.scale(BASE_PANEL_HEIGHT);
        headerH = UIScaleManager.scale(BASE_HEADER_HEIGHT);
        tabH = UIScaleManager.scale(BASE_TAB_HEIGHT);
        rowH = UIScaleManager.scale(BASE_ROW_HEIGHT);
        pad = UIScaleManager.scaleMin(BASE_PADDING, UIScaleManager.MIN_PADDING);

        int panelX = (this.width - panelW) / 2;
        int panelY = (this.height - panelH) / 2;
        int contentY = panelY + headerH + tabH;

        // Description input for create tab
        int descLabelOffset = UIScaleManager.scale(100);
        descriptionBox = new EditBox(
            this.font, panelX + pad + descLabelOffset, contentY + UIScaleManager.scale(40),
            panelW - pad * 2 - descLabelOffset - pad, UIScaleManager.scale(18),
            Component.literal("Description")
        );
        descriptionBox.setMaxLength(120);
        descriptionBox.setHint(Component.literal("What are you looking for?"));
        descriptionBox.setVisible(activeTab == Tab.CREATE);
        addWidget(descriptionBox);

        // EditorButton instances with styled rendering
        int btnW = UIScaleManager.scale(60);
        int btnH = UIScaleManager.scale(18);
        int wideBtnW = UIScaleManager.scale(120);
        int wideBtnH = UIScaleManager.scale(20);

        refreshBtn = new EditorButton("lfg_refresh", I18n.translate("devmod.lfg.button.refresh").getString())
            .style(EditorButton.Style.NORMAL).onClick(this::requestRefresh);
        refreshButton = refreshBtn.asVanilla(panelX + panelW - btnW - pad, panelY + headerH + 2, btnW, btnH);
        addRenderableWidget(refreshButton);

        createBtn = new EditorButton("lfg_create", I18n.translate("devmod.lfg.button.create").getString())
            .style(EditorButton.Style.SUCCESS).onClick(this::submitCreateListing);
        createButton = createBtn.asVanilla(panelX + panelW / 2 - wideBtnW / 2, contentY + UIScaleManager.scale(160), wideBtnW, wideBtnH);
        addRenderableWidget(createButton);

        removeBtn = new EditorButton("lfg_remove", I18n.translate("devmod.lfg.button.remove").getString())
            .style(EditorButton.Style.DANGER).onClick(this::submitRemoveListing);
        removeButton = removeBtn.asVanilla(panelX + panelW / 2 - wideBtnW / 2, contentY + UIScaleManager.scale(185), wideBtnW, wideBtnH);
        addRenderableWidget(removeButton);

        updateButtonVisibility();

        // Request initial data
        requestRefresh();
    }

    private void updateButtonVisibility() {
        boolean isBrowse = activeTab == Tab.BROWSE;
        refreshButton.visible = isBrowse;
        createButton.visible = !isBrowse;
        removeButton.visible = !isBrowse;
        descriptionBox.setVisible(!isBrowse);
    }

    @Override
    public void render(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        UIScaleManager.update();
        renderBackground(graphics, mouseX, mouseY, partialTick);

        int panelX = (this.width - panelW) / 2;
        int panelY = (this.height - panelH) / 2;

        // Panel background
        graphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, BG_COLOR);
        graphics.renderOutline(panelX, panelY, panelW, panelH, BORDER);

        // Header
        graphics.fill(panelX, panelY, panelX + panelW, panelY + headerH, HEADER_BG);
        graphics.drawCenteredString(this.font, I18n.translate("devmod.lfg.title").getString(), panelX + panelW / 2, panelY + UIScaleManager.scale(10), TEXT_PRIMARY);

        // Tabs
        int tabY = panelY + headerH;
        int tabWidth = panelW / 2;

        // Browse tab
        int browseColor = activeTab == Tab.BROWSE ? TAB_ACTIVE : TAB_INACTIVE;
        graphics.fill(panelX, tabY, panelX + tabWidth, tabY + tabH, browseColor);
        graphics.drawCenteredString(this.font, I18n.translate("devmod.lfg.tab.browse").getString(), panelX + tabWidth / 2, tabY + UIScaleManager.scale(7), TEXT_PRIMARY);

        // Create tab
        int createColor = activeTab == Tab.CREATE ? TAB_ACTIVE : TAB_INACTIVE;
        graphics.fill(panelX + tabWidth, tabY, panelX + panelW, tabY + tabH, createColor);
        graphics.drawCenteredString(this.font, I18n.translate("devmod.lfg.tab.create").getString(), panelX + tabWidth + tabWidth / 2, tabY + UIScaleManager.scale(7), TEXT_PRIMARY);

        // Separator line under tabs
        graphics.fill(panelX, tabY + tabH - 1, panelX + panelW, tabY + tabH, BORDER);

        // Content area
        int contentY = tabY + tabH;

        if (activeTab == Tab.BROWSE) {
            renderBrowseTab(graphics, panelX, contentY, mouseX, mouseY);
        } else {
            renderCreateTab(graphics, panelX, contentY, mouseX, mouseY);
        }

        // Render widgets (buttons, editbox)
        super.render(graphics, mouseX, mouseY, partialTick);

        // Render description box on top
        if (activeTab == Tab.CREATE) {
            descriptionBox.render(graphics, mouseX, mouseY, partialTick);
        }
    }

    private void renderBrowseTab(@Nonnull GuiGraphics graphics, int panelX, int contentY, int mouseX, int mouseY) {
        List<LFGSyncPayload.LFGListingEntry> allListings = ClientLFGCache.getListings();

        if (allListings.isEmpty()) {
            graphics.drawCenteredString(this.font, I18n.translate("devmod.lfg.browse.empty").getString(),
                panelX + panelW / 2, contentY + UIScaleManager.scale(40), TEXT_MUTED);
            graphics.drawCenteredString(this.font, I18n.translate("devmod.lfg.browse.empty_hint").getString(),
                panelX + panelW / 2, contentY + UIScaleManager.scale(55), TEXT_MUTED);
            return;
        }

        int maxScroll = Math.max(0, allListings.size() - MAX_VISIBLE_ROWS);
        scrollOffset = Math.min(scrollOffset, maxScroll);

        hoveredRow = -1;
        int y = contentY + pad;

        for (int i = 0; i < MAX_VISIBLE_ROWS && (i + scrollOffset) < allListings.size(); i++) {
            int index = i + scrollOffset;
            LFGSyncPayload.LFGListingEntry listing = allListings.get(index);
            int rowY = y + i * rowH;

            boolean isHovered = mouseX >= panelX + pad && mouseX < panelX + panelW - pad
                && mouseY >= rowY && mouseY < rowY + rowH - 2;
            if (isHovered) {
                hoveredRow = index;
            }

            int rowBg = isHovered ? ROW_HOVER : (i % 2 == 0 ? ROW_BG : ROW_BG_ALT);
            graphics.fill(panelX + pad, rowY, panelX + panelW - pad, rowY + rowH - 2, rowBg);

            // Activity color indicator
            LFGActivity activity = listing.getActivity();
            graphics.fill(panelX + pad, rowY, panelX + pad + 4, rowY + rowH - 2, activity.getColor());

            // Leader name + activity
            String headerText = listing.leaderName() + " - " + activity.getDisplayName();
            graphics.drawString(this.font, headerText, panelX + pad + 10, rowY + 3, TEXT_PRIMARY);

            // Player count
            String playerCount = listing.currentPlayers() + "/" + listing.maxPlayers();
            int countColor = listing.currentPlayers() >= listing.maxPlayers() ? WARNING : SUCCESS;
            int countX = panelX + panelW - pad - this.font.width(playerCount) - UIScaleManager.scale(68);
            graphics.drawString(this.font, playerCount, countX, rowY + 3, countColor);

            // Description
            String desc = listing.description();
            if (desc != null && !desc.isEmpty()) {
                int maxDescW = panelW - pad * 2 - 20;
                if (this.font.width(desc) > maxDescW) {
                    desc = this.font.plainSubstrByWidth(desc, maxDescW - 10) + "..";
                }
                graphics.drawString(this.font, desc, panelX + pad + 10, rowY + UIScaleManager.scale(15), TEXT_SECONDARY);
            }

            // Apply button area
            if (isHovered) {
                int applyBtnW = UIScaleManager.scale(50);
                int applyBtnH = UIScaleManager.scale(14);
                int btnX = panelX + panelW - pad - applyBtnW - pad;
                int btnY = rowY + 2;
                graphics.fill(btnX, btnY, btnX + applyBtnW, btnY + applyBtnH, ACCENT);
                graphics.drawCenteredString(this.font, I18n.translate("devmod.lfg.button.apply").getString(), btnX + applyBtnW / 2, btnY + 3, DesignTokens.Text.PRIMARY);
            }
        }

        // Scroll indicators
        if (scrollOffset > 0) {
            graphics.drawCenteredString(this.font, "^ Scroll Up ^", panelX + panelW / 2, contentY + 2, TEXT_MUTED);
        }
        if (scrollOffset < maxScroll) {
            int bottomY = contentY + pad + MAX_VISIBLE_ROWS * rowH;
            graphics.drawCenteredString(this.font, "v Scroll Down v", panelX + panelW / 2, bottomY, TEXT_MUTED);
        }

        // Count
        String countText = allListings.size() + " listing" + (allListings.size() != 1 ? "s" : "");
        graphics.drawString(this.font, countText, panelX + pad, contentY + panelH - headerH - tabH - UIScaleManager.scale(18), TEXT_MUTED);
    }

    private void renderCreateTab(@Nonnull GuiGraphics graphics, int panelX, int contentY, int mouseX, int mouseY) {
        int x = panelX + pad;
        int y = contentY + pad;
        Font font = this.font;

        // Activity selector
        graphics.drawString(font, I18n.translate("devmod.lfg.create.activity").getString(), x, y + 5, TEXT_PRIMARY);
        LFGActivity activity = LFGActivity.values()[selectedActivityIndex];
        String activityLabel = "< " + activity.getDisplayName() + " >";
        int actLabelX = x + 100;
        graphics.drawString(font, activityLabel, actLabelX, y + 5, activity.getColor());

        // Description label
        y += 28;
        graphics.drawString(font, I18n.translate("devmod.lfg.create.description").getString(), x, y + 5, TEXT_PRIMARY);
        // EditBox rendered separately

        // Min players
        y += 30;
        graphics.drawString(font, I18n.translate("devmod.lfg.create.min_players").getString(), x, y + 5, TEXT_PRIMARY);
        String minLabel = "< " + createMinPlayers + " >";
        graphics.drawString(font, minLabel, x + 100, y + 5, TEXT_PRIMARY);

        // Max players
        y += 22;
        graphics.drawString(font, I18n.translate("devmod.lfg.create.max_players").getString(), x, y + 5, TEXT_PRIMARY);
        String maxLabel = "< " + createMaxPlayers + " >";
        graphics.drawString(font, maxLabel, x + 100, y + 5, TEXT_PRIMARY);

        // Auto-accept
        y += 22;
        graphics.drawString(font, I18n.translate("devmod.lfg.create.auto_accept").getString(), x, y + 5, TEXT_PRIMARY);
        String autoLabel = createAutoAccept ? "[ON]" : "[OFF]";
        int autoColor = createAutoAccept ? SUCCESS : TEXT_MUTED;
        graphics.drawString(font, autoLabel, x + 100, y + 5, autoColor);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int panelX = (this.width - panelW) / 2;
        int panelY = (this.height - panelH) / 2;
        int tabY = panelY + headerH;
        int tabWidth = panelW / 2;
        int contentY = tabY + tabH;

        // Tab clicks
        if (mouseY >= tabY && mouseY < tabY + tabH) {
            if (mouseX >= panelX && mouseX < panelX + tabWidth) {
                activeTab = Tab.BROWSE;
                updateButtonVisibility();
                return true;
            } else if (mouseX >= panelX + tabWidth && mouseX < panelX + panelW) {
                activeTab = Tab.CREATE;
                updateButtonVisibility();
                return true;
            }
        }

        // Browse tab - apply button clicks
        if (activeTab == Tab.BROWSE && hoveredRow >= 0) {
            List<LFGSyncPayload.LFGListingEntry> allListings = ClientLFGCache.getListings();
            if (hoveredRow < allListings.size()) {
                int applyBtnW = UIScaleManager.scale(50);
                int btnX = panelX + panelW - pad - applyBtnW - pad;
                int rowY = contentY + pad + (hoveredRow - scrollOffset) * rowH + 2;
                int applyBtnH = UIScaleManager.scale(14);
                if (mouseX >= btnX && mouseX < btnX + applyBtnW && mouseY >= rowY && mouseY < rowY + applyBtnH) {
                    applyToListing(allListings.get(hoveredRow).listingId());
                    return true;
                }
            }
        }

        // Create tab - field clicks
        if (activeTab == Tab.CREATE) {
            int x = panelX + pad;
            int y = contentY + pad;

            // Activity selector click
            if (mouseY >= y && mouseY < y + 20 && mouseX >= x + 80 && mouseX < x + 250) {
                if (mouseX < x + 165) {
                    selectedActivityIndex = (selectedActivityIndex - 1 + LFGActivity.values().length) % LFGActivity.values().length;
                } else {
                    selectedActivityIndex = (selectedActivityIndex + 1) % LFGActivity.values().length;
                }
                return true;
            }

            // Min players click
            y += 58;
            if (mouseY >= y && mouseY < y + 20 && mouseX >= x + 80 && mouseX < x + 200) {
                if (mouseX < x + 140) {
                    createMinPlayers = Math.max(1, createMinPlayers - 1);
                } else {
                    createMinPlayers = Math.min(8, createMinPlayers + 1);
                }
                if (createMaxPlayers < createMinPlayers) {
                    createMaxPlayers = createMinPlayers;
                }
                return true;
            }

            // Max players click
            y += 22;
            if (mouseY >= y && mouseY < y + 20 && mouseX >= x + 80 && mouseX < x + 200) {
                if (mouseX < x + 140) {
                    createMaxPlayers = Math.max(createMinPlayers, createMaxPlayers - 1);
                } else {
                    createMaxPlayers = Math.min(8, createMaxPlayers + 1);
                }
                return true;
            }

            // Auto-accept click
            y += 22;
            if (mouseY >= y && mouseY < y + 20 && mouseX >= x + 80 && mouseX < x + 200) {
                createAutoAccept = !createAutoAccept;
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (activeTab == Tab.BROWSE) {
            List<LFGSyncPayload.LFGListingEntry> allListings = ClientLFGCache.getListings();
            int maxScroll = Math.max(0, allListings.size() - MAX_VISIBLE_ROWS);
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) scrollY));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (activeTab == Tab.CREATE && descriptionBox.isFocused()) {
            descriptionBox.keyPressed(keyCode, scanCode, modifiers);
            return true;
        }
        if (keyCode == 256) { // Escape
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        if (activeTab == Tab.CREATE && descriptionBox.isFocused()) {
            return descriptionBox.charTyped(c, modifiers);
        }
        return super.charTyped(c, modifiers);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // === Network Actions ===

    private void requestRefresh() {
        PacketDistributor.sendToServer(LFGActionPayload.refresh());
    }

    private void applyToListing(java.util.UUID listingId) {
        PacketDistributor.sendToServer(LFGActionPayload.apply(listingId));
    }

    private void submitCreateListing() {
        LFGActivity activity = LFGActivity.values()[selectedActivityIndex];
        String desc = descriptionBox.getValue();
        PacketDistributor.sendToServer(LFGActionPayload.create(
            activity, desc, createMinPlayers, createMaxPlayers, createAutoAccept
        ));
        // Switch to browse tab after creating
        activeTab = Tab.BROWSE;
        updateButtonVisibility();
        // Auto-refresh after a short delay
        requestRefresh();
    }

    private void submitRemoveListing() {
        PacketDistributor.sendToServer(LFGActionPayload.remove());
        requestRefresh();
    }

    // === Tab Enum ===

    private enum Tab {
        BROWSE, CREATE
    }
}

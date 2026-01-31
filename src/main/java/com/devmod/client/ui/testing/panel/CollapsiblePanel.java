package com.devmod.client.ui.testing.panel;

import java.util.Locale;
import java.util.Objects;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import com.devmod.client.ui.core.UIScaleManager;
import com.devmod.client.ui.editor.core.DesignTokens;

import static com.devmod.client.ui.testing.panel.PanelConstants.ANIMATION_SPEED;
import static com.devmod.client.ui.testing.panel.PanelConstants.COLOR_HEADER_ACCENT;
import static com.devmod.client.ui.testing.panel.PanelConstants.CONTENT_PADDING;
import static com.devmod.client.ui.testing.panel.PanelConstants.HEADER_HEIGHT;

public final class CollapsiblePanel implements UIPanel {

    private final String id;
    private final String title;
    private final UIPanel content;
    private final int headerColor;

    // Mutable state
    private boolean collapsed = false;
    private float animProgress = 1.0f;

    // Render state for click detection
    private int lastHeaderX, lastHeaderY, lastHeaderWidth;

    public CollapsiblePanel(String id, String title, UIPanel content, int headerColor) {
        this.id = id;
        this.title = title;
        this.content = content;
        this.headerColor = headerColor;
    }

    public CollapsiblePanel(String title, UIPanel content) {
        this(generateId(title), title, content, COLOR_HEADER_ACCENT);
    }

    public CollapsiblePanel(String id, String title, UIPanel content) {
        this(id, title, content, COLOR_HEADER_ACCENT);
    }

    private static String generateId(String title) {
        return "collapsible-" + title.toLowerCase(Locale.ROOT).replace(" ", "-");
    }

    // ═══════════════════════════════════════════════════════════════
    // FLUENT CONFIGURATION
    // ═══════════════════════════════════════════════════════════════

    public CollapsiblePanel collapsed(boolean collapsed) {
        this.collapsed = collapsed;
        this.animProgress = collapsed ? 0f : 1f;
        return this;
    }

    public CollapsiblePanel startCollapsed() {
        return collapsed(true);
    }

    // ═══════════════════════════════════════════════════════════════
    // STATE
    // ═══════════════════════════════════════════════════════════════

    public void toggle() {
        this.collapsed = !this.collapsed;
    }

    public boolean isCollapsed() {
        return collapsed;
    }

    public UIPanel getContent() {
        return content;
    }

    // ═══════════════════════════════════════════════════════════════
    // UIPanel IMPLEMENTATION
    // ═══════════════════════════════════════════════════════════════

    @Override
    public String id() { return id; }

    @Override
    public String title() { return title; }

    @Override
    public int getHeight(int availableWidth) {
        int contentHeight = content.getHeight(availableWidth);
        int visibleContentHeight = (int) (contentHeight * animProgress);
        return HEADER_HEIGHT + visibleContentHeight + (animProgress > 0 ? CONTENT_PADDING : 0);
    }

    @Override
    public void tick() {
        // Smooth animation
        float target = collapsed ? 0f : 1f;
        if (Math.abs(animProgress - target) > 0.01f) {
            animProgress += (target - animProgress) * ANIMATION_SPEED;
        } else {
            animProgress = target;
        }

        // Tick content if visible
        if (animProgress > 0) {
            content.tick();
        }
    }

    @Override
    public void init() {
        content.init();
    }

    @Override
    public void render(GuiGraphics graphics, int x, int y, int width, int mouseX, int mouseY) {
        var font = Objects.requireNonNull(Minecraft.getInstance().font, "font");

        // Store for click detection
        lastHeaderX = x;
        lastHeaderY = y;
        lastHeaderWidth = width;

        // Header background
        graphics.fill(x, y, x + width, y + HEADER_HEIGHT,
            DesignTokens.withAlpha(DesignTokens.Text.WHITE, DesignTokens.Alpha.A20));

        // Accent bar
        graphics.fill(x, y, x + 3, y + HEADER_HEIGHT, headerColor);

        // Collapse icon
        renderCollapseIcon(graphics, x + 8, y + (HEADER_HEIGHT - 12) / 2);

        // Title
        UIScaleManager.drawScaledString(graphics, font, title, x + 26, y + (HEADER_HEIGHT - 8) / 2,
            DesignTokens.Text.PRIMARY(), false);

        // Content (with animation)
        if (animProgress > 0.01f) {
            int contentY = y + HEADER_HEIGHT + CONTENT_PADDING;
            int contentHeight = content.getHeight(width);
            int visibleHeight = (int) (contentHeight * animProgress);

            graphics.enableScissor(x, contentY, x + width, contentY + visibleHeight);
            content.render(graphics, x, contentY, width, mouseX, mouseY);
            graphics.disableScissor();
        }
    }

    private void renderCollapseIcon(GuiGraphics graphics, int x, int y) {
        int iconColor = DesignTokens.Neutral.N500;
        if (collapsed) {
            // Right arrow (>)
            for (int i = 0; i < 6; i++) {
                graphics.fill(x + i, y + i, x + i + 1, y + 12 - i, iconColor);
            }
        } else {
            // Down arrow (v)
            for (int i = 0; i < 6; i++) {
                graphics.fill(x + i, y + i, x + 12 - i, y + i + 1, iconColor);
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Header click toggles collapse
        if (mouseX >= lastHeaderX && mouseX <= lastHeaderX + lastHeaderWidth &&
            mouseY >= lastHeaderY && mouseY <= lastHeaderY + HEADER_HEIGHT) {
            toggle();
            return true;
        }

        // Forward to content if expanded
        if (!collapsed && animProgress > 0.5f) {
            return content.mouseClicked(mouseX, mouseY, button);
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (!collapsed && animProgress > 0.5f) {
            return content.mouseReleased(mouseX, mouseY, button);
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (!collapsed && animProgress > 0.5f) {
            return content.mouseDragged(mouseX, mouseY, button, dx, dy);
        }
        return false;
    }
}
